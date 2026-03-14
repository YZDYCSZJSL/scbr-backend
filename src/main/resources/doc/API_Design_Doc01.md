# 课堂学生行为分析系统 API 接口设计文档

本文档用于规范前后端接口对接标准，每个接口严格遵循 RESTful 风格，所有请求与响应体均使用 `application/json` 格式（除文件上传外）。统一接口前缀默认包含 `/api/v1`。

***

## 模块：安全认证 (Auth Module)

### 总体规范与 DTO 定义

此模块处理用户的登录授权，需匹配 `teacher` 表中的 `emp_no` 与密码字段。

```typescript
// 登录验证请求结构 (LoginRequestDTO)
export interface LoginRequestDTO {
  username: string;        // 对应 user / teacher 表的 emp_no 字段
  password: string;        // 原始明文密码，后端需通过 BCrypt / MD5 验证密文
}

// 登录成功响应结构 (LoginResponseDTO)
export interface LoginResponseDTO {
  token: string;           // 用于后续访问的 JWT 身份令牌
  userInfo: {
    id: number;
    emp_no: string;
    name: string;
    role: number;          // 0-普通教师, 1-超级管理员 (用于前端路由鉴权)
    department?: string;
  }
}
```

### 1. 账号密码登录授权

- **功能描述**：用户身份验证与 Token 发放。后端需以不可逆加密算法 (如 BCrypt 或带盐 MD5) 对比存储密码。

- **请求路径**：`/api/v1/auth/login`

- **请求方法**：`POST`

- **请求格式**：`application/json`

- **接口参数** (Body JSON)：对应 `LoginRequestDTO` 结构

- **响应格式** (application/json)：

  ```json
  // 成功响应 (HTTP 200)
  {
    "code": 200,
    "message": "登录成功",
    "data": {
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "userInfo": {
        "id": 1,
        "emp_no": "admin",
        "name": "超级管理员",
        "role": 1,
        "department": "信息中心"
      }
    }
  }
  
  // 账号或密码错误 (HTTP 200，但附带错误码)
  {
    "code": 401,
    "message": "用户名或密码错误！",
    "data": null
  }
  
  // 账号被禁用场景
  {
    "code": 403,
    "message": "该账号已被禁用，请联系管理员！",
    "data": null
  }
  ```

---

## 模块：教师管理 (Admin / Teacher Module)

### 统一定义

- 教师账号对应数据库的 `emp_no` (工号) 字段。
- 密码要求：前端传递的密码必须经过基础校验，后端存入数据库时**必须**对 `password` 字段进行安全哈希处理（如 `BCrypt` 加盐或二次 `MD5` 等密文存储形式）。

### 1. 分页查询教师列表

- **功能描述**：用于在教师基础管理页面按多条件拉取带分页的教师详细信息列表。

- **请求路径**：`/api/v1/admin/teacher/page`

- **请求方法**：`GET`

- **请求格式**：`application/x-www-form-urlencoded`

- **接口参数** (Query Parameters)：

  | 参数名  | 类型   | 必填 | 说明                                    |
  | ------- | ------ | ---- | --------------------------------------- |
  | page    | int    | 是   | 当前页码，默认 1                        |
  | size    | int    | 是   | 每页记录数，默认 10                     |
  | keyword | string | 否   | 搜索关键词(匹配姓名/工号/部门)          |
  | role    | int    | 否   | 账户角色筛选 (0-普通教师, 1-超级管理员) |
  | status  | int    | 否   | 状态筛选 (0-禁用, 1-正常)               |

- **响应格式** (application/json)：

  ```json
  // 成功响应 Example
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
          "emp_no": "admin",
          "name": "超级管理员",
          "department": "信息中心",
          "phone": "13800000000",
          "role": 1,
          "status": 1,
          "created_at": "2024-01-01 10:00:00",
          "updated_at": "2024-01-05 12:00:00"
        }
      ]
    }
  }
  ```

### 2. 新增教师账号

- **功能描述**：录入新教师基础信息并为其下发系统登录账号。必须包含初始登录密码。

- **请求路径**：`/api/v1/admin/teacher`

- **请求方法**：`POST`

- **请求格式**：`application/json`

- **接口参数** (Body JSON)：

  | 参数名     | 类型   | 必填 | 说明                                |
  | ---------- | ------ | ---- | ----------------------------------- |
  | emp_no     | string | 是   | 唯一教师工号(登录账号)              |
  | password   | string | 是   | 初始明文密码 (由后端进行不可逆加密) |
  | name       | string | 是   | 真实姓名                            |
  | department | string | 是   | 所属教学部门名称                    |
  | phone      | string | 否   | 11位有效联系电话                    |
  | role       | int    | 是   | (0-普通教师, 1-超级管理员)          |
  | status     | int    | 是   | (0-禁用, 1-正常)                    |

- **响应格式** (application/json)：

  ```json
  {
    "code": 200,
    "message": "新增教师成功！",
    "data": {
      "id": 169
    }
  }
  ```

### 3. 编辑教师信息

- **功能描述**：更新已存在教师的基础归属信息或角色权限。如果提供了新密码，则触发用户的账号密码变更逻辑。

- **请求路径**：`/api/v1/admin/teacher`

- **请求方法**：`PUT`

- **请求格式**：`application/json`

- **接口参数** (Body JSON)：

  | 参数名     | 类型   | 必填 | 说明                                   |
  | ---------- | ------ | ---- | -------------------------------------- |
  | id         | bigint | 是   | 教师主键 ID                            |
  | emp_no     | string | 否   | 唯一教师工号(登录账号) 修改需校验重名  |
  | password   | string | 否   | 新修改密码，为空则表示不重置保持原密文 |
  | name       | string | 否   | 真实姓名                               |
  | department | string | 否   | 所属教学部门名称                       |
  | phone      | string | 否   | 11位有效联系电话                       |
  | role       | int    | 否   | 账户角色覆盖                           |

- **响应格式** (application/json)：

  ```json
  {
    "code": 200,
    "message": "修改保存成功！",
    "data": null
  }
  ```

### 4. 禁用/启用教师账号

- **功能描述**：用于快速切换单个教师账号的登录权限状态。

- **请求路径**：`/api/v1/admin/teacher/status/{id}`

- **请求方法**：`PUT`

- **请求格式**：`application/json`

- **接口参数**：

  - **Path 参数**：
    `id` (bigint) - REQUIRED. 目标教师的 ID 主键。
  - **Query/Body 参数**：
    `status` (int) - REQUIRED. 目标切换状态 (0-禁用, 1-正常) (建议走 Body 传递)

- **响应格式** (application/json)：

  ```json
  {
    "code": 200,
    "message": "状态切换成功！",
    "data": null
  }
  ```

### 5. 删除教师记录

- **功能描述**：永久移除无关账号。如果检测到历史排课/分析任务外键依赖，则应当拦截抛出提示。

- **请求路径**：`/api/v1/admin/teacher/{id}`

- **请求方法**：`DELETE`

- **请求格式**：`*/*`

- **接口参数**：

  - **Path 参数**：
    `id` (bigint) - REQUIRED. 目标教师的 ID 主键。

- **响应格式** (application/json)：

  ```json
  // 成功响应
  {
    "code": 200,
    "message": "删除成功！",
    "data": null
  }
  
  // 失败响应 Example (涉及主外键拦截)
  {
    "code": 500,
    "message": "删除失败，该账号已存关联排课信息！",
    "data": null
  }
  ```

---

## 模块：课程管理 (Admin / Course Module)

### 总体规范

- 数据库表：`course`
- 关键字段：`course_no` (课程编号), `name` (课程名称), `department` (所属部门), `hours` (学时), `status` (1-正常, 0-停课)

### 1. 分页查询课程列表

- **功能描述**：多维度条件检索课程台账及分页信息。

- **请求路径**：`/api/v1/admin/course/page`

- **请求方法**：`GET`

- **接口参数** (Query Parameters)：

  | 参数名  | 类型   | 必填 | 说明                                       |
  | ------- | ------ | ---- | ------------------------------------------ |
  | page    | int    | 是   | 当前页码，默认 1                           |
  | size    | int    | 是   | 每页记录数，默认 10                        |
  | keyword | string | 否   | 搜索关键词(匹配课程编号/课程名称/所属部门) |
  | status  | int    | 否   | 状态筛选 (0-停课, 1-正常)                  |

- **响应格式** (application/json)：

  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "total": 35,
      "records": [
        {
          "id": 1,
          "course_no": "C001",
          "name": "数据结构与算法",
          "department": "计算机学院",
          "hours": 48,
          "status": 1,
          "created_at": "2024-05-20 10:00:00"
        }
      ]
    }
  }
  ```

### 2. 新增课程

- **功能描述**：用于添加新开设的课程信息（可携带课程大纲附件地址）。

- **请求路径**：`/api/v1/admin/course`

- **请求方法**：`POST`

- **请求格式**：`application/json`

- **接口参数** (Body JSON)：

  | 参数名     | 类型   | 必填 | 说明             |
  | ---------- | ------ | ---- | ---------------- |
  | course_no  | string | 是   | 唯一课程编号     |
  | name       | string | 是   | 课程名称         |
  | department | string | 否   | 所属教学部门     |
  | hours      | int    | 否   | 总学时           |
  | status     | int    | 是   | (1-正常, 0-停课) |

- **响应格式** (application/json)：

  ```json
  { "code": 200, "message": "新增课程成功！" }
  ```

### 3. 编辑课程 & 状态停课切换

- **功能描述**：修改已有课程的学时或所属部门，以及停启课操作。

- **请求路径**：`/api/v1/admin/course`

- **请求方法**：`PUT`

- **请求格式**：`application/json`

- **接口参数** (Body JSON)：

  | 参数名     | 类型   | 必填 | 说明             |
  | ---------- | ------ | ---- | ---------------- |
  | id         | bigint | 是   | 主键 ID          |
  | name       | string | 否   | 课程名称         |
  | department | string | 否   | 所属教学部门     |
  | hours      | int    | 否   | 总学时           |
  | status     | int    | 否   | (1-正常, 0-停课) |

- **响应格式** (application/json)：

  ```json
  { "code": 200, "message": "保存成功！" }
  ```

### 4. 删除课程

- **请求路径**：`/api/v1/admin/course/{id}`
- **请求方法**：`DELETE`
- **说明**：有排课外键引用的课程无法直接删除。

---

## 模块：教室管理 (Admin / Classroom Module)

### 总体规范

- 数据库表：`classroom`
- 关键字段：`room_no` (教室编号), `name` (教室名称), `location` (位置), `capacity` (容量限额), `status` (1-可用, 0-维护中)

### 1. 分页查询教室列表

- **功能描述**：查询物理空间的教室集合。

- **请求路径**：`/api/v1/admin/classroom/page`

- **请求方法**：`GET`

- **接口参数** (Query Parameters)：

  | 参数名  | 类型   | 必填 | 说明                       |
  | ------- | ------ | ---- | -------------------------- |
  | page    | int    | 是   | 当前页码，默认 1           |
  | size    | int    | 是   | 每页记录数，默认 10        |
  | keyword | string | 否   | 按照编号/名称/具体位置搜索 |
  | status  | int    | 否   | (0-维护中, 1-可用)         |

- **响应格式** (application/json)：

  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "total": 52,
      "records": [
        {
          "id": 1,
          "room_no": "A101",
          "name": "多媒体阶梯教室A101",
          "location": "一教1楼",
          "capacity": 60,
          "status": 1,
          "created_at": "2024-05-20 10:00:00"
        }
      ]
    }
  }
  ```

### 2. 新增与编辑教室

- **方法一致**：新增(`POST`) / 编辑(`PUT`) `/api/v1/admin/classroom`

- **请求格式**：`application/json`

- **参数矩阵**：

  | 参数名   | 类型   | 说明                 |
  | -------- | ------ | -------------------- |
  | id       | bigint | 编辑必传             |
  | room_no  | string | 唯一教室编号         |
  | name     | string | 教室全称             |
  | location | string | 精确到楼层的具体位置 |
  | capacity | int    | 容纳学生上限         |
  | status   | int    | (0-维护中, 1-可用)   |

- **响应格式** (application/json)：

  ```json
  { "code": 200, "message": "保存成功！" }
  ```

### 3. 删除教室

- **请求路径**：`/api/v1/admin/classroom/{id}`
- **请求方法**：`DELETE`
- **说明**：如物理教室被排课占用则抛出拦截。