-- Harness 第四期：协作式检查点。
-- 业务恢复仍读取原始实验、任务与会话事实源；本表只记录可审计的阶段检查点。

CREATE TABLE IF NOT EXISTS `career_agent_run_checkpoint` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` varchar(80) NOT NULL,
  `sequence_no` int NOT NULL,
  `checkpoint_code` varchar(64) NOT NULL,
  `metadata_json` text,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_agent_checkpoint_run_sequence` (`run_id`, `sequence_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Harness Agent 运行检查点';
