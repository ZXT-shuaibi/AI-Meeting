-- Harness 第三期：完成通知 Outbox。
-- 仅保存脱敏任务摘要、投递状态和错误摘要；不保存简历、JD、题目、回答、提示词或外部响应正文。

CREATE TABLE IF NOT EXISTS `career_agent_notification_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `delivery_key` varchar(160) NOT NULL COMMENT '幂等投递键，例如 completion:agent-run-id',
  `run_id` varchar(80) NOT NULL COMMENT '关联 Agent 运行编号',
  `user_id` bigint NOT NULL COMMENT '通知所属用户',
  `channel` varchar(32) NOT NULL COMMENT '当前为 MCP_NOTIFICATION',
  `status` varchar(24) NOT NULL COMMENT 'PENDING、SENT、SKIPPED、FAILED',
  `title` varchar(256) DEFAULT NULL COMMENT '脱敏标题',
  `payload_json` text COMMENT '最小化工具参数',
  `attempt_count` int NOT NULL DEFAULT 0,
  `error_summary` varchar(512) DEFAULT NULL,
  `delivered_at` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_notification_delivery` (`delivery_key`),
  KEY `idx_agent_notification_status_time` (`status`, `create_time`),
  KEY `idx_agent_notification_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Harness MCP 通知 Outbox';
