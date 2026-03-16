# 课堂学生行为分析系统 API 接口设计文档

本文档用于规范“排课模块重构增强版（V6.1）”的前后端接口对接标准。每个接口严格遵循 RESTful 风格，所有请求与响应体均使用 `application/json` 格式。统一接口前缀默认包含 `/api/v1`。

---

## 模块：排课模块增强版 (Schedule Enhancement Module)

### 总体设计目标

本模块是在现有排课管理能力基础上的重构增强，目标是将原有“简单时间型排课”升级为更符合真实教务场景的“规则型排课”。

增强后的排课模块具备以下能力：

1. 支持学年、学期、星期、节次范围、周次范围、周次类型等规则字段；
2. 支持同一天连续多节课；
3. 通过 `sys_section_time` 节次时间配置表统一维护每节课的具体起止时间；
4. `course_schedule` 不再重复存储具体时钟时间，而是通过 `start_section_id / end_section_id` 关联节次表；
5. 支持教师冲突、教室冲突检测，并返回冲突明细；
6. 支持周课表视图查询；
7. 支持从排课直接进入分析任务创建页的预填信息获取；
8. 保持与分析任务模块、课堂分析报告模块、日志中心模块的兼容。

---

## 一、设计原则

### 1. 规范化设计原则
`course_schedule` 仅保留：
- 课程ID
- 教师ID
- 教室ID
- 规则型排课字段

课程名称、教师姓名、教室名称、课程编号、教师工号、教室编号等展示类字段，不直接存入排课表，而是通过联表查询返回。

### 2. 节次统一管理原则
具体上课时间不由排课表直接保存，而是由 `sys_section_time` 统一维护。  
排课表仅保存：
- 开始节次ID
- 结束节次ID

从而实现：
- 节次与时间统一配置
- 同一天连续多节课表达清晰
- 周课表视图与排课表单更直观

### 3. 规则型排课优先原则
新版排课的核心规则字段包括：
- academicYear
- semester
- weekday
- startSectionId
- endSectionId
- startWeek
- endWeek
- weekType
- studentCount
- remark

### 4. 周次类型约定
- `0`：全周
- `1`：单周
- `2`：双周

### 5. 与分析任务衔接原则
分析任务主表 `analysis_task` 仍通过 `schedule_id` 关联排课，因此现有分析任务与报表链路不需要推倒重做。

---

## 二、数据对象定义

```typescript
// 排课新增/编辑 DTO
export interface ScheduleRequestDTO {
  id?: number;

  academicYear: string;    // 学年，如 2025-2026
  semester: number;        // 1-第一学期，2-第二学期

  courseId: number;        // 课程ID
  teacherId: number;       // 教师ID
  classroomId: number;     // 教室ID

  weekday: number;         // 1-周一 ... 7-周日
  startSectionId: number;  // 开始节次ID
  endSectionId: number;    // 结束节次ID

  startWeek: number;       // 开始周
  endWeek: number;         // 结束周
  weekType: number;        // 0-全周 1-单周 2-双周

  studentCount: number;    // 应到人数
  remark?: string;         // 备注
}

// 排课分页查询 DTO
export interface SchedulePageQueryDTO {
  page: number;
  size: number;

  academicYear?: string;
  semester?: number;
  courseId?: number;
  teacherId?: number;
  classroomId?: number;
  weekday?: number;

  keyword?: string;        // 课程/教师/教室关键词模糊查询
}

// 排课分页记录 VO
export interface SchedulePageVO {
  id: number;

  academicYear: string;
  semester: number;

  courseId: number;
  courseNo: string;
  courseName: string;

  teacherId: number;
  teacherEmpNo: string;
  teacherName: string;

  classroomId: number;
  classroomNo: string;
  classroomName: string;

  weekday: number;

  startSectionId: number;
  startSectionNo: number;
  startSectionName: string;
  startSectionTime: string;

  endSectionId: number;
  endSectionNo: number;
  endSectionName: string;
  endSectionTime: string;

  startWeek: number;
  endWeek: number;
  weekType: number;

  studentCount: number;
  remark?: string;
  status: number;

  createdAt: string;
  updatedAt: string;
}

// 冲突检测 DTO
export interface ScheduleConflictCheckDTO {
  id?: number;             // 编辑时传当前排课ID，新增时不传

  academicYear: string;
  semester: number;

  teacherId: number;
  classroomId: number;

  weekday: number;
  startSectionId: number;
  endSectionId: number;

  startWeek: number;
  endWeek: number;
  weekType: number;
}

// 冲突明细 VO
export interface ScheduleConflictVO {
  hasConflict: boolean;

  teacherConflict: boolean;
  classroomConflict: boolean;

  teacherConflictList: ScheduleConflictItemVO[];
  classroomConflictList: ScheduleConflictItemVO[];
}

// 冲突项 VO
export interface ScheduleConflictItemVO {
  id: number;
  courseName: string;
  teacherName: string;
  classroomName: string;

  weekday: number;
  startSectionNo: number;
  endSectionNo: number;
  startWeek: number;
  endWeek: number;
  weekType: number;

  remark?: string;
}

// 周课表查询 DTO
export interface WeekScheduleQueryDTO {
  academicYear: string;
  semester: number;
  teacherId?: number;
  classroomId?: number;
}

// 周课表单元 VO
export interface WeekScheduleItemVO {
  id: number;

  weekday: number;

  startSectionId: number;
  startSectionNo: number;
  startSectionName: string;
  startSectionTime: string;

  endSectionId: number;
  endSectionNo: number;
  endSectionName: string;
  endSectionTime: string;

  courseName: string;
  teacherName: string;
  classroomName: string;

  startWeek: number;
  endWeek: number;
  weekType: number;
  studentCount: number;
  remark?: string;
}

// 节次配置 VO
export interface SectionTimeVO {
  id: number;
  sectionNo: number;
  sectionName: string;
  startTime: string;
  endTime: string;
  status: number;
  sortNo: number;
}

// 从排课发起分析预填 VO
export interface ScheduleAnalysisInitVO {
  scheduleId: number;

  academicYear: string;
  semester: number;

  courseId: number;
  courseName: string;

  teacherId: number;
  teacherName: string;

  classroomId: number;
  classroomName: string;

  weekday: number;

  startSectionId: number;
  startSectionNo: number;
  startSectionName: string;
  startSectionTime: string;

  endSectionId: number;
  endSectionNo: number;
  endSectionName: string;
  endSectionTime: string;

  startWeek: number;
  endWeek: number;
  weekType: number;

  studentCount: number;
  remark?: string;
}
```

------

## 三、节次表接口

### 1. 查询节次列表

- **功能描述**：
   查询系统节次时间配置，用于排课表单、周课表视图和节次下拉框。
- **请求路径**：`/api/v1/admin/schedule/section-time/list`
- **请求方法**：`GET`
- **权限要求**：管理员
- **响应示例**

```
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "sectionNo": 1,
      "sectionName": "第1节",
      "startTime": "08:00:00",
      "endTime": "08:45:00",
      "status": 1,
      "sortNo": 1
    },
    {
      "id": 2,
      "sectionNo": 2,
      "sectionName": "第2节",
      "startTime": "08:55:00",
      "endTime": "09:40:00",
      "status": 1,
      "sortNo": 2
    },
    {
      "id": 3,
      "sectionNo": 3,
      "sectionName": "第3节",
      "startTime": "10:10:00",
      "endTime": "10:55:00",
      "status": 1,
      "sortNo": 3
    }
  ]
}
```

------

## 四、排课管理接口

### 2. 分页查询排课

- **功能描述**：
   管理员分页查询排课数据，支持按学年、学期、课程、教师、教室、星期及关键词筛选。
- **请求路径**：`/api/v1/admin/schedule/page`
- **请求方法**：`GET`
- **权限要求**：管理员
- **接口参数（Query Parameters）**

| 参数名       | 类型   | 必填 | 说明                 |
| ------------ | ------ | ---- | -------------------- |
| page         | int    | 是   | 当前页码             |
| size         | int    | 是   | 每页大小             |
| academicYear | string | 否   | 学年                 |
| semester     | int    | 否   | 学期                 |
| courseId     | long   | 否   | 课程ID               |
| teacherId    | long   | 否   | 教师ID               |
| classroomId  | long   | 否   | 教室ID               |
| weekday      | int    | 否   | 星期                 |
| keyword      | string | 否   | 课程/教师/教室关键词 |

- **响应示例**

```
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 3,
    "page": 1,
    "size": 10,
    "records": [
      {
        "id": 1,
        "academicYear": "2025-2026",
        "semester": 2,
        "courseId": 1,
        "courseNo": "CS101",
        "courseName": "数据结构与算法",
        "teacherId": 2,
        "teacherEmpNo": "T2024001",
        "teacherName": "张教授",
        "classroomId": 1,
        "classroomNo": "A101",
        "classroomName": "多媒体阶梯教室A101",
        "weekday": 1,
        "startSectionId": 1,
        "startSectionNo": 1,
        "startSectionName": "第1节",
        "startSectionTime": "08:00:00",
        "endSectionId": 2,
        "endSectionNo": 2,
        "endSectionName": "第2节",
        "endSectionTime": "09:40:00",
        "startWeek": 1,
        "endWeek": 16,
        "weekType": 0,
        "studentCount": 58,
        "remark": "数据结构 周一 第1-2节 全周",
        "status": 2,
        "createdAt": "2026-03-08 09:00:00",
        "updatedAt": "2026-03-10 09:40:00"
      }
    ]
  }
}
```

------

### 3. 新增排课

- **功能描述**：
   新增一条规则型排课记录。提交前必须进行冲突检测，防止教师或教室出现排课冲突。
- **请求路径**：`/api/v1/admin/schedule`
- **请求方法**：`POST`
- **权限要求**：管理员
- **请求体**：`ScheduleRequestDTO`
- **请求示例**

```
{
  "academicYear": "2025-2026",
  "semester": 2,
  "courseId": 1,
  "teacherId": 2,
  "classroomId": 1,
  "weekday": 2,
  "startSectionId": 1,
  "endSectionId": 3,
  "startWeek": 1,
  "endWeek": 16,
  "weekType": 0,
  "studentCount": 60,
  "remark": "数据结构 周二 第1-3节 全周"
}
```

- **后端处理规则**
  1. 校验课程、教师、教室、开始节次、结束节次是否存在；
  2. 校验 `startSectionId <= endSectionId`；
  3. 校验 `startWeek <= endWeek`；
  4. 进行教师冲突检测；
  5. 进行教室冲突检测；
  6. 无冲突时写入数据库并记录日志。
- **成功响应示例**

```
{
  "code": 200,
  "message": "新增排课成功！",
  "data": null
}
```

- **失败响应示例（冲突）**

```
{
  "code": 400,
  "message": "排课冲突：教师与教室均存在冲突！",
  "data": {
    "teacherConflict": true,
    "classroomConflict": true
  }
}
```

------

### 4. 修改排课

- **功能描述**：
   修改现有排课规则，编辑时冲突检测必须排除当前排课记录本身。
- **请求路径**：`/api/v1/admin/schedule`
- **请求方法**：`PUT`
- **权限要求**：管理员
- **请求体**：`ScheduleRequestDTO`
- **后端处理规则**
  1. `id` 必填；
  2. 保持与新增接口相同的校验规则；
  3. 冲突检测时排除当前 `id`；
  4. 更新成功后记录日志。
- **请求示例**

```
{
  "id": 2,
  "academicYear": "2025-2026",
  "semester": 2,
  "courseId": 2,
  "teacherId": 2,
  "classroomId": 2,
  "weekday": 3,
  "startSectionId": 3,
  "endSectionId": 4,
  "startWeek": 1,
  "endWeek": 16,
  "weekType": 1,
  "studentCount": 46,
  "remark": "操作系统 周三 第3-4节 单周（已修改）"
}
```

------

### 5. 删除排课

- **功能描述**：
   删除指定排课记录。
- **请求路径**：`/api/v1/admin/schedule/{id}`
- **请求方法**：`DELETE`
- **权限要求**：管理员
- **路径参数**

| 参数名 | 类型 | 必填 | 说明   |
| ------ | ---- | ---- | ------ |
| id     | long | 是   | 排课ID |

- **成功响应示例**

```
{
  "code": 200,
  "message": "删除成功",
  "data": null
}
```

------

## 五、冲突检测接口

### 6. 排课冲突检测

- **功能描述**：
   根据排课规则检测教师冲突和教室冲突，并返回冲突明细列表。
- **请求路径**：`/api/v1/admin/schedule/conflict/check`
- **请求方法**：`POST`
- **权限要求**：管理员
- **请求体**：`ScheduleConflictCheckDTO`
- **请求示例**

```
{
  "academicYear": "2025-2026",
  "semester": 2,
  "teacherId": 2,
  "classroomId": 1,
  "weekday": 1,
  "startSectionId": 1,
  "endSectionId": 2,
  "startWeek": 1,
  "endWeek": 16,
  "weekType": 0
}
```

- **响应示例**

```
{
  "code": 200,
  "message": "success",
  "data": {
    "hasConflict": true,
    "teacherConflict": true,
    "classroomConflict": true,
    "teacherConflictList": [
      {
        "id": 1,
        "courseName": "数据结构与算法",
        "teacherName": "张教授",
        "classroomName": "多媒体阶梯教室A101",
        "weekday": 1,
        "startSectionNo": 1,
        "endSectionNo": 2,
        "startWeek": 1,
        "endWeek": 16,
        "weekType": 0,
        "remark": "数据结构 周一 第1-2节 全周"
      }
    ],
    "classroomConflictList": [
      {
        "id": 1,
        "courseName": "数据结构与算法",
        "teacherName": "张教授",
        "classroomName": "多媒体阶梯教室A101",
        "weekday": 1,
        "startSectionNo": 1,
        "endSectionNo": 2,
        "startWeek": 1,
        "endWeek": 16,
        "weekType": 0,
        "remark": "数据结构 周一 第1-2节 全周"
      }
    ]
  }
}
```

------

## 六、周课表视图接口

### 7. 周课表查询

- **功能描述**：
   为前端周课表视图提供数据，支持按学年、学期、教师、教室进行筛选。
- **请求路径**：`/api/v1/admin/schedule/week-view`
- **请求方法**：`GET`
- **权限要求**：管理员
- **接口参数（Query Parameters）**

| 参数名       | 类型   | 必填 | 说明   |
| ------------ | ------ | ---- | ------ |
| academicYear | string | 是   | 学年   |
| semester     | int    | 是   | 学期   |
| teacherId    | long   | 否   | 教师ID |
| classroomId  | long   | 否   | 教室ID |

- **响应示例**

```
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "weekday": 1,
      "startSectionId": 1,
      "startSectionNo": 1,
      "startSectionName": "第1节",
      "startSectionTime": "08:00:00",
      "endSectionId": 2,
      "endSectionNo": 2,
      "endSectionName": "第2节",
      "endSectionTime": "09:40:00",
      "courseName": "数据结构与算法",
      "teacherName": "张教授",
      "classroomName": "多媒体阶梯教室A101",
      "startWeek": 1,
      "endWeek": 16,
      "weekType": 0,
      "studentCount": 58,
      "remark": "数据结构 周一 第1-2节 全周"
    },
    {
      "id": 5,
      "weekday": 2,
      "startSectionId": 1,
      "startSectionNo": 1,
      "startSectionName": "第1节",
      "startSectionTime": "08:00:00",
      "endSectionId": 3,
      "endSectionNo": 3,
      "endSectionName": "第3节",
      "endSectionTime": "10:55:00",
      "courseName": "数据结构与算法",
      "teacherName": "张教授",
      "classroomName": "多媒体阶梯教室A101",
      "startWeek": 1,
      "endWeek": 16,
      "weekType": 0,
      "studentCount": 60,
      "remark": "数据结构 周二 第1-3节 全周（连续三节）"
    }
  ]
}
```

------

## 七、从排课发起分析预填接口

### 8. 获取排课分析预填信息

- **功能描述**：
   根据排课 ID 返回分析任务创建页所需的预填信息，不直接创建分析任务，只负责提供排课上下文。
- **请求路径**：`/api/v1/admin/schedule/{id}/analysis-init`
- **请求方法**：`GET`
- **权限要求**：管理员
- **路径参数**

| 参数名 | 类型 | 必填 | 说明   |
| ------ | ---- | ---- | ------ |
| id     | long | 是   | 排课ID |

- **响应示例**

```
{
  "code": 200,
  "message": "success",
  "data": {
    "scheduleId": 1,
    "academicYear": "2025-2026",
    "semester": 2,
    "courseId": 1,
    "courseName": "数据结构与算法",
    "teacherId": 2,
    "teacherName": "张教授",
    "classroomId": 1,
    "classroomName": "多媒体阶梯教室A101",
    "weekday": 1,
    "startSectionId": 1,
    "startSectionNo": 1,
    "startSectionName": "第1节",
    "startSectionTime": "08:00:00",
    "endSectionId": 2,
    "endSectionNo": 2,
    "endSectionName": "第2节",
    "endSectionTime": "09:40:00",
    "startWeek": 1,
    "endWeek": 16,
    "weekType": 0,
    "studentCount": 58,
    "remark": "数据结构 周一 第1-2节 全周"
  }
}
```

------

## 八、冲突检测逻辑说明

### 1. 节次范围重叠判定

由于排课使用 `start_section_id / end_section_id` 表示节次范围，因此只要两个排课的节次区间有交集，即视为节次冲突。

判定方式可抽象为：

- `max(startSectionNo1, startSectionNo2) <= min(endSectionNo1, endSectionNo2)`

### 2. 周次范围重叠判定

若两个排课的周次区间存在交集，则视为周次重叠。

判定方式：

- `max(startWeek1, startWeek2) <= min(endWeek1, endWeek2)`

### 3. 周次类型重叠判定

- 一方为全周（0），则与另一方视为可能重叠；
- 一方为单周（1），另一方为双周（2），通常视为不冲突；
- 双方同为单周或同为双周，且周次范围有交集，则视为冲突。

### 4. 教师冲突成立条件

以下条件同时满足时，视为教师冲突：

1. 学年相同；
2. 学期相同；
3. 教师相同；
4. 星期相同；
5. 节次范围重叠；
6. 周次范围重叠；
7. 周次类型可重叠。

### 5. 教室冲突成立条件

以下条件同时满足时，视为教室冲突：

1. 学年相同；
2. 学期相同；
3. 教室相同；
4. 星期相同；
5. 节次范围重叠；
6. 周次范围重叠；
7. 周次类型可重叠。

### 6. 编辑场景排除自身

当接口传入 `id` 时，冲突检测需排除该条排课本身，避免误报。

------

## 九、与分析任务模块的衔接说明

本模块不改变 `analysis_task` 表结构，仍保持：

- `analysis_task.schedule_id -> course_schedule.id`

因此现有分析任务模块、分析任务中心、课堂分析报告模块继续沿用原有逻辑。

从排课发起分析的推荐流程为：

1. 前端在排课列表点击“发起分析”；
2. 调用 `/api/v1/admin/schedule/{id}/analysis-init` 获取预填信息；
3. 前端跳转到分析任务创建页面；
4. 用户补充媒体源信息后，调用原有分析任务创建接口；
5. 后端仍通过 `schedule_id` 建立分析任务与排课的关联。

------

## 十、权限与兼容性说明

### 1. 权限要求

排课增强相关接口建议仅管理员访问，普通教师不开放排课管理能力。

### 2. 与旧功能兼容

本次重构后，排课模块全面转向规则型排课。
 由于数据库模型已明确不再保存 `start_time / end_time`，后续前端与后端应统一基于节次表工作，不再依赖时间型排课字段。

### 3. 日志要求

新增排课、修改排课、删除排课仍需写入操作日志，并保持现有日志中心兼容。

### 4. 不重复存展示字段

课程编号、课程名称、教师工号、教师姓名、教室编号、教室名称等均通过联表返回，不直接存入 `course_schedule` 表