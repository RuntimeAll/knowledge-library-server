-- Durable idempotency for Agent source/product registration. Business rows are preserved.
CREATE TABLE kb_agent_submission (
 id BIGINT NOT NULL AUTO_INCREMENT,
 actor VARCHAR(64) NOT NULL,
 operation VARCHAR(20) NOT NULL,
 external_key VARCHAR(160) COLLATE utf8mb4_bin NOT NULL,
 request_hash CHAR(64) NOT NULL,
 resource_id BIGINT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id),
 UNIQUE KEY uk_kb_agent_submission(actor,operation,external_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
