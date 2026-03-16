# 课堂学生行为分析系统 API 接口设计文档

本文档用于规范“日志审计模块”的前后端接口对接标准。每个接口严格遵循 RESTful 风格，所有请求与响应体均使用 `application/json` 格式。统一接口前缀默认包含 `/api/v1`。

---

## 模块：日志审计中心 (Log Center Module)

### 总体规范与设计目标

该模块用于为后台管理端提供统一的“登录日志、操作日志、任务日志”查询与审计能力，主要面向以下场景：

1. 管理员查看系统登录历史，排查账号异常与登录失败原因；
2. 管理员查看关键业务操作留痕，支持追溯“谁在什么时间做了什么操作”；
3. 统一复用 `analysis_task_log` 作为任务执行日志来源，不重复建设任务日志表；
4. 与现有“分析任务中心模块”配合，形成“任务状态 + 执行日志 + 系统操作留痕”的完整追踪链路；
5. 本模块主要服务于后台管理与系统审计，不影响现有业务接口兼容性。

### 数据库映射与日志分类约定

本模块新增 `sys_login_log`、`sys_operation_log` 两张日志表，并复用 `analysis_task_log`：

- `sys_login_log.login_status`：`1-登录成功, 0-登录失败`
- `sys_operation_log.operation_status`：`1-操作成功, 0-操作失败`
- `analysis_task_log.status`：`1-成功日志, 0-失败日志`

推荐第一版操作日志的 `module_name / operation_type` 采用固定枚举风格，便于前端筛选与统计，例如：

- `module_name`：`分析任务中心 / 课堂分析 / 课堂分析报告 / 排课管理 / 教师管理 / 课程管理 / 教室管理 / 系统参数`
- `operation_type`：`LOGIN / CREATE_ANALYSIS_TASK / RETRY_TASK / EXPORT_REPORT / CREATE_SCHEDULE / UPDATE_SCHEDULE / DELETE_SCHEDULE / CREATE_TEACHER / UPDATE_TEACHER / DELETE_TEACHER / ACTIVATE_CONFIG`

```typescript
// 登录日志分页查询参数 DTO (LoginLogPageQueryDTO)
export interface LoginLogPageQueryDTO {
  page: number;
  size: number;
  empNo?: string;             // 工号模糊查询
  userName?: string;          // 用户姓名模糊查询
  loginStatus?: number;       // 1-成功, 0-失败
  startDate?: string;         // YYYY-MM-DD
  endDate?: string;           // YYYY-MM-DD
}

// 登录日志单条记录 DTO (LoginLogRecordDTO)
export interface LoginLogRecordDTO {
  id: number;
  empNo: string;
  userName?: string;
  loginStatus: number;
  loginMessage?: string;
  ipAddress?: string;
  userAgent?: string;
  createdAt: string;
}

// 操作日志分页查询参数 DTO (OperationLogPageQueryDTO)
export interface OperationLogPageQueryDTO {
  page: number;
  size: number;
  empNo?: string;
  moduleName?: string;
  operationType?: string;
  operationStatus?: number;   // 1-成功, 0-失败
  startDate?: string;
  endDate?: string;
}

// 操作日志单条记录 DTO (OperationLogRecordDTO)
export interface OperationLogRecordDTO {
  id: number;
  empNo: string;
  userName?: string;
  moduleName: string;
  operationType: string;
  businessId?: number;
  operationDesc?: string;
  operationStatus: number;
  requestMethod?: string;
  requestUrl?: string;
  createdAt: string;
}

// 任务日志分页查询参数 DTO (TaskLogPageQueryDTO)
export interface TaskLogPageQueryDTO {
  page: number;
  size: number;
  taskId?: number;
  stage?: string;
  status?: number;            // 1-成功日志, 0-失败日志
  startDate?: string;
  endDate?: string;
}

// 任务日志单条记录 DTO (TaskLogRecordDTO)
export interface TaskLogRecordDTO {
  id: number;
  taskId: number;
  stage: string;
  status: number;
  message: string;
  detailJson?: any;
  createdAt: string;
}
```

### 安全规则（必须遵守）

1. 本模块建议仅允许超级管理员访问；
2. 前端禁止传递 `role`、`userId`、`teacherId` 等身份字段；
3. 后端必须根据请求头 Token 解析当前登录用户角色；
4. 普通教师默认无权访问全系统日志中心；
5. 所有分页接口统一走 Query Parameters，不接受前端篡改数据权限范围。

---

### 1. 分页查询登录日志

- **功能描述**：
  提供后台“登录日志”页所需的分页查询能力，用于审计用户登录成功/失败历史。

- **请求路径**：`/api/v1/log/login/page`

- **请求方法**：`GET`

- **请求格式**：`application/x-www-form-urlencoded`

- **接口参数** (Query Parameters)：

  | 参数名 | 类型 | 必填 | 说明 |
  |---|---|---|---|
  | page | int | 是 | 当前页码，默认 1 |
  | size | int | 是 | 每页记录数，默认 10 |
  | empNo | string | 否 | 工号模糊查询 |
  | userName | string | 否 | 用户姓名模糊查询 |
  | loginStatus | int | 否 | 1-登录成功, 0-登录失败 |
  | startDate | string | 否 | 查询起始日期 (YYYY-MM-DD) |
  | endDate | string | 否 | 查询结束日期 (YYYY-MM-DD) |

- **后端处理规则**：

  1. 仅允许超级管理员访问；
  2. 支持按工号、姓名、登录状态和时间范围筛选；
  3. `userAgent` 建议完整保留在详情或 tooltip 中，列表页可只展示摘要；
  4. 默认按 `created_at desc` 倒序返回。

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
          "id": 1,
          "empNo": "admin",
          "userName": "系统管理员",
          "loginStatus": 1,
          "loginMessage": "登录成功",
          "ipAddress": "127.0.0.1",
          "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/134.0",
          "createdAt": "2026-03-16 08:30:12"
        },
        {
          "id": 3,
          "empNo": "T2024002",
          "userName": "李老师",
          "loginStatus": 0,
          "loginMessage": "密码错误",
          "ipAddress": "127.0.0.1",
          "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/134.0",
          "createdAt": "2026-03-16 08:36:05"
        }
      ]
    }
  }
  ```

---

### 2. 分页查询操作日志

- **功能描述**：
  提供后台“操作日志”页所需的分页查询能力，用于追溯关键业务操作记录。

- **请求路径**：`/api/v1/log/operation/page`

- **请求方法**：`GET`

- **请求格式**：`application/x-www-form-urlencoded`

- **接口参数** (Query Parameters)：

  | 参数名 | 类型 | 必填 | 说明 |
  |---|---|---|---|
  | page | int | 是 | 当前页码，默认 1 |
  | size | int | 是 | 每页记录数，默认 10 |
  | empNo | string | 否 | 工号模糊查询 |
  | moduleName | string | 否 | 模块名称筛选 |
  | operationType | string | 否 | 操作类型筛选 |
  | operationStatus | int | 否 | 1-成功, 0-失败 |
  | startDate | string | 否 | 查询起始日期 (YYYY-MM-DD) |
  | endDate | string | 否 | 查询结束日期 (YYYY-MM-DD) |

- **后端处理规则**：

  1. 仅允许超级管理员访问；
  2. 支持按模块、操作类型、时间范围等维度分页检索；
  3. `businessId` 用于关联具体业务对象，例如任务ID、排课ID、教师ID；
  4. 第一版建议重点记录：发起分析任务、重试任务、导出报表、排课增删改、基础数据增删改、参数方案激活。

- **响应格式** (application/json)：

  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "total": 6,
      "page": 1,
      "size": 10,
      "records": [
        {
          "id": 1,
          "empNo": "admin",
          "userName": "系统管理员",
          "moduleName": "分析任务中心",
          "operationType": "RETRY_TASK",
          "businessId": 10002,
          "operationDesc": "管理员重试失败任务",
          "operationStatus": 1,
          "requestMethod": "POST",
          "requestUrl": "/api/v1/task-center/10002/retry",
          "createdAt": "2026-03-15 10:05:00"
        },
        {
          "id": 3,
          "empNo": "T2024001",
          "userName": "张教授",
          "moduleName": "课堂分析报告",
          "operationType": "EXPORT_REPORT",
          "businessId": 10001,
          "operationDesc": "导出课堂分析报表",
          "operationStatus": 1,
          "requestMethod": "GET",
          "requestUrl": "/api/v1/report/export",
          "createdAt": "2026-03-10 08:10:00"
        }
      ]
    }
  }
  ```

---

### 3. 分页查询任务日志

- **功能描述**：
  提供后台“任务日志”页所需的分页查询能力。该接口复用 `analysis_task_log`，用于审计分析任务执行过程。

- **请求路径**：`/api/v1/log/task/page`

- **请求方法**：`GET`

- **请求格式**：`application/x-www-form-urlencoded`

- **接口参数** (Query Parameters)：

  | 参数名 | 类型 | 必填 | 说明 |
  |---|---|---|---|
  | page | int | 是 | 当前页码，默认 1 |
  | size | int | 是 | 每页记录数，默认 10 |
  | taskId | bigint | 否 | 指定任务ID |
  | stage | string | 否 | 阶段筛选，如 CREATED / RUNNING / FINISHED / FAILED |
  | status | int | 否 | 1-成功日志, 0-失败日志 |
  | startDate | string | 否 | 查询起始日期 (YYYY-MM-DD) |
  | endDate | string | 否 | 查询结束日期 (YYYY-MM-DD) |

- **后端处理规则**：

  1. 仅允许超级管理员访问；
  2. 按 `created_at desc` 倒序分页返回；
  3. `detailJson` 原样保留扩展信息，前端可在详情 tooltip 或弹窗中展示；
  4. 本接口用于日志中心，不替代任务中心中的 `/api/v1/task-center/{taskId}/logs` 接口。

- **响应格式** (application/json)：

  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "total": 14,
      "page": 1,
      "size": 10,
      "records": [
        {
          "id": 9,
          "taskId": 10002,
          "stage": "FAILED",
          "status": 0,
          "message": "模型服务请求超时",
          "detailJson": {
            "rawError": "Read timed out"
          },
          "createdAt": "2026-03-15 10:04:10"
        },
        {
          "id": 10,
          "taskId": 10002,
          "stage": "RETRY",
          "status": 1,
          "message": "管理员重新发起失败任务",
          "detailJson": {
            "retryCount": 1
          },
          "createdAt": "2026-03-15 10:05:00"
        }
      ]
    }
  }
  ```

---

## 后端落库建议（第一版）

### 1. 登录日志落库建议

建议在登录认证逻辑中：

- 登录成功：写 `sys_login_log.login_status = 1`
- 登录失败：写 `sys_login_log.login_status = 0`
- 同时保留 `emp_no / user_name / ip_address / user_agent / login_message`

### 2. 操作日志落库建议

第一版建议采用“关键业务点手动埋点”方式，暂不强制引入 AOP。

建议优先记录以下操作：

- 创建分析任务
- 重试分析任务
- 导出报表
- 新增/修改/删除排课
- 新增/修改/删除教师、课程、教室
- 激活评分配置方案

### 3. 任务日志复用建议

- `analysis_task_log` 继续作为任务执行日志来源；
- 任务中心详情接口与日志中心任务日志分页接口共用同一张表；
- 避免重复建设 `sys_task_log` 等表。
