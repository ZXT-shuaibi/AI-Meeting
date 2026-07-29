-- Harness 第一阶段运行审计表。
-- 本脚本只新增“运行总账 + 阶段事件”两张旁路审计表；不会修改简历、面试、RAG 实验或 Redis/Mongo 快照等既有事实源。
-- 适用于 MySQL 5.7+，仅使用 CREATE TABLE IF NOT EXISTS，重复执行不会删除已有审计记录。

CREATE TABLE IF NOT EXISTS `career_agent_run` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `run_id` varchar(80) NOT NULL COMMENT 'Harness 运行唯一编号',
  `trace_id` varchar(128) DEFAULT NULL COMMENT '关联既有 AI Trace 编号',
  `user_id` bigint DEFAULT NULL COMMENT '任务归属用户编号',
  `business_type` varchar(64) NOT NULL COMMENT '业务类型，例如 RAG_EXPERIMENT、INTERVIEW_REPORT',
  `business_id` varchar(128) DEFAULT NULL COMMENT '业务对象编号，例如 experimentId、resumeId',
  `scene_code` varchar(64) NOT NULL COMMENT 'Agent 运行场景',
  `session_id` varchar(128) DEFAULT NULL COMMENT '会话或任务编号',
  `status` varchar(24) NOT NULL COMMENT 'RUNNING、SUCCEEDED、FAILED、CANCELLED、TIMED_OUT',
  `rag_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '本次任务是否启用 RAG',
  `input_summary` varchar(512) DEFAULT NULL COMMENT '脱敏输入摘要，不保存 JD、简历、回答原文',
  `config_fingerprint` varchar(256) DEFAULT NULL COMMENT '运行配置指纹或安全配置摘要',
  `result_summary` varchar(512) DEFAULT NULL COMMENT '脱敏结果摘要',
  `error_message` varchar(512) DEFAULT NULL COMMENT '脱敏错误摘要',
  `metadata_json` text COMMENT '仅保存安全运行元数据',
  `started_at` datetime DEFAULT NULL COMMENT '业务开始时间',
  `finished_at` datetime DEFAULT NULL COMMENT '业务结束时间',
  `duration_ms` bigint DEFAULT NULL COMMENT '总耗时毫秒，可由开始和结束时间推导',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` tinyint DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_career_agent_run_id` (`run_id`),
  KEY `idx_career_agent_run_trace` (`trace_id`),
  KEY `idx_career_agent_run_user_time` (`user_id`, `create_time`),
  KEY `idx_career_agent_run_scene_status_time` (`scene_code`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Harness Agent 业务运行总账';

CREATE TABLE IF NOT EXISTS `career_agent_run_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `run_id` varchar(80) NOT NULL COMMENT '所属 Harness 运行编号',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型，例如 RUN_CREATED、RUN_FINISHED、RAG_RETRIEVE',
  `stage_code` varchar(64) NOT NULL COMMENT '阶段编码',
  `status` varchar(24) NOT NULL COMMENT '事件发生时的运行状态',
  `message` varchar(512) DEFAULT NULL COMMENT '面向管理端的脱敏阶段摘要',
  `metadata_json` text COMMENT '只保存结果数量、耗时、降级等安全指标',
  `occurred_at` datetime NOT NULL COMMENT '事件发生时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` tinyint DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_career_agent_run_event_time` (`run_id`, `occurred_at`),
  KEY `idx_career_agent_run_event_type_time` (`event_type`, `occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Harness Agent 运行阶段事件';
