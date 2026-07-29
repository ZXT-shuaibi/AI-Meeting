-- Harness 第二期：RAG 评测基线。
-- 仅冻结已完成实验在当时的配置和指标快照，不修改或复制简历、JD、Prompt、标签和检索证据原文。

CREATE TABLE IF NOT EXISTS `career_agent_evaluation_baseline` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `owner_user_id` bigint NOT NULL COMMENT '源 RAG 实验所属用户',
  `name` varchar(100) NOT NULL COMMENT '基线名称',
  `scene_code` varchar(64) NOT NULL COMMENT '当前固定为 RAG_EXPERIMENT',
  `source_experiment_id` bigint NOT NULL COMMENT '源实验编号',
  `config_fingerprint` varchar(256) DEFAULT NULL COMMENT '源实验配置指纹',
  `runtime_config_json` mediumtext COMMENT '冻结运行配置快照',
  `metric_snapshot_json` mediumtext COMMENT '冻结量化指标快照',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_agent_eval_baseline_owner_time` (`owner_user_id`, `create_time`),
  KEY `idx_agent_eval_baseline_experiment` (`source_experiment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Harness RAG 评测基线快照';
