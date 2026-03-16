-- ==========================================================
-- 课堂学生行为分析系统 - 数据库初始化脚本 (V5.0 日志审计模块增强版)
-- 说明：
-- 1. 本脚本会删除当前数据库中的业务表并重建；
-- 2. 在 V4.0 分析任务中心增强版基础上继续演进；
-- 3. 新增 sys_login_log / sys_operation_log 两张日志表；
-- 4. 复用 analysis_task_log 作为任务执行日志表，不重复建表；
-- 5. 附带最小可运行的逻辑模拟数据，便于前后端联调与页面演示。
-- ==========================================================

CREATE DATABASE IF NOT EXISTS `smart_classroom_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `smart_classroom_db`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==========================================================
-- 1. 清理旧表（注意顺序：先删依赖表，再删主表）
-- ==========================================================
DROP TABLE IF EXISTS `sys_operation_log`;
DROP TABLE IF EXISTS `sys_login_log`;
DROP TABLE IF EXISTS `analysis_task_log`;
DROP TABLE IF EXISTS `analysis_detail`;
DROP TABLE IF EXISTS `analysis_task`;
DROP TABLE IF EXISTS `sys_weight_config`;
DROP TABLE IF EXISTS `course_schedule`;
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

CREATE TABLE `course_schedule` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `course_id` bigint(20) NOT NULL COMMENT '关联课程ID',
  `teacher_id` bigint(20) NOT NULL COMMENT '关联教师ID',
  `classroom_id` bigint(20) NOT NULL COMMENT '关联教室ID',
  `student_count` int(11) NOT NULL DEFAULT '0' COMMENT '应到学生人数',
  `start_time` datetime NOT NULL COMMENT '上课开始时间',
  `end_time` datetime NOT NULL COMMENT '下课结束时间',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '状态：0-未开始, 1-进行中, 2-已结束',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_teacher_id` (`teacher_id`),
  KEY `idx_classroom_id` (`classroom_id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_schedule_time` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排课安排表';

CREATE TABLE `sys_file` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `original_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_key` varchar(255) NOT NULL COMMENT 'OSS的Object Key',
  `file_url` varchar(500) NOT NULL COMMENT '文件的公网/内网访问链接',
  `file_md5` varchar(64) DEFAULT NULL COMMENT '文件MD5值',
  `upload_by` bigint(20) NOT NULL COMMENT '上传者的教师ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teacher_md5` (`upload_by`, `file_md5`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统统一文件管理表';

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
-- 3. 核心业务表（分析任务中心增强）
-- ==========================================================

CREATE TABLE `analysis_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `teacher_id` bigint(20) NOT NULL COMMENT '发起分析的教师ID',
  `classroom_id` bigint(20) NOT NULL COMMENT '关联教室ID',
  `schedule_id` bigint(20) NOT NULL COMMENT '关联的具体某节课',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI分析任务主表（分析任务中心增强版）';

CREATE TABLE `analysis_detail` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `task_id` bigint(20) NOT NULL COMMENT '关联 analysis_task 的 ID',
  `record_type` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0-全量明细(文件流), 1-趋势聚合(实时流走势), 2-违规抓拍(实时流铁证)',
  `frame_time` int(11) DEFAULT '0' COMMENT '视频帧秒数或实时流距离开课的秒数',
  `behavior_type` varchar(50) NOT NULL COMMENT '行为类别(如：玩手机, 趴桌, 正常听课)',
  `count` int(11) NOT NULL DEFAULT '0' COMMENT '出现人数',
  `bounding_boxes` json DEFAULT NULL COMMENT 'YOLO坐标数组 (record_type=1时为NULL)',
  `snapshot_url` varchar(500) DEFAULT NULL COMMENT '关键帧抓拍图片OSS链接 (仅 record_type=2 时有值)',
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
-- 4. 新增：日志审计模块表
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
-- 5. 模拟基础数据
-- ==========================================================

INSERT INTO `teacher` (`id`, `emp_no`, `password`, `name`, `phone`, `department`, `role`, `status`, `created_at`, `updated_at`) VALUES
(1, 'admin', 'e10adc3949ba59abbe56e057f20f883e', '系统管理员', '13800000000', '信息中心', 1, 1, '2026-03-01 09:00:00', '2026-03-01 09:00:00'),
(2, 'T2024001', 'e10adc3949ba59abbe56e057f20f883e', '张教授', '13800000001', '计算机学院', 0, 1, '2026-03-01 09:10:00', '2026-03-01 09:10:00'),
(3, 'T2024002', 'e10adc3949ba59abbe56e057f20f883e', '李老师', '13800000002', '计算机学院', 0, 1, '2026-03-01 09:20:00', '2026-03-01 09:20:00');

INSERT INTO `course` (`id`, `course_no`, `name`, `department`, `hours`, `status`, `created_at`, `updated_at`) VALUES
(1, 'CS101', '数据结构与算法', '计算机学院', 48, 1, '2026-03-01 10:00:00', '2026-03-01 10:00:00'),
(2, 'CS102', '操作系统', '计算机学院', 48, 1, '2026-03-01 10:10:00', '2026-03-01 10:10:00'),
(3, 'CS103', '数据库原理', '计算机学院', 32, 1, '2026-03-01 10:20:00', '2026-03-01 10:20:00');

INSERT INTO `classroom` (`id`, `room_no`, `name`, `location`, `capacity`, `status`, `created_at`, `updated_at`) VALUES
(1, 'A101', '多媒体阶梯教室A101', '一教1楼', 80, 1, '2026-03-01 11:00:00', '2026-03-01 11:00:00'),
(2, 'B203', '智慧教室B203', '二教2楼', 60, 1, '2026-03-01 11:10:00', '2026-03-01 11:10:00'),
(3, 'C305', '机房C305', '实验楼3楼', 50, 1, '2026-03-01 11:20:00', '2026-03-01 11:20:00');

INSERT INTO `course_schedule` (`id`, `course_id`, `teacher_id`, `classroom_id`, `student_count`, `start_time`, `end_time`, `status`, `created_at`, `updated_at`) VALUES
(1, 1, 2, 1, 58, '2026-03-10 08:00:00', '2026-03-10 09:40:00', 2, '2026-03-08 09:00:00', '2026-03-10 09:40:00'),
(2, 2, 2, 2, 46, '2026-03-15 10:00:00', '2026-03-15 11:40:00', 2, '2026-03-13 09:00:00', '2026-03-15 11:40:00'),
(3, 3, 3, 3, 42, '2026-03-16 14:00:00', '2026-03-16 15:40:00', 1, '2026-03-14 09:00:00', '2026-03-16 14:10:00'),
(4, 1, 3, 1, 55, '2026-03-18 08:00:00', '2026-03-18 09:40:00', 0, '2026-03-14 09:30:00', '2026-03-14 09:30:00');

INSERT INTO `sys_file` (`id`, `original_name`, `file_key`, `file_url`, `file_md5`, `upload_by`, `created_at`) VALUES
(1, 'ds_lesson_0310.mp4', 'analysis/20260310/ds_lesson_0310.mp4', 'https://oss.example.com/analysis/20260310/ds_lesson_0310.mp4', 'md5_ds_0310', 2, '2026-03-10 07:55:00'),
(2, 'os_lesson_0315.mp4', 'analysis/20260315/os_lesson_0315.mp4', 'https://oss.example.com/analysis/20260315/os_lesson_0315.mp4', 'md5_os_0315', 2, '2026-03-15 09:55:00'),
(3, 'db_lesson_0316.jpg', 'analysis/20260316/db_lesson_0316.jpg', 'https://oss.example.com/analysis/20260316/db_lesson_0316.jpg', 'md5_db_0316', 3, '2026-03-16 13:59:00');

INSERT INTO `sys_weight_config` (`id`, `config_name`, `is_active`, `config_content`, `description`, `created_at`, `updated_at`) VALUES
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

-- ==========================================================
-- 6. 模拟分析任务数据
-- ==========================================================

INSERT INTO `analysis_task` (`id`, `teacher_id`, `classroom_id`, `schedule_id`, `file_id`, `media_type`, `status`, `attendance_count`, `total_score`, `start_time`, `finish_time`, `fail_reason`, `retry_count`, `created_at`, `updated_at`) VALUES
(10001, 2, 1, 1, 1, 2, 2, 54, 88.50, '2026-03-10 08:01:00', '2026-03-10 08:06:30', NULL, 0, '2026-03-10 08:00:30', '2026-03-10 08:06:30'),
(10002, 2, 2, 2, 2, 2, 3, 0, 0.00, '2026-03-15 10:02:00', '2026-03-15 10:04:10', '模型服务请求超时', 1, '2026-03-15 10:01:10', '2026-03-15 10:04:10'),
(10003, 3, 3, 3, NULL, 3, 1, 36, 81.00, '2026-03-16 14:01:00', NULL, NULL, 0, '2026-03-16 14:00:10', '2026-03-16 14:05:10'),
(10004, 3, 3, 3, 3, 1, 0, 0, 0.00, NULL, NULL, NULL, 0, '2026-03-16 13:59:20', '2026-03-16 13:59:20');

INSERT INTO `analysis_detail` (`task_id`, `record_type`, `frame_time`, `behavior_type`, `count`, `bounding_boxes`, `snapshot_url`, `created_at`) VALUES
(10001, 0, 0, '正常听课', 32, JSON_ARRAY(JSON_ARRAY(80,100,180,260), JSON_ARRAY(200,110,290,270)), NULL, '2026-03-10 08:06:00'),
(10001, 0, 60, '书写', 12, JSON_ARRAY(JSON_ARRAY(90,120,170,260)), NULL, '2026-03-10 08:06:00'),
(10001, 0, 120, '阅读', 6, JSON_ARRAY(JSON_ARRAY(210,115,280,255)), NULL, '2026-03-10 08:06:00'),
(10001, 0, 180, '举手', 4, JSON_ARRAY(JSON_ARRAY(300,80,360,240)), NULL, '2026-03-10 08:06:00'),
(10003, 1, 30, '正常听课', 24, NULL, NULL, '2026-03-16 14:02:00'),
(10003, 1, 60, '正常听课', 22, NULL, NULL, '2026-03-16 14:03:00'),
(10003, 1, 90, '玩手机', 3, NULL, NULL, '2026-03-16 14:04:00'),
(10003, 2, 90, '玩手机', 3, JSON_ARRAY(JSON_ARRAY(220,140,300,300), JSON_ARRAY(320,130,390,295)), 'https://oss.example.com/snapshot/20260316/task10003_phone_90s.jpg', '2026-03-16 14:04:05');

INSERT INTO `analysis_task_log` (`task_id`, `stage`, `status`, `message`, `detail_json`, `created_at`) VALUES
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

-- ==========================================================
-- 7. 模拟日志审计数据
-- ==========================================================

INSERT INTO `sys_login_log` (`emp_no`, `user_name`, `login_status`, `login_message`, `ip_address`, `user_agent`, `created_at`) VALUES
('admin', '系统管理员', 1, '登录成功', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/134.0', '2026-03-16 08:30:12'),
('T2024001', '张教授', 1, '登录成功', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/134.0', '2026-03-16 08:35:18'),
('T2024002', '李老师', 0, '密码错误', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/134.0', '2026-03-16 08:36:05'),
('T2024002', '李老师', 1, '登录成功', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/134.0', '2026-03-16 08:37:10');

INSERT INTO `sys_operation_log` (`emp_no`, `user_name`, `module_name`, `operation_type`, `business_id`, `operation_desc`, `operation_status`, `request_method`, `request_url`, `created_at`) VALUES
('admin', '系统管理员', '分析任务中心', 'RETRY_TASK', 10002, '管理员重试失败任务', 1, 'POST', '/api/v1/task-center/10002/retry', '2026-03-15 10:05:00'),
('T2024001', '张教授', '课堂分析', 'CREATE_ANALYSIS_TASK', 10001, '教师创建文件流分析任务', 1, 'POST', '/api/v1/analysis/task', '2026-03-10 08:00:30'),
('T2024001', '张教授', '课堂分析报告', 'EXPORT_REPORT', 10001, '导出课堂分析报表', 1, 'GET', '/api/v1/report/export', '2026-03-10 08:10:00'),
('admin', '系统管理员', '排课管理', 'UPDATE_SCHEDULE', 2, '修改排课时间与教室安排', 1, 'PUT', '/api/v1/admin/schedule', '2026-03-14 09:20:00'),
('admin', '系统管理员', '系统参数', 'ACTIVATE_CONFIG', 1, '激活默认课堂评分方案', 1, 'PUT', '/api/v1/admin/config/active/1', '2026-03-01 12:05:00'),
('admin', '系统管理员', '教师管理', 'CREATE_TEACHER', 3, '新增教师账号：李老师', 1, 'POST', '/api/v1/admin/teacher', '2026-03-01 09:20:00');

SET FOREIGN_KEY_CHECKS = 1;
