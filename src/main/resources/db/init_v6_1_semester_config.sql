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

-- 初始数据：2025-2026学年第二学期（假设2026年2月23日开学，第一周周一）
INSERT INTO `sys_semester_config`
(`academic_year`, `semester`, `term_start_date`, `status`)
VALUES
('2025-2026', 2, '2026-02-23', 1);
