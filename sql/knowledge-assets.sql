-- Knowledge assets and HTML exchange. Run only in knowledge_ruoyi.
-- Re-running is non-destructive; no official RuoYi tables are replaced.
CREATE TABLE IF NOT EXISTS kb_asset (
  asset_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件编号',
  purpose VARCHAR(32) NOT NULL COMMENT '用途',
  storage_provider VARCHAR(16) NOT NULL DEFAULT 'ALIYUN_OSS',
  bucket_name VARCHAR(128) NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  oss_version_id VARCHAR(256) DEFAULT NULL,
  original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(128) NOT NULL,
  file_size BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL COMMENT 'UPLOADING READY FAILED DELETE_PENDING DELETED',
  failure_message VARCHAR(500) DEFAULT NULL,
  test_run_id VARCHAR(64) DEFAULT NULL,
  external_key VARCHAR(128) DEFAULT NULL,
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME DEFAULT NULL,
  remark VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (asset_id),
  UNIQUE KEY uk_kb_asset_key (bucket_name, object_key),
  UNIQUE KEY uk_kb_asset_external (create_by, external_key),
  KEY idx_kb_asset_run (test_run_id, status),
  KEY idx_kb_asset_hash (sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库OSS文件台账';

CREATE TABLE IF NOT EXISTS kb_import_batch (
  batch_id BIGINT NOT NULL AUTO_INCREMENT,
  idempotency_key VARCHAR(128) NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL,
  total_count INT NOT NULL DEFAULT 0,
  success_count INT NOT NULL DEFAULT 0,
  failure_count INT NOT NULL DEFAULT 0,
  source_id BIGINT DEFAULT NULL,
  input_asset_id BIGINT DEFAULT NULL,
  test_run_id VARCHAR(64) DEFAULT NULL,
  create_by VARCHAR(64) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL,
  PRIMARY KEY (batch_id),
  UNIQUE KEY uk_kb_import_request (create_by, idempotency_key),
  KEY idx_kb_import_run (test_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库HTML导入批次';

CREATE TABLE IF NOT EXISTS kb_import_item (
  item_id BIGINT NOT NULL AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  item_order INT NOT NULL,
  client_key VARCHAR(160) NOT NULL,
  intent VARCHAR(16) NOT NULL,
  status VARCHAR(24) NOT NULL,
  question_id BIGINT DEFAULT NULL,
  message VARCHAR(500) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (item_id),
  UNIQUE KEY uk_kb_import_item (batch_id, item_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库逐题导入结果';

CREATE TABLE IF NOT EXISTS kb_import_identity (
  client_key VARCHAR(160) NOT NULL,
  create_by VARCHAR(64) NOT NULL,
  question_id BIGINT NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (create_by, client_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部题目稳定标识';

CREATE TABLE IF NOT EXISTS kb_exchange_identity (
  singleton_id INT NOT NULL PRIMARY KEY,
  instance_id CHAR(36) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HTML交换实例标识防跨库误覆盖';
INSERT IGNORE INTO kb_exchange_identity(singleton_id,instance_id) VALUES(1,UUID());
