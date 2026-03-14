# 课堂学生行为分析系统 API 接口设计文档

本文档用于规范前后端接口对接标准，每个接口严格遵循 RESTful 风格，所有请求与响应体均使用 `application/json` 格式（除文件上传外）。统一接口前缀默认包含 `/api/v1`。

---

## 模块：排课管理 (Admin / Schedule Module)

### 总体规范与 DTO 定义
排课是系统的核心连接枢纽，将关联 `teacher`, `course`, 与 `classroom`。
```typescript
// 排课列表响应 DTO (ScheduleResponseDTO)
export interface ScheduleResponseDTO {
  id: number;
  courseId: number;
  courseName: string;      // 从 course 表联查
  classroomId: number;
  classroomName: string;   // 从 classroom 表联查
  teacherId: number;
  teacherName: string;     // 从 teacher 表联查
  studentCount: number;    // 应到人数
  startTime: string;       // 上课时间 (例如 2024-05-20 08:00:00)
  endTime: string;         // 下课时间
  status: number;          // 0-未开始, 1-进行中, 2-已结束
}

// 排课表单提交 DTO (ScheduleRequestDTO)
export interface ScheduleRequestDTO {
  id?: number;             // 更新时必填
  courseId: number;        // 关联的课程 ID
  classroomId: number;     // 关联的教室 ID
  teacherId: number;       // 关联的教师 ID
  studentCount: number;    // 应到人数
  startTime: string;       // 格式 YYYY-MM-DD HH:mm:ss
  endTime: string;         // 格式 YYYY-MM-DD HH:mm:ss
}
```

**防冲突逻辑规范**：
在持久化层执行新增或修改排课记录之前，后端 `Service` 层必须拦截校验：
1. **教室冲突冲突**：同一 `classroomId` 在指定的 `[startTime, endTime]` 区间内，是否存在时间重叠的其他排课记录。
2. **教师分身冲突**：同一 `teacherId` 在指定的 `[startTime, endTime]` 区间内，是否存在时间重叠的其他排课记录。
若存在重叠，直接响应 `HTTP 200` 并附带特殊的通用业务级失败状态码（如 `500`）和文字提示（例如：“当前选定时段该教师已安排其他课程，请重新规划”）。


### 1. 分页查询排课列表
- **功能描述**：全景查询各教室的课程分布信息。
- **请求路径**：`/api/v1/admin/schedule/page`
- **请求方法**：`GET`
- **接口参数** (Query Parameters)：
  | 参数名 | 类型 | 必填 | 说明 |
  |---|---|---|---|
  | page | int | 是 | 当前页码，默认 1 |
  | size | int | 是 | 每页记录数，默认 10 |
  | teacherId | bigint | 否 | 特定教师排课过滤 |
  | classroomId | bigint | 否 | 特定教室排课过滤 |
  | courseId | bigint | 否 | 特定课程排课过滤 |
  | status | int | 否 | (0-未开始, 1-进行中, 2-已结束) |
- **响应格式** (application/json)：
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "total": 12,
      "page": 1,
      "size": 10,
      "records": [
        {
          "id": 1,
          "courseId": 1,
          "courseName": "数据结构与算法",
          "classroomId": 1,
          "classroomName": "多媒体阶梯教室A101",
          "teacherId": 2,
          "teacherName": "张老师",
          "studentCount": 50,
          "startTime": "2024-05-20 08:00:00",
          "endTime": "2024-05-20 09:40:00",
          "status": 2
        }
      ]
    }
  }
  ```

### 2. 新增或编辑排课
- **请求路径**：`/api/v1/admin/schedule`
- **请求方法**：`POST` (新增) / `PUT` (编辑)
- **请求格式**：`application/json`
- **接口参数**：对应上述 `ScheduleRequestDTO` JSON 格式。
- **响应格式** (application/json)：
  ```json
  // 成功响应
  { "code": 200, "message": "保存成功！数据无冲突。" }
  
  // 失败响应 Example (例如时间重叠)
  { "code": 500, "message": "排课冲突：该教室在指定时间段内已被占用！" }
  ```

### 3. 取消/删除排课
- **请求路径**：`/api/v1/admin/schedule/{id}`
- **请求方法**：`DELETE`
- **说明**：只有“未开始”(`status = 0`) 的课程可以被直接取消/删除。已进行或已出具 AI 报告的记录拒绝阻断。

### 4. 获取排课下拉列表 (实时课堂分析)
- **功能描述**：在“我的工作台”下拉框联动，针对实时流或文件流返回允许分析的排课数据。
- **请求路径**：`/api/v1/admin/schedule/analysis-list`
- **请求方法**：`GET`
- **接口参数** (Query Parameters)：
  | 参数名 | 类型 | 必填 | 说明 |
  |---|---|---|---|
  | streamType | int | 是 | 1-实时流, 2-文件流 (安全红线：绝对不允许从前端传 role, userId 等参，必须经过后端 JWT Token 解析) |
- **数据权限过滤规则 (全局底线)**：任何角色、任何流状态下，都绝对不能返回“未开始（status=0）”的排课。
- **不同角色与流类型的业务逻辑控制**：
  1. **超级管理员 (role=1)** 
     - 实时流 (`streamType=1`)：只查 **进行中** (`status=1`)
     - 文件流 (`streamType=2`)：查 **进行中及已结束** (`status IN (1, 2)`)
  2. **普通老师 (role=0)**
     - 实时流 (`streamType=1`)：只查 **自己关联** (`teacher_id` 匹配) 且 **进行中** (`status=1`)
     - 文件流 (`streamType=2`)：查 **自己关联** 且 **进行中及已结束** (`status IN (1, 2)`)
- **响应格式** (application/json)：
  ```json
  {
    "code": 200,
    "message": "success",
    "data": [
      {
        "scheduleId": 1,
        "courseName": "数据结构与算法",
        "classroomId": 1,
        "classroomName": "多媒体阶梯教室A101",
        "teacherId": 2,
        "teacherName": "张老师",
        "startTime": "2024-05-20 08:00:00",
        "endTime": "2024-05-20 09:40:00",
        "status": 2
      }
    ]
  }
  ```

---

## 模块：系统参数管理 (Admin / Config Module)

### 总体规范与 DTO 定义
此模块用于管理系统的全局行为分析配置，允许存储多套配置，但**全局仅能有一套方案处于激活状态**（即 `is_active = 1`，排他性）。

```typescript
// 具体单项行为权重对象定义（构成 config_content 数组）
export interface BehaviorWeightItem {
  behaviorType: string;    // 例如 "listening"
  name: string;            // 例如 "听讲"
  weight: number;          // 例如 5.0
}

// 导入的纯 JSON 结构体定义 (ConfigImportDTO)
export interface ConfigImportDTO {
  config_name: string;
  description: string;
  is_active?: number;
  config_content: BehaviorWeightItem[];
}

// 系统配置表单及响应体 (SystemConfigDTO)
export interface SystemConfigDTO {
  id: number;
  config_name: string;
  is_active: number;       // 0-未激活, 1-使用中
  config_content: string; // 后端可能存 JSON Array 字符串
  description: string;
  created_at?: string;
}
```

**触发独占激活机制**：
无论是 新增配置、编辑配置，还是调用 一键激活接口，只要入参中 `is_active = 1`，后端在其对应的 `Service` 事务中，必须先执行 `UPDATE sys_weight_config SET is_active = 0 WHERE id != {当前ID}`，然后再将当前 ID 设为 1，确保全局状态独占。

### 1. 分页查询方案列表
- **功能描述**：查询当前所有的配置方案。
- **请求路径**：`/api/v1/admin/config/page`
- **请求方法**：`GET`
- **响应格式** (application/json)：返回包含 `SystemConfigDTO` 的分页数组。

### 2. 新增或编辑方案
- **请求路径**：`/api/v1/admin/config`
- **请求方法**：`POST` / `PUT`
- **请求参数**：对应 `SystemConfigDTO`

### 3. 一键激活指定方案
- **功能描述**：令指定的配置立即生效接管系统评分，并取消其他所有方案的激活状态。
- **请求路径**：`/api/v1/admin/config/{id}/active`
- **请求方法**：`PUT`
- **响应格式** (application/json)：
  ```json
  { "code": 200, "message": "成功切换使用 [严格期末考试模式]" }
  ```

### 4. 方案 JSON 导入
- **功能描述**：读取用户拖拽上传的 `.json` 静态数据并直接存入数据库中。
- **请求路径**：`/api/v1/admin/config/import`
- **请求方法**：`POST`
- **请求格式**：`multipart/form-data`
- **接口参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  |---|---|---|---|
  | file | File | 是 | 上传的 JSON 配置文件，内部遵循 `ConfigImportDTO` |
- **响应格式** (application/json)：
  ```json
  { "code": 200, "message": "成功载入配置方案" }
  ```

---

## 模块：分析任务管理 (Analysis Task Module)

### 总体规范
该模块主要用于接收前端传递的课堂监控画面或本地文件，并投递给 AI 分析引擎。

### 1. 启动 AI 识别 (创建分析任务)
- **功能描述**：前端针对“文件流”模式上传图片或视频文件，并关联排课信息交由后端启动识别任务。
- **请求路径**：`/api/v1/analysis/task`
- **请求方法**：`POST`
- **请求格式**：`multipart/form-data`
- **接口参数** (FormData)：
  | 参数名 | 类型 | 必填 | 说明 |
  |---|---|---|---|
  | file | File | 是 | 用户上传的图片或视频文件内容（二进制流） |
  | fileName | string | 是 | 文件的原始名称（含后缀，如 `lecture.mp4`） |
  | scheduleId | bigint | 是 | 关联的排课 ID |
  | streamType | int | 是 | 当前分析模式（1-实时流，2-文件流）。文件上传场景应固定传 2 |
- **响应格式** (application/json)：
  ```json
  // 成功响应 (HTTP 200)
  {
    "code": 200,
    "message": "分析任务创建成功，AI 正在快马加鞭识别中！",
    "data": {
      "taskId": "a1b2c3d4e5"
    }
  }
  
  // 失败响应 Example (如缺少必要参数)
  {
    "code": 400,
    "message": "请选择要关联的排课信息！",
    "data": null
  }
  ```

### 2. 建立实时课堂分析流 (WebSocket)
- **功能描述**：建立实时视频流的分析通道，前端定时抽帧发送（Base64），后端返回 AI 预测的坐标（xyxy）供前端渲染显示。
- **连接规范**：
  - **连接地址**：`ws://<domain>/ws/v1/analysis/stream/{scheduleId}?token=xxx`
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

### 2. 单条/批量统一导出接口
- **功能描述**：根据传入的报表 ID 数组导出对应的 Excel 课堂分析报表。
- **请求路径**：`/api/v1/report/export`
- **请求方法**：`POST`
- **请求格式**：`application/json`
- **接口参数** (Body JSON)：
  | 参数名 | 类型 | 必填 | 说明 |
  |---|---|---|---|
  | ids | array[bigint] | 是 | 包含选中记录 ID 的数组，如 [1, 2, 3]。若是单条导出，则传 [id] |
- **响应格式** (文件流)：
  - `Content-Type`: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
  - 返回实际的 Excel 文件二进制流（前端通过 Blob 和 URL.createObjectURL 实现静默下载）。

### 3. 报表详情查询（主子表）
- **功能描述**：根据报表 ID 查询任务主表信息以及对应的算法分析行为明细列表 (detailList)。
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
          "behaviorType": "listening",
          "count": 30
        },
        {
          "behaviorType": "playing_phone",
          "count": 2
        }
      ]
    }
  }
  ```

