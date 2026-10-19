-- 为上传恢复添加所有权令牌；旧上传超过10分钟可用原幂等键先核对OSS再恢复。
SET @kb_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_asset' AND COLUMN_NAME='upload_token')=0,
 'ALTER TABLE kb_asset ADD COLUMN upload_token VARCHAR(36) NULL COMMENT ''上传恢复所有权令牌''', 'SELECT 1');
PREPARE kb_stmt FROM @kb_ddl;
EXECUTE kb_stmt;
DEALLOCATE PREPARE kb_stmt;
