CREATE TABLE IF NOT EXISTS `career_resume` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint DEFAULT NULL COMMENT 'User id',
  `cv_type` varchar(32) DEFAULT 'upload' COMMENT 'upload/excellent',
  `name` varchar(128) DEFAULT NULL COMMENT 'Candidate name',
  `title` varchar(128) DEFAULT NULL COMMENT 'Target title',
  `summary` text COMMENT 'Resume summary',
  `cv_json` longtext COMMENT 'Structured CvBO JSON',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_cv_type` (`cv_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Career resume structured aggregate';

CREATE TABLE IF NOT EXISTS `career_resume_chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `resume_id` bigint NOT NULL COMMENT 'Resume id',
  `vector_id` varchar(64) DEFAULT NULL COMMENT 'External vector id',
  `chunk_type` varchar(32) NOT NULL COMMENT 'overview/summary/skills/experience/project/education',
  `chunk_index` int DEFAULT 0 COMMENT 'Chunk index',
  `content` text NOT NULL COMMENT 'Chunk content',
  `vector_json` mediumtext COMMENT 'Fallback embedding vector JSON',
  `metadata_json` text COMMENT 'Metadata JSON',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_resume_type` (`resume_id`, `chunk_type`),
  KEY `idx_vector_id` (`vector_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Career resume RAG chunks';

CREATE TABLE IF NOT EXISTS `career_job_match_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `task_id` varchar(64) NOT NULL COMMENT 'Task id',
  `user_id` bigint DEFAULT NULL COMMENT 'User id',
  `status` varchar(32) NOT NULL COMMENT 'STARTED/COMPLETED/FAILED',
  `job_description` text COMMENT 'JD text',
  `limit_count` int DEFAULT 3 COMMENT 'Requested limit',
  `matched_templates_json` longtext COMMENT 'Matched resume template snippets',
  `error_message` text COMMENT 'Failure reason',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`),
  KEY `idx_user_task` (`user_id`, `task_id`),
  KEY `idx_status_update` (`status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Career JD-resume match task';
