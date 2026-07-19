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


CREATE TABLE IF NOT EXISTS `career_resume_contact` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `resume_id` bigint NOT NULL COMMENT 'Resume id',
  `phone` varchar(64) DEFAULT NULL COMMENT 'Phone',
  `email` varchar(128) DEFAULT NULL COMMENT 'Email',
  `location` varchar(128) DEFAULT NULL COMMENT 'Location',
  `website` varchar(256) DEFAULT NULL COMMENT 'Website',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resume_contact` (`resume_id`),
  KEY `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Career resume contact detail';

CREATE TABLE IF NOT EXISTS `career_resume_education` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `resume_id` bigint NOT NULL COMMENT 'Resume id',
  `item_index` int DEFAULT 0 COMMENT 'Order index',
  `school` varchar(128) DEFAULT NULL COMMENT 'School',
  `major` varchar(128) DEFAULT NULL COMMENT 'Major',
  `degree` varchar(64) DEFAULT NULL COMMENT 'Degree',
  `start_date` date DEFAULT NULL COMMENT 'Start date',
  `end_date` date DEFAULT NULL COMMENT 'End date',
  `description` text COMMENT 'Description',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_resume_index` (`resume_id`, `item_index`),
  KEY `idx_school` (`school`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Career resume education detail';

CREATE TABLE IF NOT EXISTS `career_resume_experience` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `resume_id` bigint NOT NULL COMMENT 'Resume id',
  `item_index` int DEFAULT 0 COMMENT 'Order index',
  `company` varchar(128) DEFAULT NULL COMMENT 'Company',
  `industry` varchar(128) DEFAULT NULL COMMENT 'Industry',
  `role` varchar(128) DEFAULT NULL COMMENT 'Role',
  `start_date` date DEFAULT NULL COMMENT 'Start date',
  `end_date` date DEFAULT NULL COMMENT 'End date',
  `description` text COMMENT 'Description',
  `highlights_json` text COMMENT 'HighlightBO JSON array',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_resume_index` (`resume_id`, `item_index`),
  KEY `idx_company_role` (`company`, `role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Career resume experience detail';

CREATE TABLE IF NOT EXISTS `career_resume_project` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `resume_id` bigint NOT NULL COMMENT 'Resume id',
  `item_index` int DEFAULT 0 COMMENT 'Order index',
  `name` varchar(128) DEFAULT NULL COMMENT 'Project name',
  `role` varchar(128) DEFAULT NULL COMMENT 'Role',
  `start_date` date DEFAULT NULL COMMENT 'Start date',
  `end_date` date DEFAULT NULL COMMENT 'End date',
  `description` text COMMENT 'Description',
  `highlights_json` text COMMENT 'HighlightBO JSON array',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_resume_index` (`resume_id`, `item_index`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Career resume project detail';

CREATE TABLE IF NOT EXISTS `career_resume_skill` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `resume_id` bigint NOT NULL COMMENT 'Resume id',
  `item_index` int DEFAULT 0 COMMENT 'Order index',
  `category` varchar(64) DEFAULT NULL COMMENT 'Skill category',
  `name` varchar(128) DEFAULT NULL COMMENT 'Skill name',
  `level` varchar(64) DEFAULT NULL COMMENT 'Skill level',
  `highlights_json` text COMMENT 'HighlightBO JSON array',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_resume_index` (`resume_id`, `item_index`),
  KEY `idx_skill_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Career resume skill detail';

CREATE TABLE IF NOT EXISTS `career_resume_social_link` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `resume_id` bigint NOT NULL COMMENT 'Resume id',
  `item_index` int DEFAULT 0 COMMENT 'Order index',
  `name` varchar(64) DEFAULT NULL COMMENT 'Link label',
  `url` varchar(512) DEFAULT NULL COMMENT 'Link URL',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_resume_index` (`resume_id`, `item_index`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Career resume social links';

CREATE TABLE IF NOT EXISTS `career_resume_certificate` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `resume_id` bigint NOT NULL COMMENT 'Resume id',
  `item_index` int DEFAULT 0 COMMENT 'Order index',
  `name` varchar(128) DEFAULT NULL COMMENT 'Certificate name',
  `issuer` varchar(128) DEFAULT NULL COMMENT 'Issuer',
  `issue_date` date DEFAULT NULL COMMENT 'Issue date',
  `description` text COMMENT 'Description',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_resume_index` (`resume_id`, `item_index`),
  KEY `idx_certificate_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Career resume certificate detail';

CREATE TABLE IF NOT EXISTS `career_resume_format_meta` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `resume_id` bigint NOT NULL COMMENT 'Resume id',
  `alignment` varchar(32) DEFAULT NULL COMMENT 'Text alignment',
  `line_spacing` decimal(4,2) DEFAULT NULL COMMENT 'Line spacing',
  `font_family` varchar(128) DEFAULT NULL COMMENT 'Font family',
  `date_pattern` varchar(64) DEFAULT NULL COMMENT 'Date pattern',
  `hyperlink_style` varchar(64) DEFAULT NULL COMMENT 'Hyperlink style',
  `show_avatar` tinyint(1) DEFAULT NULL COMMENT 'Show avatar',
  `show_social` tinyint(1) DEFAULT NULL COMMENT 'Show social links',
  `two_column_layout` tinyint(1) DEFAULT NULL COMMENT 'Two-column layout',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resume_format_meta` (`resume_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Career resume format metadata';

CREATE TABLE IF NOT EXISTS `career_resume_locale_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `resume_id` bigint NOT NULL COMMENT 'Resume id',
  `locale` varchar(32) DEFAULT NULL COMMENT 'Locale',
  `date_pattern` varchar(64) DEFAULT NULL COMMENT 'Locale date pattern',
  `section_labels` text COMMENT 'Localized section labels',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resume_locale_config` (`resume_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Career resume locale config';

CREATE TABLE IF NOT EXISTS `career_resume_highlight` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `resume_id` bigint NOT NULL COMMENT 'Resume id',
  `owner_type` varchar(32) NOT NULL COMMENT 'experience/project/skill',
  `owner_index` int DEFAULT 0 COMMENT 'Parent item order index',
  `item_index` int DEFAULT 0 COMMENT 'Highlight order index',
  `type` varchar(64) DEFAULT NULL COMMENT 'Highlight type',
  `related_id` varchar(128) DEFAULT NULL COMMENT 'Related source id',
  `highlight` text COMMENT 'Highlight text',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_resume_owner` (`resume_id`, `owner_type`, `owner_index`, `item_index`),
  KEY `idx_related_id` (`related_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Career resume highlights';

CREATE TABLE IF NOT EXISTS `career_resume_chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `resume_id` bigint NOT NULL COMMENT 'Resume id',
  `user_id` bigint DEFAULT NULL COMMENT 'User id',
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
  KEY `idx_user_resume_type` (`user_id`, `resume_id`, `chunk_type`),
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

CREATE TABLE IF NOT EXISTS `career_resume_parse_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `task_id` varchar(64) NOT NULL COMMENT 'Task id',
  `user_id` bigint DEFAULT NULL COMMENT 'User id',
  `status` varchar(32) NOT NULL COMMENT 'PROCESSING/ANALYZING/SAVING/COMPLETED/FAILED/CANCELED',
  `resume_id` bigint DEFAULT NULL COMMENT 'Completed resume id',
  `original_filename` varchar(255) DEFAULT NULL COMMENT 'Original upload filename',
  `file_size` bigint DEFAULT NULL COMMENT 'Upload file size',
  `content_type` varchar(128) DEFAULT NULL COMMENT 'Upload content type',
  `cv_type` varchar(32) DEFAULT 'upload' COMMENT 'upload/excellent',
  `storage_provider` varchar(64) DEFAULT NULL COMMENT 'local-snapshot/oss',
  `storage_key` varchar(255) DEFAULT NULL COMMENT 'Object storage key or task snapshot key',
  `file_path` varchar(512) DEFAULT NULL COMMENT 'Object storage path',
  `file_snapshot` mediumblob COMMENT 'Bounded local file snapshot for retry/fallback',
  `retry_of_task_id` varchar(64) DEFAULT NULL COMMENT 'Source task id when retrying',
  `error_message` text COMMENT 'Failure reason',
  `job_description` text COMMENT 'Job description used for optimization',
  `optimization_result_json` longtext COMMENT 'Serialized optimization feedback result',
  `optimized_at` datetime DEFAULT NULL COMMENT 'Optimization completion time',
  `start_time` datetime DEFAULT NULL COMMENT 'Task start time',
  `complete_time` datetime DEFAULT NULL COMMENT 'Task complete time',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`),
  KEY `idx_user_task` (`user_id`, `task_id`),
  KEY `idx_user_status_update` (`user_id`, `status`, `update_time`),
  KEY `idx_status_update` (`status`, `update_time`),
  KEY `idx_retry_of_task` (`retry_of_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Career async resume parse task';
