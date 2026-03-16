-- ==========================================================
-- 课堂学生行为分析系统 - 数据库初始化脚本 (V6.1 排课模块重构版)
-- 说明：
-- 1. 本脚本为完整初始化脚本，会删除旧表并重建；
-- 2. 在 V5.0 日志审计模块基础上重构排课模型；
-- 3. course_schedule 不再保存 start_time / end_time；
-- 4. 采用 start_section_id / end_section_id 关联 sys_section_time；
-- 5. 支持学年、学期、星期、节次范围、周次范围、周次类型、备注；
-- 6. 保留分析任务、报表、日志中心等现有模块兼容。
-- ==========================================================

CREATE DATABASE IF NOT EXISTS `smart_classroom_db`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `smart_classroom_db`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==========================================================
-- 1. 清理旧表
-- ==========================================================
DROP TABLE IF EXISTS `sys_operation_log`;
DROP TABLE IF EXISTS `sys_login_log`;
DROP TABLE IF EXISTS `analysis_task_log`;
DROP TABLE IF EXISTS `analysis_detail`;
DROP TABLE IF EXISTS `analysis_task`;
DROP TABLE IF EXISTS `sys_weight_config`;
DROP TABLE IF EXISTS `course_schedule`;
DROP TABLE IF EXISTS `sys_section_time`;
DROP TABLE IF EXISTS `sys_file`;
DROP TABLE IF EXISTS `classroom`;
DROP TABLE IF EXISTS `course`;
DROP TABLE IF EXISTS `teacher`;

-- ==========================================================
-- 2. 基础业务表
-- ==========================================================

CREATE TABLE `teacher` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `emp_no` varchar(50) NOT NULL COMMENT '教师工号(登录账号)',
  `password` varchar(100) NOT NULL COMMENT '登录密码',
  `name` varchar(50) NOT NULL COMMENT '教师姓名',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `department` varchar(100) DEFAULT NULL COMMENT '所属教学部门',
  `role` tinyint(4) NOT NULL DEFAULT '0' COMMENT '角色：0-普通老师, 1-超级管理员',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-正常, 0-禁用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emp_no` (`emp_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师信息表';

CREATE TABLE `course` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `course_no` varchar(50) NOT NULL COMMENT '课程编号',
  `name` varchar(100) NOT NULL COMMENT '课程名称',
  `department` varchar(100) DEFAULT NULL COMMENT '所属教学部门',
  `hours` int(11) DEFAULT '0' COMMENT '总学时',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-正常, 0-停课',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_no` (`course_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程信息表';

CREATE TABLE `classroom` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `room_no` varchar(50) NOT NULL COMMENT '教室编号(如A101)',
  `name` varchar(100) NOT NULL COMMENT '教室名称',
  `location` varchar(100) DEFAULT NULL COMMENT '具体位置',
  `capacity` int(11) DEFAULT '0' COMMENT '容纳人数',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-可用, 0-维护中',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_no` (`room_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教室信息表';

CREATE TABLE `sys_file` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `original_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_key` varchar(255) NOT NULL COMMENT 'OSS对象Key',
  `file_url` varchar(500) NOT NULL COMMENT '文件访问URL',
  `file_md5` varchar(64) DEFAULT NULL COMMENT '文件MD5',
  `upload_by` bigint(20) NOT NULL COMMENT '上传者教师ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teacher_md5` (`upload_by`, `file_md5`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统文件表';

-- ==========================================================
-- 3. 节次时间配置表
-- ==========================================================

CREATE TABLE `sys_section_time` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `section_no` int(11) NOT NULL COMMENT '节次编号',
  `section_name` varchar(50) NOT NULL COMMENT '节次名称，如 第1节',
  `start_time` time NOT NULL COMMENT '开始时间',
  `end_time` time NOT NULL COMMENT '结束时间',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-启用，0-停用',
  `sort_no` int(11) NOT NULL DEFAULT '0' COMMENT '排序号',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_section_no` (`section_no`),
  KEY `idx_status_sort` (`status`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统节次时间配置表';

-- ==========================================================
-- 4. 排课表（重构版）
-- ==========================================================

CREATE TABLE `course_schedule` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',

  `academic_year` varchar(20) NOT NULL COMMENT '学年，如 2025-2026',
  `semester` tinyint(4) NOT NULL COMMENT '学期：1-第一学期，2-第二学期',

  `course_id` bigint(20) NOT NULL COMMENT '课程ID',
  `teacher_id` bigint(20) NOT NULL COMMENT '教师ID',
  `classroom_id` bigint(20) NOT NULL COMMENT '教室ID',

  `weekday` tinyint(4) NOT NULL COMMENT '星期：1-周一 ... 7-周日',
  `start_section_id` bigint(20) NOT NULL COMMENT '开始节次ID',
  `end_section_id` bigint(20) NOT NULL COMMENT '结束节次ID',

  `start_week` int(11) NOT NULL COMMENT '开始周',
  `end_week` int(11) NOT NULL COMMENT '结束周',
  `week_type` tinyint(4) NOT NULL DEFAULT '0' COMMENT '周次类型：0-全周，1-单周，2-双周',

  `student_count` int(11) NOT NULL DEFAULT '0' COMMENT '应到人数',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',

  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '状态：0-未开始, 1-进行中, 2-已结束',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  KEY `idx_course_id` (`course_id`),
  KEY `idx_teacher_id` (`teacher_id`),
  KEY `idx_classroom_id` (`classroom_id`),

  KEY `idx_schedule_term_weekday` (`academic_year`, `semester`, `weekday`),
  KEY `idx_schedule_teacher_term` (`teacher_id`, `academic_year`, `semester`),
  KEY `idx_schedule_classroom_term` (`classroom_id`, `academic_year`, `semester`),
  KEY `idx_schedule_week_range` (`start_week`, `end_week`, `week_type`),
  KEY `idx_schedule_section_range` (`start_section_id`, `end_section_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排课安排表（规则型重构版）';

-- ==========================================================
-- 5. 系统参数表
-- ==========================================================

CREATE TABLE `sys_weight_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `config_name` varchar(100) NOT NULL COMMENT '配置方案名称',
  `is_active` tinyint(2) NOT NULL DEFAULT '0' COMMENT '是否激活：1-已激活, 0-未激活',
  `config_content` json NOT NULL COMMENT '配置明文参数',
  `description` varchar(255) DEFAULT NULL COMMENT '说明',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行为评分权重配置方案表';

-- ==========================================================
-- 6. 分析任务相关表
-- ==========================================================

CREATE TABLE `analysis_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `teacher_id` bigint(20) NOT NULL COMMENT '发起分析的教师ID',
  `classroom_id` bigint(20) NOT NULL COMMENT '关联教室ID',
  `schedule_id` bigint(20) NOT NULL COMMENT '关联排课ID',
  `file_id` bigint(20) DEFAULT NULL COMMENT '关联 sys_file 的ID (实时流 media_type=3 时可为 NULL)',
  `media_type` tinyint(4) NOT NULL COMMENT '1-图片, 2-视频, 3-实时流',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0-排队中, 1-分析中, 2-成功, 3-失败',
  `attendance_count` int(11) DEFAULT '0' COMMENT 'AI识别实到人数',
  `total_score` decimal(5,2) DEFAULT '0.00' COMMENT '综合得分',
  `start_time` datetime DEFAULT NULL COMMENT '任务开始分析时间',
  `finish_time` datetime DEFAULT NULL COMMENT '任务结束时间',
  `fail_reason` varchar(255) DEFAULT NULL COMMENT '失败原因',
  `retry_count` int(11) NOT NULL DEFAULT '0' COMMENT '重试次数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_schedule_id` (`schedule_id`),
  KEY `idx_teacher_status` (`teacher_id`, `status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI分析任务主表';

CREATE TABLE `analysis_detail` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `task_id` bigint(20) NOT NULL COMMENT '关联 analysis_task 的ID',
  `record_type` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0-全量明细, 1-趋势聚合, 2-违规抓拍',
  `frame_time` int(11) DEFAULT '0' COMMENT '帧秒数/实时流秒数',
  `behavior_type` varchar(50) NOT NULL COMMENT '行为类别',
  `count` int(11) NOT NULL DEFAULT '0' COMMENT '出现人数',
  `bounding_boxes` json DEFAULT NULL COMMENT 'YOLO坐标数组',
  `snapshot_url` varchar(500) DEFAULT NULL COMMENT '关键帧抓拍图片OSS链接',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_type` (`task_id`, `record_type`),
  KEY `idx_task_behavior_time` (`task_id`, `behavior_type`, `frame_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI分析任务识别明细表';

CREATE TABLE `analysis_task_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `task_id` bigint(20) NOT NULL COMMENT '关联 analysis_task.id',
  `stage` varchar(50) NOT NULL COMMENT '阶段：CREATED/UPLOADED/RUNNING/MODEL_ACCEPTED/WAITING_CALLBACK/FINISHED/FAILED/RETRY/STOPPED',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1-成功日志, 0-失败日志',
  `message` varchar(255) NOT NULL COMMENT '简短日志信息',
  `detail_json` json DEFAULT NULL COMMENT '扩展信息(JSON)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_created` (`task_id`, `created_at`),
  KEY `idx_stage_status` (`stage`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分析任务执行日志表';

-- ==========================================================
-- 7. 日志审计模块表
-- ==========================================================

CREATE TABLE `sys_login_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `emp_no` varchar(64) NOT NULL COMMENT '工号',
  `user_name` varchar(64) DEFAULT NULL COMMENT '用户名',
  `login_status` tinyint(4) NOT NULL COMMENT '登录状态：1成功 0失败',
  `login_message` varchar(255) DEFAULT NULL COMMENT '登录结果说明',
  `ip_address` varchar(64) DEFAULT NULL COMMENT 'IP地址',
  `user_agent` varchar(512) DEFAULT NULL COMMENT '客户端信息',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_no` (`emp_no`),
  KEY `idx_login_status` (`login_status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';

CREATE TABLE `sys_operation_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `emp_no` varchar(64) NOT NULL COMMENT '操作人工号',
  `user_name` varchar(64) DEFAULT NULL COMMENT '操作人姓名',
  `module_name` varchar(64) NOT NULL COMMENT '模块名称',
  `operation_type` varchar(64) NOT NULL COMMENT '操作类型',
  `business_id` bigint(20) DEFAULT NULL COMMENT '业务ID',
  `operation_desc` varchar(255) DEFAULT NULL COMMENT '操作说明',
  `operation_status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '操作状态：1成功 0失败',
  `request_method` varchar(16) DEFAULT NULL COMMENT '请求方法',
  `request_url` varchar(255) DEFAULT NULL COMMENT '请求路径',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_emp_no` (`emp_no`),
  KEY `idx_module_name` (`module_name`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';


-- ==========================================================
-- 学期配置表 (V6.1 补充)
-- 用于存储每个学期的开学日期，支持实时流排课筛选时推算当前周次
-- ==========================================================

CREATE TABLE IF NOT EXISTS `sys_semester_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `academic_year` varchar(20) NOT NULL COMMENT '学年，如 2025-2026',
  `semester` tinyint(4) NOT NULL COMMENT '学期：1-第一学期，2-第二学期',
  `term_start_date` date NOT NULL COMMENT '学期开学日期（第1周周一）',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-启用，0-停用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_year_semester` (`academic_year`, `semester`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学期配置表';


ALTER TABLE `course_schedule`
ADD CONSTRAINT `fk_schedule_semester_config`
FOREIGN KEY (`academic_year`, `semester`)
REFERENCES `sys_semester_config` (`academic_year`, `semester`)
ON UPDATE CASCADE
ON DELETE RESTRICT;

-- 初始数据：2025-2026学年第二学期（假设2026年2月23日开学，第一周周一）
INSERT INTO `sys_semester_config`
(`academic_year`, `semester`, `term_start_date`, `status`)
VALUES
('2025-2026', 2, '2026-02-23', 1);

-- ==========================================================
-- 8. 基础数据
-- ==========================================================

INSERT INTO `teacher`
(`id`, `emp_no`, `password`, `name`, `phone`, `department`, `role`, `status`, `created_at`, `updated_at`)
VALUES
(1, 'admin', 'e10adc3949ba59abbe56e057f20f883e', '系统管理员', '13800000000', '信息中心', 1, 1, '2026-03-01 09:00:00', '2026-03-01 09:00:00'),
(2, 'T2024001', 'e10adc3949ba59abbe56e057f20f883e', '张教授', '13800000001', '计算机学院', 0, 1, '2026-03-01 09:10:00', '2026-03-01 09:10:00'),
(3, 'T2024002', 'e10adc3949ba59abbe56e057f20f883e', '李老师', '13800000002', '计算机学院', 0, 1, '2026-03-01 09:20:00', '2026-03-01 09:20:00');

INSERT INTO `course`
(`id`, `course_no`, `name`, `department`, `hours`, `status`, `created_at`, `updated_at`)
VALUES
(1, 'CS101', '数据结构与算法', '计算机学院', 48, 1, '2026-03-01 10:00:00', '2026-03-01 10:00:00'),
(2, 'CS102', '操作系统', '计算机学院', 48, 1, '2026-03-01 10:10:00', '2026-03-01 10:10:00'),
(3, 'CS103', '数据库原理', '计算机学院', 32, 1, '2026-03-01 10:20:00', '2026-03-01 10:20:00');

INSERT INTO `classroom`
(`id`, `room_no`, `name`, `location`, `capacity`, `status`, `created_at`, `updated_at`)
VALUES
(1, 'A101', '多媒体阶梯教室A101', '一教1楼', 80, 1, '2026-03-01 11:00:00', '2026-03-01 11:00:00'),
(2, 'B203', '智慧教室B203', '二教2楼', 60, 1, '2026-03-01 11:10:00', '2026-03-01 11:10:00'),
(3, 'C305', '机房C305', '实验楼3楼', 50, 1, '2026-03-01 11:20:00', '2026-03-01 11:20:00');

INSERT INTO `sys_section_time`
(`id`, `section_no`, `section_name`, `start_time`, `end_time`, `status`, `sort_no`, `created_at`, `updated_at`)
VALUES
(1, 1, '第1节', '08:00:00', '08:45:00', 1, 1, NOW(), NOW()),
(2, 2, '第2节', '08:55:00', '09:40:00', 1, 2, NOW(), NOW()),
(3, 3, '第3节', '10:10:00', '10:55:00', 1, 3, NOW(), NOW()),
(4, 4, '第4节', '11:05:00', '11:50:00', 1, 4, NOW(), NOW()),
(5, 5, '第5节', '14:00:00', '14:45:00', 1, 5, NOW(), NOW()),
(6, 6, '第6节', '14:55:00', '15:40:00', 1, 6, NOW(), NOW()),
(7, 7, '第7节', '16:00:00', '16:45:00', 1, 7, NOW(), NOW()),
(8, 8, '第8节', '16:55:00', '17:40:00', 1, 8, NOW(), NOW()),
(9, 9, '第9节', '19:00:00', '19:45:00', 1, 9, NOW(), NOW()),
(10, 10, '第10节', '19:55:00', '20:40:00', 1, 10, NOW(), NOW());

INSERT INTO `course_schedule`
(`id`, `academic_year`, `semester`, `course_id`, `teacher_id`, `classroom_id`,
 `weekday`, `start_section_id`, `end_section_id`, `start_week`, `end_week`, `week_type`,
 `student_count`, `remark`, `status`, `created_at`, `updated_at`)
VALUES
(1, '2025-2026', 2, 1, 2, 1, 1, 1, 2, 1, 16, 0, 58, '数据结构 周一 第1-2节 全周', 2, '2026-03-08 09:00:00', '2026-03-10 09:40:00'),
(2, '2025-2026', 2, 2, 2, 2, 3, 3, 4, 1, 16, 1, 46, '操作系统 周三 第3-4节 单周', 2, '2026-03-13 09:00:00', '2026-03-15 11:40:00'),
(3, '2025-2026', 2, 3, 3, 3, 3, 3, 4, 1, 16, 2, 42, '数据库原理 周三 第3-4节 双周', 1, '2026-03-14 09:00:00', '2026-03-16 14:10:00'),
(4, '2025-2026', 2, 1, 3, 1, 5, 5, 6, 1, 16, 0, 55, '数据结构 周五 第5-6节 全周', 0, '2026-03-14 09:30:00', '2026-03-14 09:30:00'),
(5, '2025-2026', 2, 1, 2, 1, 2, 1, 3, 1, 16, 0, 60, '数据结构 周二 第1-3节 全周（连续三节）', 1, NOW(), NOW()),
(6, '2025-2026', 2, 2, 2, 2, 4, 5, 6, 1, 16, 1, 48, '操作系统 周四 第5-6节 单周', 1, NOW(), NOW()),
(7, '2025-2026', 2, 3, 3, 3, 4, 5, 6, 1, 16, 2, 44, '数据库原理 周四 第5-6节 双周', 1, NOW(), NOW());

INSERT INTO `sys_file`
(`id`, `original_name`, `file_key`, `file_url`, `file_md5`, `upload_by`, `created_at`)
VALUES
(1, 'ds_lesson_0310.mp4', 'analysis/20260310/ds_lesson_0310.mp4', 'https://oss.example.com/analysis/20260310/ds_lesson_0310.mp4', 'md5_ds_0310', 2, '2026-03-10 07:55:00'),
(2, 'os_lesson_0315.mp4', 'analysis/20260315/os_lesson_0315.mp4', 'https://oss.example.com/analysis/20260315/os_lesson_0315.mp4', 'md5_os_0315', 2, '2026-03-15 09:55:00'),
(3, 'db_lesson_0316.jpg', 'analysis/20260316/db_lesson_0316.jpg', 'https://oss.example.com/analysis/20260316/db_lesson_0316.jpg', 'md5_db_0316', 3, '2026-03-16 13:59:00');

INSERT INTO `sys_weight_config`
(`id`, `config_name`, `is_active`, `config_content`, `description`, `created_at`, `updated_at`)
VALUES
(1, '默认课堂评分方案', 1,
 JSON_ARRAY(
   JSON_OBJECT('behaviorType', '正常听课', 'name', '正常听课', 'weight', 5.0),
   JSON_OBJECT('behaviorType', '书写', 'name', '书写', 'weight', 4.0),
   JSON_OBJECT('behaviorType', '阅读', 'name', '阅读', 'weight', 4.0),
   JSON_OBJECT('behaviorType', '举手', 'name', '举手', 'weight', 3.0),
   JSON_OBJECT('behaviorType', '玩手机', 'name', '玩手机', 'weight', -6.0),
   JSON_OBJECT('behaviorType', '趴桌', 'name', '趴桌', 'weight', -8.0)
 ),
 '系统默认启用方案', '2026-03-01 12:00:00', '2026-03-01 12:00:00'),
(2, '严格课堂纪律方案', 0,
 JSON_ARRAY(
   JSON_OBJECT('behaviorType', '正常听课', 'name', '正常听课', 'weight', 5.0),
   JSON_OBJECT('behaviorType', '书写', 'name', '书写', 'weight', 4.0),
   JSON_OBJECT('behaviorType', '阅读', 'name', '阅读', 'weight', 4.0),
   JSON_OBJECT('behaviorType', '举手', 'name', '举手', 'weight', 3.0),
   JSON_OBJECT('behaviorType', '玩手机', 'name', '玩手机', 'weight', -10.0),
   JSON_OBJECT('behaviorType', '趴桌', 'name', '趴桌', 'weight', -12.0)
 ),
 '更强调课堂纪律与异常行为处罚', '2026-03-02 12:00:00', '2026-03-02 12:00:00');

INSERT INTO `analysis_task`
(`id`, `teacher_id`, `classroom_id`, `schedule_id`, `file_id`, `media_type`, `status`,
 `attendance_count`, `total_score`, `start_time`, `finish_time`,
 `fail_reason`, `retry_count`, `created_at`, `updated_at`)
VALUES
(10001, 2, 1, 1, 1, 2, 2, 54, 88.50, '2026-03-10 08:01:00', '2026-03-10 08:06:30', NULL, 0, '2026-03-10 08:00:30', '2026-03-10 08:06:30'),
(10002, 2, 2, 2, 2, 2, 3, 0, 0.00, '2026-03-15 10:02:00', '2026-03-15 10:04:10', '模型服务请求超时', 1, '2026-03-15 10:01:10', '2026-03-15 10:04:10'),
(10003, 3, 3, 3, NULL, 3, 1, 36, 81.00, '2026-03-16 14:01:00', NULL, NULL, 0, '2026-03-16 14:00:10', '2026-03-16 14:05:10'),
(10004, 3, 3, 3, 3, 1, 0, 0, 0.00, NULL, NULL, NULL, 0, '2026-03-16 13:59:20', '2026-03-16 13:59:20');

INSERT INTO `analysis_detail`
(`task_id`, `record_type`, `frame_time`, `behavior_type`, `count`, `bounding_boxes`, `snapshot_url`, `created_at`)
VALUES
(10001, 0, 0, '正常听课', 32, JSON_ARRAY(JSON_ARRAY(80,100,180,260), JSON_ARRAY(200,110,290,270)), NULL, '2026-03-10 08:06:00'),
(10001, 0, 60, '书写', 12, JSON_ARRAY(JSON_ARRAY(90,120,170,260)), NULL, '2026-03-10 08:06:00'),
(10001, 0, 120, '阅读', 6, JSON_ARRAY(JSON_ARRAY(210,115,280,255)), NULL, '2026-03-10 08:06:00'),
(10001, 0, 180, '举手', 4, JSON_ARRAY(JSON_ARRAY(300,80,360,240)), NULL, '2026-03-10 08:06:00'),
(10003, 1, 30, '正常听课', 24, NULL, NULL, '2026-03-16 14:02:00'),
(10003, 1, 60, '正常听课', 22, NULL, NULL, '2026-03-16 14:03:00'),
(10003, 1, 90, '玩手机', 3, NULL, NULL, '2026-03-16 14:04:00'),
(10003, 2, 90, '玩手机', 3, JSON_ARRAY(JSON_ARRAY(220,140,300,300), JSON_ARRAY(320,130,390,295)), 'https://oss.example.com/snapshot/20260316/task10003_phone_90s.jpg', '2026-03-16 14:04:05');

INSERT INTO `analysis_task_log`
(`task_id`, `stage`, `status`, `message`, `detail_json`, `created_at`)
VALUES
(10001, 'CREATED', 1, '分析任务创建成功', JSON_OBJECT('scheduleId', 1, 'mediaType', 2), '2026-03-10 08:00:30'),
(10001, 'UPLOADED', 1, '文件上传并写入系统文件表', JSON_OBJECT('fileId', 1, 'fileName', 'ds_lesson_0310.mp4'), '2026-03-10 08:00:45'),
(10001, 'RUNNING', 1, '任务已开始分析', JSON_OBJECT('startTime', '2026-03-10 08:01:00'), '2026-03-10 08:01:00'),
(10001, 'MODEL_ACCEPTED', 1, '模型服务已受理文件流任务', JSON_OBJECT('endpoint', '/model/api/v1/tasks/file'), '2026-03-10 08:01:03'),
(10001, 'FINISHED', 1, '任务执行完成，结果已落库', JSON_OBJECT('attendanceCount', 54, 'totalScore', 88.50), '2026-03-10 08:06:30'),
(10002, 'CREATED', 1, '分析任务创建成功', JSON_OBJECT('scheduleId', 2, 'mediaType', 2), '2026-03-15 10:01:10'),
(10002, 'UPLOADED', 1, '文件上传并写入系统文件表', JSON_OBJECT('fileId', 2, 'fileName', 'os_lesson_0315.mp4'), '2026-03-15 10:01:20'),
(10002, 'RUNNING', 1, '任务已开始分析', JSON_OBJECT('startTime', '2026-03-15 10:02:00'), '2026-03-15 10:02:00'),
(10002, 'FAILED', 0, '模型服务请求超时', JSON_OBJECT('rawError', 'Read timed out'), '2026-03-15 10:04:10'),
(10002, 'RETRY', 1, '管理员重新发起失败任务', JSON_OBJECT('retryCount', 1), '2026-03-15 10:05:00'),
(10003, 'CREATED', 1, '实时分析任务创建成功', JSON_OBJECT('scheduleId', 3, 'mediaType', 3), '2026-03-16 14:00:10'),
(10003, 'RUNNING', 1, '实时任务已开始分析', JSON_OBJECT('startTime', '2026-03-16 14:01:00'), '2026-03-16 14:01:00'),
(10003, 'WAITING_CALLBACK', 1, '实时流持续处理中', JSON_OBJECT('progress', 60), '2026-03-16 14:05:10'),
(10004, 'CREATED', 1, '图片分析任务创建成功', JSON_OBJECT('scheduleId', 3, 'mediaType', 1), '2026-03-16 13:59:20');

INSERT INTO `sys_login_log`
(`emp_no`, `user_name`, `login_status`, `login_message`, `ip_address`, `user_agent`, `created_at`)
VALUES
('admin', '系统管理员', 1, '登录成功', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/134.0', '2026-03-16 08:30:12'),
('T2024001', '张教授', 1, '登录成功', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/134.0', '2026-03-16 08:35:18'),
('T2024002', '李老师', 0, '密码错误', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/134.0', '2026-03-16 08:36:05'),
('T2024002', '李老师', 1, '登录成功', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/134.0', '2026-03-16 08:37:10');

INSERT INTO `sys_operation_log`
(`emp_no`, `user_name`, `module_name`, `operation_type`, `business_id`, `operation_desc`, `operation_status`, `request_method`, `request_url`, `created_at`)
VALUES
('admin', '系统管理员', '分析任务中心', 'RETRY_TASK', 10002, '管理员重试失败任务', 1, 'POST', '/api/v1/task-center/10002/retry', '2026-03-15 10:05:00'),
('T2024001', '张教授', '课堂分析', 'CREATE_ANALYSIS_TASK', 10001, '教师创建文件流分析任务', 1, 'POST', '/api/v1/analysis/task', '2026-03-10 08:00:30'),
('T2024001', '张教授', '课堂分析报告', 'EXPORT_REPORT', 10001, '导出课堂分析报表', 1, 'GET', '/api/v1/report/export', '2026-03-10 08:10:00'),
('admin', '系统管理员', '排课管理', 'UPDATE_SCHEDULE', 2, '修改排课规则', 1, 'PUT', '/api/v1/admin/schedule', '2026-03-14 09:20:00'),
('admin', '系统管理员', '系统参数', 'ACTIVATE_CONFIG', 1, '激活默认课堂评分方案', 1, 'PUT', '/api/v1/admin/config/active/1', '2026-03-01 12:05:00'),
('admin', '系统管理员', '教师管理', 'CREATE_TEACHER', 3, '新增教师账号：李老师', 1, 'POST', '/api/v1/admin/teacher', '2026-03-01 09:20:00');

SET FOREIGN_KEY_CHECKS = 1;