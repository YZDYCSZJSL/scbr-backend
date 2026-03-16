# 课堂学生行为分析系统 API 接口设计文档

本文档用于规范“分析任务中心模块”的前后端接口对接标准。每个接口严格遵循 RESTful 风格，所有请求与响应体均使用 `application/json` 格式。统一接口前缀默认包含 `/api/v1`。

---

## 模块：分析任务中心 (Task Center Module)

### 总体规范与设计目标

该模块用于将现有“分析任务创建能力”升级为“任务生命周期管理能力”，主要面向以下场景：

1. 在独立的任务中心页面分页查看所有分析任务；
2. 支持按任务状态、媒体类型、时间范围进行筛选；
3. 查看单个任务的基础信息、执行状态、失败原因与日志时间线；
4. 对失败任务执行“重新分析”；
5. 不影响现有 `/api/v1/analysis/task`、`/api/v1/analysis/task/{taskId}/status` 等旧接口的兼容性。

### 数据库映射与状态约定

本模块基于现有 `analysis_task` 主表增强实现，并新增 `analysis_task_log` 日志表。

- `analysis_task.media_type`：`1-图片, 2-视频, 3-实时流`
- `analysis_task.status`：`0-排队中, 1-分析中, 2-成功, 3-失败`
- `analysis_task.start_time`：任务正式开始分析时间
- `analysis_task.finish_time`：任务结束时间（成功或失败）
- `analysis_task.fail_reason`：失败原因摘要
- `analysis_task.retry_count`：已重试次数

- `analysis_task_log.stage`：
  - `CREATED`：任务创建成功
  - `UPLOADED`：文件上传并写入 `sys_file`
  - `RUNNING`：任务已开始分析
  - `MODEL_ACCEPTED`：模型服务已受理
  - `WAITING_CALLBACK`：等待回调 / 实时处理中
  - `FINISHED`：任务成功完成
  - `FAILED`：任务失败
  - `RETRY`：管理员手动重试
  - `STOPPED`：任务被主动停止

- `analysis_task_log.status`：`1-成功日志, 0-失败日志`

```typescript
// 任务中心分页查询参数 DTO (TaskCenterPageQueryDTO)
export interface TaskCenterPageQueryDTO {
  page: number;               // 当前页码，默认 1
  size: number;               // 每页记录数，默认 10
  keyword?: string;           // 模糊搜索关键词（课程名称 / 教师姓名 / 教室名称）
  status?: number;            // 0-排队中, 1-分析中, 2-成功, 3-失败
  mediaType?: number;         // 1-图片, 2-视频, 3-实时流
  startDate?: string;         // 查询开始日期 (YYYY-MM-DD)
  endDate?: string;           // 查询结束日期 (YYYY-MM-DD)
}

// 任务中心列表单条记录 DTO (TaskCenterRecordDTO)
export interface TaskCenterRecordDTO {
  id: number;
  courseName: string;
  classroomName: string;
  teacherName: string;
  mediaType: number;
  status: number;
  attendanceCount: number;
  totalScore: number;
  retryCount: number;
  failReason?: string;
  createdAt: string;
  startTime?: string;
  finishTime?: string;
  durationSeconds?: number;
}

// 任务中心详情响应 DTO (TaskCenterDetailDTO)
export interface TaskCenterDetailDTO {
  id: number;
  courseName: string;
  teacherName: string;
  classroomName: string;
  scheduleId: number;
  mediaType: number;
  status: number;
  fileId?: number;
  fileName?: string;
  fileUrl?: string;
  attendanceCount: number;
  totalScore: number;
  retryCount: number;
  failReason?: string;
  createdAt: string;
  startTime?: string;
  finishTime?: string;
  durationSeconds?: number;
}

// 任务执行日志 DTO (TaskLogItemDTO)
export interface TaskLogItemDTO {
  id: number;
  stage: string;
  status: number;             // 1-成功日志, 0-失败日志
  message: string;
  detailJson?: any;
  createdAt: string;
}

// 失败任务重试响应 DTO (RetryTaskResponseDTO)
export interface RetryTaskResponseDTO {
  taskId: number;
  status: number;             // 重试后通常回到 0-排队中 或 1-分析中
  retryCount: number;
}
```

### 安全规则（必须遵守）

1. 前端禁止传递 `teacherId`、`role`、`userId` 等身份字段；
2. 后端必须根据请求头 Token 解析当前登录用户；
3. 普通教师仅允许查看自己的分析任务；
4. 超级管理员允许查看全部任务并重试失败任务；
5. 失败重试只允许作用于 `status = 3` 的任务；
6. 本模块新增接口不替代现有分析任务创建/状态轮询接口，仅作为任务中心页面的增强能力。

---

### 1. 分页查询分析任务列表

- **功能描述**：
  提供“分析任务中心”列表页所需的分页数据，支持状态、媒体类型、时间范围与关键词筛选。

- **请求路径**：`/api/v1/task-center/page`

- **请求方法**：`GET`

- **请求格式**：`application/x-www-form-urlencoded`

- **接口参数** (Query Parameters)：

  | 参数名 | 类型 | 必填 | 说明 |
  |---|---|---|---|
  | page | int | 是 | 当前页码，默认 1 |
  | size | int | 是 | 每页记录数，默认 10 |
  | keyword | string | 否 | 模糊搜索关键词（课程名称 / 教师姓名 / 教室名称） |
  | status | int | 否 | 0-排队中, 1-分析中, 2-成功, 3-失败 |
  | mediaType | int | 否 | 1-图片, 2-视频, 3-实时流 |
  | startDate | string | 否 | 查询起始日期 (YYYY-MM-DD) |
  | endDate | string | 否 | 查询结束日期 (YYYY-MM-DD) |

- **后端处理规则**：

  1. 超级管理员返回全量任务列表；
  2. 普通教师仅返回 `analysis_task.teacher_id = 当前登录用户ID` 的记录；
  3. 响应中需要联查 `course_schedule / course / classroom / teacher` 组装课程、教师、教室名称；
  4. `durationSeconds` 计算口径：
     - 若 `startTime` 与 `finishTime` 均存在，则返回秒级差值；
     - 否则返回 `null`；
  5. 任务列表不返回明细 `analysis_detail` 数据。

- **响应格式** (application/json)：

  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "total": 4,
      "page": 1,
      "size": 10,
      "records": [
        {
          "id": 10001,
          "courseName": "数据结构与算法",
          "classroomName": "多媒体阶梯教室A101",
          "teacherName": "张教授",
          "mediaType": 2,
          "status": 2,
          "attendanceCount": 54,
          "totalScore": 88.50,
          "retryCount": 0,
          "failReason": null,
          "createdAt": "2026-03-10 08:00:30",
          "startTime": "2026-03-10 08:01:00",
          "finishTime": "2026-03-10 08:06:30",
          "durationSeconds": 330
        },
        {
          "id": 10002,
          "courseName": "操作系统",
          "classroomName": "智慧教室B203",
          "teacherName": "张教授",
          "mediaType": 2,
          "status": 3,
          "attendanceCount": 0,
          "totalScore": 0,
          "retryCount": 1,
          "failReason": "模型服务调用超时",
          "createdAt": "2026-03-15 10:01:10",
          "startTime": "2026-03-15 10:02:00",
          "finishTime": "2026-03-15 10:04:10",
          "durationSeconds": 130
        }
      ]
    }
  }
  ```

---

### 2. 查询分析任务详情

- **功能描述**：
  根据任务 ID 查询分析任务详情，用于任务中心的详情抽屉 / 详情页展示。

- **请求路径**：`/api/v1/task-center/{taskId}`

- **请求方法**：`GET`

- **请求格式**：`application/x-www-form-urlencoded`

- **接口参数**：

  - **Path 参数**：
    `taskId` (bigint) - REQUIRED. 分析任务主键 ID。

- **后端处理规则**：

  1. 校验当前用户是否有权限查看该任务；
  2. 详情页响应允许联查 `sys_file`，返回文件名与文件地址；
  3. 不直接返回 `analysis_detail` 明细列表；
  4. 需要返回 `failReason`、`retryCount`、`durationSeconds` 等任务中心专属字段；
  5. 若任务不存在，返回业务错误码与提示信息。

- **响应格式** (application/json)：

  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "id": 10001,
      "courseName": "数据结构与算法",
      "teacherName": "张教授",
      "classroomName": "多媒体阶梯教室A101",
      "scheduleId": 1,
      "mediaType": 2,
      "status": 2,
      "fileId": 1,
      "fileName": "ds_lesson_0310.mp4",
      "fileUrl": "https://oss.example.com/analysis/20260310/ds_lesson_0310.mp4",
      "attendanceCount": 54,
      "totalScore": 88.50,
      "retryCount": 0,
      "failReason": null,
      "createdAt": "2026-03-10 08:00:30",
      "startTime": "2026-03-10 08:01:00",
      "finishTime": "2026-03-10 08:06:30",
      "durationSeconds": 330
    }
  }
  ```

---

### 3. 查询任务执行日志

- **功能描述**：
  根据任务 ID 查询任务执行日志列表，用于详情页展示任务时间线。

- **请求路径**：`/api/v1/task-center/{taskId}/logs`

- **请求方法**：`GET`

- **请求格式**：`application/x-www-form-urlencoded`

- **接口参数**：

  - **Path 参数**：
    `taskId` (bigint) - REQUIRED. 分析任务主键 ID。

- **后端处理规则**：

  1. 需按 `created_at ASC` 排序返回，方便前端渲染时间线；
  2. 普通教师仅可查看自己的任务日志；
  3. `detailJson` 直接透传即可，前端按需显示；
  4. 若任务尚无日志，返回空数组即可，不视为异常。

- **响应格式** (application/json)：

  ```json
  {
    "code": 200,
    "message": "success",
    "data": [
      {
        "id": 1,
        "stage": "CREATED",
        "status": 1,
        "message": "任务创建成功",
        "detailJson": {
          "mediaType": 2,
          "scheduleId": 1
        },
        "createdAt": "2026-03-10 08:00:30"
      },
      {
        "id": 2,
        "stage": "UPLOADED",
        "status": 1,
        "message": "文件上传并写入 sys_file 成功",
        "detailJson": {
          "fileId": 1,
          "fileName": "ds_lesson_0310.mp4"
        },
        "createdAt": "2026-03-10 08:00:45"
      },
      {
        "id": 3,
        "stage": "RUNNING",
        "status": 1,
        "message": "任务开始进入分析中",
        "detailJson": {
          "startTime": "2026-03-10 08:01:00"
        },
        "createdAt": "2026-03-10 08:01:00"
      },
      {
        "id": 4,
        "stage": "FINISHED",
        "status": 1,
        "message": "任务执行成功并完成结果回写",
        "detailJson": {
          "attendanceCount": 54,
          "totalScore": 88.5
        },
        "createdAt": "2026-03-10 08:06:30"
      }
    ]
  }
  ```

---

### 4. 失败任务重试

- **功能描述**：
  针对已失败的任务，重新发起一次分析流程。该接口主要供管理员或任务创建者在任务中心中执行“重试”。

- **请求路径**：`/api/v1/task-center/{taskId}/retry`

- **请求方法**：`POST`

- **请求格式**：`application/json`

- **接口参数**：

  - **Path 参数**：
    `taskId` (bigint) - REQUIRED. 分析任务主键 ID。

- **后端处理规则**：

  1. 仅允许 `status = 3` 的失败任务重试；
  2. 重试时必须执行以下更新：
     - `retry_count = retry_count + 1`
     - `fail_reason = NULL`
     - `start_time = NULL`
     - `finish_time = NULL`
     - `status = 0`（重新进入排队中）
  3. 写入一条 `analysis_task_log.stage = RETRY` 的日志；
  4. 随后由后端异步执行器继续调用既有的任务分析逻辑；
  5. 若当前任务不是失败状态，则直接拦截并返回错误提示。

- **响应格式** (application/json)：

  ```json
  // 成功响应
  {
    "code": 200,
    "message": "任务已重新加入分析队列，请稍后刷新查看状态！",
    "data": {
      "taskId": 10002,
      "status": 0,
      "retryCount": 2
    }
  }

  // 失败响应 Example
  {
    "code": 500,
    "message": "当前任务状态不是失败，禁止重复重试！",
    "data": null
  }
  ```

---

## 模块：现有接口兼容性说明 (Compatibility Notes)

为降低改造风险，本次“分析任务中心模块”采用**新增接口为主、旧接口保持兼容**的策略：

1. 现有 `/api/v1/analysis/task` 创建任务接口保持不变；
2. 现有 `/api/v1/analysis/task/{taskId}/status` 轮询状态接口保持不变；
3. 现有 `/api/v1/analysis/task/{taskId}/stop` 实时流停止接口保持不变；
4. “任务中心页面”统一使用本次新增的 `/api/v1/task-center/*` 接口；
5. 后端服务层应在原任务流程关键节点同步写入 `analysis_task_log`，但不强制前端旧页面消费该数据。

---

## 模块：数据库调整摘要 (仅供后端实现参考)

### 1. `analysis_task` 表新增字段

| 字段名 | 类型 | 说明 |
|---|---|---|
| start_time | datetime | 任务开始分析时间 |
| finish_time | datetime | 任务结束时间 |
| fail_reason | varchar(255) | 失败原因 |
| retry_count | int | 重试次数，默认 0 |

### 2. 新增 `analysis_task_log` 表

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| task_id | bigint | 关联任务 ID |
| stage | varchar(50) | 执行阶段 |
| status | tinyint | 1-成功日志, 0-失败日志 |
| message | varchar(255) | 简短日志信息 |
| detail_json | json | 扩展信息 |
| created_at | datetime | 创建时间 |

---

## 模块：前端页面建议（供联调参考）

建议新增页面路径：

- `/task-center`：分析任务中心列表页

建议前端至少拆分以下组件：

1. `TaskCenter/index.vue`：任务列表页
2. `TaskDetailDrawer.vue`：任务详情抽屉
3. `TaskLogTimeline.vue`：任务日志时间线

任务列表常见筛选项：

- 状态
- 媒体类型
- 日期范围
- 关键词

任务列表常见操作项：

- 查看详情
- 失败任务重试

