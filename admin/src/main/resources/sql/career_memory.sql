CREATE TABLE IF NOT EXISTS `career_memory_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `memory_id` varchar(191) NOT NULL COMMENT 'Memory/session id',
  `message_index` int DEFAULT 0 COMMENT 'Message order index',
  `role` varchar(32) NOT NULL COMMENT 'SYSTEM/USER/ASSISTANT/TOOL',
  `content` longtext COMMENT 'Message content',
  `message_time` datetime(6) DEFAULT NULL COMMENT 'Message timestamp',
  `metadata_json` text COMMENT 'Message metadata JSON',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_memory_index` (`memory_id`, `message_index`),
  KEY `idx_memory_time` (`memory_id`, `message_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Career hybrid memory messages cold store';

CREATE TABLE IF NOT EXISTS `career_memory_decision` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `memory_id` varchar(191) NOT NULL COMMENT 'Memory/session id',
  `message_index` int DEFAULT 0 COMMENT 'Related message index',
  `summary` text COMMENT 'Decision summary',
  `decision_time` datetime(6) DEFAULT NULL COMMENT 'Decision timestamp',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_memory_index` (`memory_id`, `message_index`),
  KEY `idx_memory_time` (`memory_id`, `decision_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Career decision index cold store';