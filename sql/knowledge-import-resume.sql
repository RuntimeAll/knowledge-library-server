-- Apply once after knowledge-assets.sql. Never re-run the already applied DDL.
-- An execution claim is private server state; item receipts remain unique by batch/order.
ALTER TABLE kb_import_batch
    ADD COLUMN input_ready TINYINT(1) NOT NULL DEFAULT 0 COMMENT '原HTML及资源清单已持久化',
    ADD COLUMN claim_token VARCHAR(64) DEFAULT NULL COMMENT '执行租约 fencing token',
    ADD COLUMN lease_until DATETIME(3) DEFAULT NULL COMMENT '数据库时钟租约截止',
    ADD COLUMN last_error VARCHAR(500) DEFAULT NULL COMMENT '可恢复基础设施错误';

CREATE TABLE kb_import_resource (
    batch_id BIGINT NOT NULL,
    resource_key VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
    asset_id BIGINT NOT NULL,
    content_type VARCHAR(200) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    file_size BIGINT NOT NULL,
    PRIMARY KEY (batch_id, resource_key),
    KEY idx_kb_import_resource_asset (asset_id),
    CONSTRAINT fk_kb_import_resource_batch FOREIGN KEY (batch_id) REFERENCES kb_import_batch(batch_id),
    CONSTRAINT fk_kb_import_resource_asset FOREIGN KEY (asset_id) REFERENCES kb_asset(asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入原资源清单，用于中断恢复';
