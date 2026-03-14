## 模块：数据大屏 (Dashboard Module)

### 总体规范与 DTO 定义

此模块用于为“数据大屏首页”提供统一的聚合统计结果。
 为降低前端联调复杂度，**建议第一版采用单接口聚合返回**，一次性下发：

- 当前登录用户的角色信息
- 顶部 3 张 KPI 卡片
- 行为占比画像（饼图）
- 排行图（柱状图）
- 近七日趋势图（折线图）

**核心安全规则**：

1. 前端**不得传递** `role`、`teacherId`、`userId` 等身份参数。
2. 后端必须根据请求头中的 JWT Token 解析当前登录用户，再决定返回“管理员视角”还是“普通教师视角”数据。
3. 管理员与教师端**共用同一路径**，由后端自动分流返回不同统计结果。
4. 课堂出勤率的口径统一为：
    `AI识别实到人数总和 / 应到人数总和 × 100%`
    其中：
   - 实到人数来源：`analysis_task.attendance_count`
   - 应到人数来源：`course_schedule.student_count`（排课中的应到学生人数）

```
// 顶部 KPI 单项
export interface DashboardKpiItem {
  label: string;       // 指标名称
  value: number;       // 指标值
  unit?: string;       // 单位，如 "次" "%" "分" "门"
}

// 饼图 / 柱图通用 name-value 项
export interface DashboardNameValueItem {
  name: string;        // 类别名称 / 教师名称 / 课程名称
  value: number;       // 对应数值
}

// 当前登录用户简要信息
export interface DashboardUserInfo {
  empNo: string;
  name: string;
  role: number;        // 0-普通教师, 1-超级管理员
  department?: string;
}

// 数据大屏总览响应 DTO (DashboardOverviewDTO)
export interface DashboardOverviewDTO {
  role: number;                         // 0-普通教师, 1-超级管理员
  userInfo: DashboardUserInfo;          // 当前登录用户信息

  kpi: DashboardKpiItem[];              // 顶部 3 张 KPI 卡片

  pieTitle: string;                     // 饼图标题
  pie: DashboardNameValueItem[];        // 各行为占比画像

  barTitle: string;                     // 排行图标题
  bar: DashboardNameValueItem[];        // 排行图数据

  lineTitle: string;                    // 折线图标题
  lineDates: string[];                  // 近七日日期，如 ["03-07","03-08",...]
  lineValues: number[];                 // 对应折线数值
}
```

------

### 1. 获取数据大屏总览数据

- **功能描述**：
   返回当前登录用户在“数据大屏首页”所需的全部聚合数据。
   后端需根据 JWT Token 自动识别当前用户角色：

  - `role = 1`：返回**管理员视角**
  - `role = 0`：返回**教师视角**

- **请求路径**：`/api/v1/dashboard/overview`

- **请求方法**：`GET`

- **请求格式**：`application/x-www-form-urlencoded`

- **接口参数** (Query Parameters)：

  | 参数名 | 类型 | 必填 | 说明                                             |
  | ------ | ---- | ---- | ------------------------------------------------ |
  | days   | int  | 否   | 统计窗口天数，默认 7。当前页面固定近七日时可不传 |

- **前端禁止传递的字段**：

  - `role`
  - `teacherId`
  - `userId`
  - `department`

  这些字段必须由后端从 JWT Token 中解析，不允许客户端伪造。该约束与现有“排课分析列表”接口的安全红线保持一致。

- **管理员视角下的展示口径**：

  1. **全校累计分析任务数**
      含义：全校已创建并完成的分析任务累计数量
  2. **近七日课堂出勤率**
      含义：最近 7 天内所有已完成任务的
      `attendance_count 总和 / student_count 总和 × 100%`
  3. **近七日全校课堂平均专注度得分**
      含义：最近 7 天内所有已完成任务的 `total_score` 平均值
  4. **各行为占比画像**
      建议统一为固定 7 类行为：
     - 举手回答问题
     - 阅读
     - 趴桌
     - 起立回答问题
     - 玩手机
     - 书写
     - 正常听课
  5. **全校教师课堂平均专注度排行 Top 5**
      含义：按教师维度聚合 `total_score` 平均值，取前 5 名
  6. **近七日全校课堂平均专注度趋势**
      含义：最近 7 天内，按天统计的 `total_score` 平均值趋势

- **教师视角下的展示口径**：

  1. **本学期任课课程数**
      含义：当前教师本学期关联的不同 `course_id` 数量（按课程去重）
  2. **近七日该教师课堂出勤率**
      含义：最近 7 天内该教师所有已完成任务的
      `attendance_count 总和 / student_count 总和 × 100%`
  3. **近七日课堂平均专注度得分**
      含义：最近 7 天内该教师所有已完成任务的 `total_score` 平均值
  4. **各行为占比画像**
      含义：最近 7 天内该教师范围内的行为分布
  5. **教师课程专注度排行**
      含义：按课程维度聚合当前教师的 `total_score` 平均值并排序
  6. **近七日课堂平均专注度趋势**
      含义：最近 7 天内，按天统计当前教师的 `total_score` 平均值趋势

- **响应格式** (application/json)：

  #### 成功响应 Example（管理员视角）

  ```
  {
    "code": 200,
    "message": "success",
    "data": {
      "role": 1,
      "userInfo": {
        "empNo": "ADMIN001",
        "name": "系统管理员",
        "role": 1,
        "department": "信息中心"
      },
      "kpi": [
        { "label": "全校累计分析任务数", "value": 12560, "unit": "次" },
        { "label": "近七日课堂出勤率", "value": 93.6, "unit": "%" },
        { "label": "近七日全校课堂平均专注度得分", "value": 87.5, "unit": "分" }
      ],
      "pieTitle": "各行为占比画像",
      "pie": [
        { "name": "正常听课", "value": 65000 },
        { "name": "举手回答问题", "value": 15000 },
        { "name": "阅读", "value": 12000 },
        { "name": "书写", "value": 18000 },
        { "name": "玩手机", "value": 8000 },
        { "name": "趴桌", "value": 5000 },
        { "name": "起立回答问题", "value": 3000 }
      ],
      "barTitle": "全校教师课堂平均专注度排行 Top 5",
      "bar": [
        { "name": "张教授", "value": 92.5 },
        { "name": "李老师", "value": 91.0 },
        { "name": "王老师", "value": 89.5 },
        { "name": "赵老师", "value": 86.0 },
        { "name": "孙老师", "value": 84.5 }
      ],
      "lineTitle": "近七日全校课堂平均专注度趋势",
      "lineDates": ["03-07", "03-08", "03-09", "03-10", "03-11", "03-12", "03-13"],
      "lineValues": [85.0, 86.2, 88.1, 87.0, 86.4, 89.0, 87.5]
    }
  }
  ```

  #### 成功响应 Example（教师视角）

  ```
  {
    "code": 200,
    "message": "success",
    "data": {
      "role": 0,
      "userInfo": {
        "empNo": "T2024001",
        "name": "张教授",
        "role": 0,
        "department": "计算机学院"
      },
      "kpi": [
        { "label": "本学期任课课程数", "value": 3, "unit": "门" },
        { "label": "近七日该教师课堂出勤率", "value": 96.8, "unit": "%" },
        { "label": "近七日课堂平均专注度得分", "value": 85.2, "unit": "分" }
      ],
      "pieTitle": "各行为占比画像",
      "pie": [
        { "name": "正常听课", "value": 1200 },
        { "name": "举手回答问题", "value": 300 },
        { "name": "阅读", "value": 280 },
        { "name": "书写", "value": 410 },
        { "name": "玩手机", "value": 150 },
        { "name": "趴桌", "value": 50 },
        { "name": "起立回答问题", "value": 60 }
      ],
      "barTitle": "教师课程专注度排行",
      "bar": [
        { "name": "数据结构与算法", "value": 88.5 },
        { "name": "操作系统", "value": 86.0 },
        { "name": "数据库原理", "value": 84.5 }
      ],
      "lineTitle": "近七日课堂平均专注度趋势",
      "lineDates": ["03-07", "03-08", "03-09", "03-10", "03-11", "03-12", "03-13"],
      "lineValues": [82.0, 84.0, 85.0, 83.0, 86.0, 88.0, 85.2]
    }
  }
  ```

  #### 失败响应 Example（Token 无效 / 用户不存在）

  ```
  {
    "code": 401,
    "message": "登录状态已失效，请重新登录！",
    "data": null
  }
  ```

