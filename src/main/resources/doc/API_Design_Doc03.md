# 课堂学生行为分析系统 Java 后端接口设计文档

本文档用于规范 **前端 -> Java 后端** 以及 **Python 模型服务 -> Java 后端回调** 的接口对接标准。每个接口严格遵循 RESTful 风格，所有请求与响应体均使用 `application/json` 格式（除文件上传外）。统一接口前缀默认包含 `/api/v1`。

---

## 模块：课堂分析任务管理 (Analysis Task Module)

### 总体规范与职责边界

该模块由 **Java 后端** 负责实现，对前端统一暴露课堂分析能力，并负责：

1. 解析前端 JWT，校验教师身份与排课权限；
2. 创建 `analysis_task` 主表记录；
3. 处理文件上传并写入 `sys_file`；
4. 调用云端 Python 模型服务（FastAPI）发起识别；
5. 接收模型结果后写入 `analysis_detail`，并回填 `analysis_task.attendance_count` 与 `analysis_task.total_score`；
6. 对前端提供任务状态查询、实时流 WebSocket、报表查询等能力。

### 数据库映射约定

- `analysis_task.media_type`：`1-图片, 2-视频, 3-实时流`
- `analysis_task.status`：`0-排队中, 1-分析中, 2-成功, 3-失败`
- `analysis_detail.record_type`：`0-全量明细(文件流), 1-趋势聚合(实时流走势), 2-违规抓拍(实时流铁证)`
- `analysis_detail.bounding_boxes`：统一存储 **人框 xyxy 数组**，不直接存行为区域大框。

```typescript
// 创建分析任务响应 DTO (CreateAnalysisTaskResponseDTO)
export interface CreateAnalysisTaskResponseDTO {
  taskId: number;
  status: number;          // 0-排队中, 1-分析中
}

// 文件流任务状态响应 DTO (AnalysisTaskStatusDTO)
export interface AnalysisTaskStatusDTO {
  taskId: number;
  status: number;          // 0-排队中, 1-分析中, 2-成功, 3-失败
  mediaType: number;       // 1-图片, 2-视频, 3-实时流
  attendanceCount?: number;
  totalScore?: number;
  progress?: number;       // 0~100，可选
  errorMessage?: string;
}

// WebSocket 推送结果 DTO (RealtimeAnalysisResultDTO)
export interface RealtimeAnalysisResultDTO {
  type: string;            // 固定为 result
  taskId?: number;
  attendanceCount: number;
  totalScore: number;
  details: Array<{
    behaviorType: string;
    count: number;
    boundingBoxes: number[][];   // 每个元素为 [x1, y1, x2, y2]
  }>;
}
```

### 1. 启动 AI 识别 (创建文件流分析任务)

- **功能描述**：前端上传图片或视频文件并关联排课信息，由 Java 后端创建分析任务；Java 落库完成后，异步调用 Python 模型服务进行识别。

- **请求路径**：`/api/v1/analysis/task`

- **请求方法**：`POST`

- **请求格式**：`multipart/form-data`

- **接口参数** (FormData)：

  | 参数名 | 类型 | 必填 | 说明 |
  |---|---|---|---|
  | file | File | 是 | 用户上传的图片或视频文件内容（二进制流） |
  | fileName | string | 是 | 文件原始名称（含后缀，如 `lecture.mp4`） |
  | scheduleId | bigint | 是 | 关联的排课 ID |
  | streamType | int | 是 | 当前分析模式（1-实时流, 2-文件流）。文件上传场景固定传 2 |

- **Java 后端处理规则**：

  1. 根据 JWT 解析当前教师身份，禁止前端直接上传 `teacherId`；
  2. 校验 `scheduleId` 是否可被当前用户访问，且排课状态满足文件分析要求；
  3. 上传文件并写入 `sys_file`；
  4. 插入 `analysis_task`：`status=0`（排队中）；
  5. 将任务投递给异步执行器/消息队列，由后台线程调用 Python 模型服务；
  6. 后台线程真正开始调用模型时，应先将 `analysis_task.status` 更新为 `1`（分析中）。

- **响应格式** (application/json)：

  ```json
  // 成功响应 (HTTP 200)
  {
    "code": 200,
    "message": "分析任务创建成功，AI 正在快马加鞭识别中！",
    "data": {
      "taskId": 10001,
      "status": 0
    }
  }
  
  // 失败响应 Example (如缺少必要参数)
  {
    "code": 400,
    "message": "请选择要关联的排课信息！",
    "data": null
  }
  ```

### 2. 查询分析任务状态

- **功能描述**：文件流分析为异步任务，前端通过该接口轮询当前任务状态与结果摘要。

- **请求路径**：`/api/v1/analysis/task/{taskId}/status`

- **请求方法**：`GET`

- **请求格式**：`application/x-www-form-urlencoded`

- **接口参数**：

  - **Path 参数**：
    `taskId` (bigint) - REQUIRED. 分析任务主键 ID。

- **响应格式** (application/json)：

  ```json
  // 任务仍在处理中
  {
    "code": 200,
    "message": "success",
    "data": {
      "taskId": 10001,
      "status": 1,
      "mediaType": 2,
      "progress": 65
    }
  }
  
  // 任务成功完成
  {
    "code": 200,
    "message": "success",
    "data": {
      "taskId": 10001,
      "status": 2,
      "mediaType": 2,
      "attendanceCount": 45,
      "totalScore": 85.5,
      "progress": 100
    }
  }
  
  // 任务失败
  {
    "code": 200,
    "message": "success",
    "data": {
      "taskId": 10001,
      "status": 3,
      "mediaType": 2,
      "errorMessage": "模型服务调用超时"
    }
  }
  ```

### 3. 建立实时课堂分析流 (WebSocket)

- **功能描述**：前端与 Java 后端建立实时视频流分析通道；Java 后端负责鉴权、任务初始化、与 Python 模型服务同步交互，并将结果回推前端。

- **连接规范**：
  - **连接地址**：`ws://<domain>/ws/v1/analysis/stream/{scheduleId}?token=xxx`

- **连接建立后的 Java 后端处理规则**：

  1. 根据 `token` 解析教师身份与角色；
  2. 校验 `scheduleId` 是否允许当前用户发起实时分析；
  3. 创建一条 `analysis_task` 记录：`media_type=3`, `file_id=NULL`, `status=1`；
  4. 为当前 WebSocket 会话绑定 `taskId`，用于后续实时明细入库。

- **心跳检测（Heartbeat）与断线重连规范**：
  - **心跳发送频率**：前端每隔 30 秒发送一次 Ping 消息。
  - **前端心跳格式**：`{"type": "ping", "timestamp": 1684567890123}`
  - **后端响应格式**：`{"type": "pong", "timestamp": 1684567890123}`
  - **超时断线判定**：如果前端连续 3 次（即 90 秒）没有收到后端的 Pong 响应，或者监听到 `onclose` / `onerror` 事件，则判定为连接断开，触发重连机制。

- **数据传输规范（JSON 契约）**：
  - **前端发送图像帧格式（前端主动推送）**：
    ```json
    {
      "type": "frame",
      "image": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQ..."
    }
    ```
  - **后端返回 AI 预测的坐标格式（服务器主动推送）**：
    ```json
    {
      "type": "result",
      "taskId": 10002,
      "attendanceCount": 45,
      "totalScore": 85.5,
      "details": [
        {
          "behaviorType": "正常听课",
          "count": 43,
          "boundingBoxes": [
            [10.5, 20.1, 50.8, 60.2],
            [100.0, 100.0, 150.0, 150.0]
          ]
        },
        {
          "behaviorType": "玩手机",
          "count": 2,
          "boundingBoxes": [
            [200.0, 300.0, 250.0, 350.0],
            [400.0, 100.0, 450.0, 150.0]
          ]
        }
      ]
    }
    ```

- **实时入库规则**：

  1. 趋势聚合：Java 后端按固定窗口（如每 30 秒）写入 `analysis_detail.record_type=1`，仅保存人数统计，不保存框；
  2. 违规抓拍：当某异常行为超过阈值时，Java 后端触发截图上传 OSS，并写入 `analysis_detail.record_type=2`，保存 `snapshot_url` 与对应人的 `bounding_boxes`；
  3. 会话结束时，Java 后端更新 `analysis_task.status=2/3`，并回填最终 `attendance_count` 与 `total_score`。

### 4. 主动停止实时分析任务

- **功能描述**：前端主动结束当前实时分析会话，Java 后端释放 WebSocket 关联资源并结束任务。

- **请求路径**：`/api/v1/analysis/task/{taskId}/stop`

- **请求方法**：`PUT`

- **请求格式**：`application/json`

- **接口参数**：

  - **Path 参数**：
    `taskId` (bigint) - REQUIRED. 分析任务主键 ID。

- **响应格式** (application/json)：

  ```json
  {
    "code": 200,
    "message": "实时分析任务已结束！",
    "data": null
  }
  ```

---

## 模块：模型服务回调 (Internal / Model Callback Module)

### 总体规范

该模块由 **Java 后端** 负责实现，仅允许 **Python 模型服务** 在内网或受信任网络环境下访问。建议结合以下安全措施：

1. 固定模型服务白名单 IP；
2. 要求请求头携带 `X-Model-Token`；
3. 所有回调接口均不对前端开放；
4. 接口路径建议部署在网关隐藏前缀下，但对文档统一描述为 `/api/v1/internal/...`。

```typescript
// 模型文件流回调请求 DTO (ModelFileCallbackDTO)
export interface ModelFileCallbackDTO {
  taskId: number;
  mediaType: number;       // 1-图片, 2-视频
  attendanceCount: number;
  details: Array<{
    frameTime: number;
    behaviorType: string;
    count: number;
    boundingBoxes: number[][];
  }>;
  modelMeta?: {
    modelVersion?: string;
    sampleIntervalSec?: number;
    inferenceMs?: number;
  };
}

// 模型失败回调请求 DTO (ModelFailCallbackDTO)
export interface ModelFailCallbackDTO {
  taskId: number;
  errorCode?: string;
  errorMessage: string;
}
```

### 1. 模型文件流识别成功回调

- **功能描述**：Python 模型服务在图片/视频识别完成后，将最终结构化结果回调给 Java 后端，由 Java 后端负责落库与算分。

- **请求路径**：`/api/v1/internal/model/task/callback/success`

- **请求方法**：`POST`

- **请求格式**：`application/json`

- **接口参数** (Body JSON)：对应 `ModelFileCallbackDTO` 结构

- **Java 后端处理规则**：

  1. 根据 `taskId` 查询 `analysis_task`；
  2. 校验该任务当前状态必须为 `1-分析中`；
  3. 按 `details` 遍历写入 `analysis_detail.record_type=0`；
  4. 根据当前激活的 `sys_weight_config` 计算 `total_score`；
  5. 更新 `analysis_task.status=2`, `attendance_count`, `total_score`；
  6. 若为图片任务，可统一令 `frameTime=0`。

- **响应格式** (application/json)：

  ```json
  {
    "code": 200,
    "message": "模型结果回调成功！",
    "data": null
  }
  ```

### 2. 模型文件流识别失败回调

- **功能描述**：Python 模型服务在图片/视频识别失败后，将失败原因回调给 Java 后端，由 Java 后端更新任务状态。

- **请求路径**：`/api/v1/internal/model/task/callback/fail`

- **请求方法**：`POST`

- **请求格式**：`application/json`

- **接口参数** (Body JSON)：对应 `ModelFailCallbackDTO` 结构

- **响应格式** (application/json)：

  ```json
  {
    "code": 200,
    "message": "失败状态已回写！",
    "data": null
  }
  ```

---

## 模块：报表管理 (Report Module)

### 总体规范

该模块主要用于展示和导出课堂分析任务的历史报表数据。

### 1. 分页查询报表数据

- **功能描述**：报表条件分页查询接口。

- **请求路径**：`/api/v1/report/page`

- **请求方法**：`GET`

- **请求格式**：`application/x-www-form-urlencoded`

- **接口参数** (Query Parameters)：

  | 参数名 | 类型 | 必填 | 说明 |
  |---|---|---|---|
  | page | int | 是 | 当前页码，默认 1 |
  | size | int | 是 | 每页记录数，默认 10 |
  | keyword | string | 否 | 搜索关键词(匹配课程名称或教师) |
  | startDate | string | 否 | 搜索起始日期 (YYYY-MM-DD) |
  | endDate | string | 否 | 搜索结束日期 (YYYY-MM-DD) |
  | mediaType | int | 否 | 1-图片, 2-视频, 3-实时流 |
  | status | int | 否 | 0-排队中, 1-分析中, 2-成功, 3-失败 |

- **响应格式** (application/json)：

  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "total": 145,
      "page": 1,
      "size": 10,
      "records": [
        {
          "id": 1,
          "courseName": "数据结构与算法",
          "classroomName": "多媒体阶梯教室A101",
          "teacherName": "张老师",
          "mediaType": 2,
          "createdAt": "2024-05-20 08:00:00",
          "status": 2,
          "attendanceCount": 48,
          "totalScore": 82.50
        }
      ]
    }
  }
  ```

### 2. 报表详情查询（主子表）

- **功能描述**：根据报表 ID 查询 `analysis_task` 主表信息以及对应 `analysis_detail` 明细列表。

- **请求路径**：`/api/v1/report/{id}/detail`

- **请求方法**：`GET`

- **接口参数**：

  - **Path 参数**：
    `id` (bigint) - REQUIRED. 报表（通常是分析任务表）的主键 ID。

- **响应格式** (application/json)：

  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "id": 1,
      "courseName": "数据结构与算法",
      "teacherName": "张老师",
      "classroomName": "多媒体阶梯教室A101",
      "mediaType": 2,
      "createdAt": "2024-05-20 08:00:00",
      "status": 2,
      "totalScore": 82.50,
      "attendanceCount": 48,
      "fileUrl": "https://...",
      "detailList": [
        {
          "recordType": 0,
          "frameTime": 1,
          "behaviorType": "正常听课",
          "count": 40,
          "boundingBoxes": [
            [10.0, 20.0, 50.0, 60.0]
          ],
          "snapshotUrl": null
        },
        {
          "recordType": 2,
          "frameTime": 60,
          "behaviorType": "玩手机",
          "count": 5,
          "boundingBoxes": [
            [100.0, 200.0, 150.0, 250.0]
          ],
          "snapshotUrl": "https://your-oss.aliyuncs.com/s_001.jpg"
        }
      ]
    }
  }
  ```

