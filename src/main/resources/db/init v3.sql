-- ==========================================================
-- 课堂学生行为分析系统 - 数据库初始化脚本 (V3.1 架构优化版)
-- 包含完整表结构设计与逻辑模拟数据
-- ==========================================================

-- 1. 创建并切换数据库
CREATE DATABASE IF NOT EXISTS `smart_classroom_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `smart_classroom_db`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==========================================================
-- 2. 清理旧表
-- ==========================================================
DROP TABLE IF EXISTS `analysis_detail`;
DROP TABLE IF EXISTS `analysis_task`;
DROP TABLE IF EXISTS `sys_weight_config`;
DROP TABLE IF EXISTS `course_schedule`;
DROP TABLE IF EXISTS `sys_file`;
DROP TABLE IF EXISTS `classroom`;
DROP TABLE IF EXISTS `course`;
DROP TABLE IF EXISTS `teacher`;

-- ==========================================================
-- 3. 创建表结构
-- ==========================================================

-- 教师表
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

-- 课程表
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

-- 教室表
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

-- 课程安排表 (排课表)
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
  KEY `idx_teacher_id` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排课安排表';

-- 系统统一文件管理表
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

-- 系统行为权重配置方案表
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
-- 核心大改：分析任务主表 (V3.1 优化版)
-- ==========================================================
CREATE TABLE `analysis_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `teacher_id` bigint(20) NOT NULL COMMENT '发起分析的教师ID',
  `classroom_id` bigint(20) NOT NULL COMMENT '关联教室ID',
  `schedule_id` bigint(20) NOT NULL COMMENT '关联的具体某节课',
  `file_id` bigint(20) DEFAULT NULL COMMENT '关联 sys_file的ID (实时流 media_type=3 时可为 NULL)',
  `media_type` tinyint(4) NOT NULL COMMENT '1-图片, 2-视频, 3-实时流',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0-排队中, 1-分析中, 2-成功, 3-失败',
  `attendance_count` int(11) DEFAULT '0' COMMENT 'AI识别实到人数',
  `total_score` decimal(5,2) DEFAULT '0.00' COMMENT '综合得分',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI分析任务主表';

-- ==========================================================
-- 核心大改：分析任务明细表 (V3.1 优化版)
-- ==========================================================
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
  KEY `idx_task_type` (`task_id`, `record_type`) COMMENT '联合索引：加速前端画折线图和照片墙'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI分析任务识别明细表';


-- ==========================================================
-- 4. 插入逻辑严密的模拟数据
-- ==========================================================

-- 写入基础字典数据
INSERT INTO `teacher` (`id`, `emp_no`, `password`, `name`, `department`, `role`) VALUES 
(1, 'T2024001', 'e10adc3949ba59abbe56e057f20f883e', '张教授', '计算机学院', 0),
(2, 'ADMIN001', 'e10adc3949ba59abbe56e057f20f883e', '系统管理员', '信息中心', 1);

INSERT INTO `course` (`id`, `course_no`, `name`, `department`, `hours`) VALUES 
(1, 'CS101', '高等数学', '数学学院', 48),
(2, 'CS102', '数据结构与算法', '计算机学院', 64);

INSERT INTO `classroom` (`id`, `room_no`, `name`, `capacity`) VALUES 
(1, 'A-101', '第一教学楼A101', 50),
(2, 'B-205', '第二教学楼B205 (智慧教室)', 60);

-- 写入排课数据 (为后面的任务做准备)
INSERT INTO `course_schedule` (`id`, `course_id`, `teacher_id`, `classroom_id`, `student_count`, `start_time`, `end_time`) VALUES 
(1, 1, 1, 1, 45, '2024-05-20 08:00:00', '2024-05-20 09:35:00'),
(2, 2, 1, 2, 55, '2024-05-21 10:00:00', '2024-05-21 11:35:00');

-- 写入评分权重配置
INSERT INTO `sys_weight_config` (`id`, `config_name`, `is_active`, `config_content`) VALUES 
(1, '默认评分标准', 1, '{"玩手机": -5, "趴桌": -5, "举手互动": 2, "阅读课本": 1, "正常听课": 0}');

-- 写入系统文件 (模拟上传的视频和抓拍图片)
INSERT INTO `sys_file` (`id`, `original_name`, `file_key`, `file_url`, `upload_by`) VALUES 
(1, '高数课堂录像.mp4', 'videos/2024/05/v_001.mp4', 'https://your-oss.aliyuncs.com/v_001.mp4', 1),
(2, '违规抓拍_10分20秒.jpg', 'images/snap/s_001.jpg', 'https://your-oss.aliyuncs.com/s_001.jpg', 1);


-- ----------------------------------------------------------
-- 重点演示：生成分析任务 (1个视频流任务, 1个实时流任务)
-- ----------------------------------------------------------

-- 任务1：文件流分析 (上传了视频，所以有 file_id=1, media_type=2)
INSERT INTO `analysis_task` (`id`, `teacher_id`, `classroom_id`, `schedule_id`, `file_id`, `media_type`, `status`, `attendance_count`, `total_score`) VALUES 
(1, 1, 1, 1, 1, 2, 2, 43, 85.50);

-- 任务2：实时流分析 (只是开了摄像头没保存视频，所以 file_id 为 NULL, media_type=3)
INSERT INTO `analysis_task` (`id`, `teacher_id`, `classroom_id`, `schedule_id`, `file_id`, `media_type`, `status`, `attendance_count`, `total_score`) VALUES 
(2, 1, 2, 2, NULL, 3, 2, 53, 78.00);


-- ----------------------------------------------------------
-- 重点演示：生成明细数据 (对应上面的不同业务场景)
-- ----------------------------------------------------------

-- 👉 场景 1：对应任务 1 (视频文件流)。因为是处理录像，所以都是全量明细 (record_type=0)，带详细坐标，不带抓拍图。
INSERT INTO `analysis_detail` (`task_id`, `record_type`, `frame_time`, `behavior_type`, `count`, `bounding_boxes`) VALUES 
(1, 0, 1, '正常听课', 40, '[[10,20,50,60], [100,100,150,150]]'), -- 假装有40个框，JSON略
(1, 0, 1, '玩手机', 2, '[[200,300,250,350], [400,100,450,150]]'),
(1, 0, 1, '趴桌', 1, '[[500,400,550,450]]'),
(1, 0, 2, '正常听课', 43, '[[...]]'); -- 第2秒大家都认真听讲了

-- 👉 场景 2：对应任务 2 (实时流)。产生两种数据：
-- 2.1 趋势聚合数据 (record_type=1)，每30秒写入一次。只存数字，不存框(NULL)，不存图(NULL)！用于画折线图。
INSERT INTO `analysis_detail` (`task_id`, `record_type`, `frame_time`, `behavior_type`, `count`, `bounding_boxes`, `snapshot_url`) VALUES 
(2, 1, 30, '正常听课', 50, NULL, NULL),
(2, 1, 30, '玩手机', 2, NULL, NULL),
(2, 1, 30, '趴桌', 1, NULL, NULL),
(2, 1, 60, '正常听课', 45, NULL, NULL),
(2, 1, 60, '玩手机', 5, NULL, NULL),
(2, 1, 60, '趴桌', 3, NULL, NULL);

-- 2.2 违规抓拍数据 (record_type=2)。在第60秒时由于5人玩手机触发了报警，因此存下图片和所有人的坐标！用于画抓拍照片墙。
INSERT INTO `analysis_detail` (`task_id`, `record_type`, `frame_time`, `behavior_type`, `count`, `bounding_boxes`, `snapshot_url`) VALUES 
(2, 2, 60, '玩手机', 5, '[[100,200,150,250], [200,300,250,350], [300,100,350,150], [400,200,450,250], [500,300,550,350]]', 'https://your-oss.aliyuncs.com/s_001.jpg');

SET FOREIGN_KEY_CHECKS = 1;