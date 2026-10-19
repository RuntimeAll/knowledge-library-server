-- PRD-002 additive layout experience and immutable render snapshots. Existing IDs are preserved.
CREATE TABLE IF NOT EXISTS kb_experience (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, code VARCHAR(64) NOT NULL, name VARCHAR(200) NOT NULL,
 experience_type VARCHAR(32) NOT NULL, description TEXT, usage_notes TEXT, status VARCHAR(20) NOT NULL,
 row_version BIGINT NOT NULL DEFAULT 0, create_by VARCHAR(64) NOT NULL DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_by VARCHAR(64) NOT NULL DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, remark VARCHAR(500),
 UNIQUE KEY uk_kb_experience_code(code), KEY ix_kb_experience_status(status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS kb_experience_version (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, experience_id BIGINT NOT NULL, version_no INT NOT NULL, status VARCHAR(20) NOT NULL,
 schema_version VARCHAR(40) NOT NULL, field_schema_json JSON NOT NULL, default_values_json JSON NOT NULL, definition_json JSON NOT NULL,
 definition_hash CHAR(64) NOT NULL, change_note TEXT, publish_by VARCHAR(64), publish_time DATETIME,
 row_version BIGINT NOT NULL DEFAULT 0, create_by VARCHAR(64) NOT NULL DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_by VARCHAR(64) NOT NULL DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, remark VARCHAR(500),
 UNIQUE KEY uk_kb_experience_version(experience_id,version_no), KEY ix_kb_experience_version_status(status),
 CONSTRAINT fk_kb_experience_version FOREIGN KEY(experience_id) REFERENCES kb_experience(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS kb_experience_version_asset (
 version_id BIGINT NOT NULL, resource_key VARCHAR(128) NOT NULL, asset_id BIGINT NOT NULL, asset_role VARCHAR(32) NOT NULL,
 PRIMARY KEY(version_id,resource_key), KEY ix_kb_template_asset(asset_id),
 CONSTRAINT fk_kb_template_asset_version FOREIGN KEY(version_id) REFERENCES kb_experience_version(id),
 CONSTRAINT fk_kb_template_asset FOREIGN KEY(asset_id) REFERENCES kb_asset(asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS kb_render_job (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, template_version_id BIGINT NOT NULL, actor_user_id BIGINT NOT NULL,
 idempotency_key VARCHAR(128) NOT NULL, request_hash CHAR(64) NOT NULL, material_id BIGINT, material_row_version BIGINT,
 config_snapshot_json JSON NOT NULL, template_snapshot_json JSON NOT NULL, renderer_fingerprint VARCHAR(200),
 status VARCHAR(24) NOT NULL, lease_token VARCHAR(64), lease_until DATETIME, retry_count INT NOT NULL DEFAULT 0,
 error_code VARCHAR(64), error_message VARCHAR(2000), row_version BIGINT NOT NULL DEFAULT 0,
 create_by VARCHAR(64) NOT NULL DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_by VARCHAR(64) NOT NULL DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, remark VARCHAR(500),
 UNIQUE KEY uk_kb_render_request(actor_user_id,idempotency_key), KEY ix_kb_render_queue(status,lease_until),
 KEY ix_kb_render_material(material_id), CONSTRAINT fk_kb_render_template FOREIGN KEY(template_version_id) REFERENCES kb_experience_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS kb_render_job_item (
 job_id BIGINT NOT NULL, seq INT NOT NULL, section_key VARCHAR(64) NOT NULL, day_key VARCHAR(64), question_id BIGINT NOT NULL,
 question_row_version BIGINT NOT NULL, content_hash CHAR(64) NOT NULL, snapshot_json JSON NOT NULL,
 PRIMARY KEY(job_id,seq), KEY ix_kb_render_question(question_id),
 CONSTRAINT fk_kb_render_item_job FOREIGN KEY(job_id) REFERENCES kb_render_job(id),
 CONSTRAINT fk_kb_render_item_question FOREIGN KEY(question_id) REFERENCES kb_question(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS kb_render_job_asset (
 job_id BIGINT NOT NULL, resource_key VARCHAR(128) NOT NULL, asset_id BIGINT NOT NULL, asset_role VARCHAR(32) NOT NULL, sha256 CHAR(64) NOT NULL,
 PRIMARY KEY(job_id,resource_key), KEY ix_kb_render_asset(asset_id),
 CONSTRAINT fk_kb_render_asset_job FOREIGN KEY(job_id) REFERENCES kb_render_job(id),
 CONSTRAINT fk_kb_render_asset FOREIGN KEY(asset_id) REFERENCES kb_asset(asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE kb_product ADD COLUMN render_job_id BIGINT NULL, ADD COLUMN render_output_key VARCHAR(64) NULL,
 ADD UNIQUE KEY uk_kb_product_render(render_job_id,render_output_key);
INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time) SELECT '经验类型','kb_experience_type','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_type WHERE dict_type='kb_experience_type');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 1,'排版经验','LAYOUT','kb_experience_type','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_experience_type' AND dict_value='LAYOUT');
INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time) SELECT '经验状态','kb_experience_status','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_type WHERE dict_type='kb_experience_status');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 1,'启用','ACTIVE','kb_experience_status','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_experience_status' AND dict_value='ACTIVE');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 2,'停用','DISABLED','kb_experience_status','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_experience_status' AND dict_value='DISABLED');
INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time) SELECT '经验版本状态','kb_experience_version_status','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_type WHERE dict_type='kb_experience_version_status');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 1,'草稿','DRAFT','kb_experience_version_status','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_experience_version_status' AND dict_value='DRAFT');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 2,'已发布','PUBLISHED','kb_experience_version_status','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_experience_version_status' AND dict_value='PUBLISHED');
INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time) SELECT '生成任务状态','kb_render_status','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_type WHERE dict_type='kb_render_status');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 1,'等待生成','QUEUED','kb_render_status','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_render_status' AND dict_value='QUEUED');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 2,'生成中','RUNNING','kb_render_status','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_render_status' AND dict_value='RUNNING');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 3,'已完成','SUCCEEDED','kb_render_status','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_render_status' AND dict_value='SUCCEEDED');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 4,'失败可重试','FAILED','kb_render_status','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_render_status' AND dict_value='FAILED');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '经验模板',parent_id,7,'experience','knowledge/experience/index',1,0,'C','0','0','knowledge:experience:list','build','admin',NOW()
FROM sys_menu WHERE perms='knowledge:question:list' AND NOT EXISTS(SELECT 1 FROM (SELECT perms FROM sys_menu) m WHERE perms='knowledge:experience:list') LIMIT 1;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) SELECT '查看模板',menu_id,99,'','F','0','0','knowledge:experience:query','admin',NOW() FROM sys_menu WHERE perms='knowledge:experience:list' AND NOT EXISTS(SELECT 1 FROM (SELECT perms FROM sys_menu) m WHERE perms='knowledge:experience:query') LIMIT 1;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) SELECT '新增模板',menu_id,99,'','F','0','0','knowledge:experience:add','admin',NOW() FROM sys_menu WHERE perms='knowledge:experience:list' AND NOT EXISTS(SELECT 1 FROM (SELECT perms FROM sys_menu) m WHERE perms='knowledge:experience:add') LIMIT 1;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) SELECT '维护模板',menu_id,99,'','F','0','0','knowledge:experience:edit','admin',NOW() FROM sys_menu WHERE perms='knowledge:experience:list' AND NOT EXISTS(SELECT 1 FROM (SELECT perms FROM sys_menu) m WHERE perms='knowledge:experience:edit') LIMIT 1;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) SELECT '删除模板',menu_id,99,'','F','0','0','knowledge:experience:remove','admin',NOW() FROM sys_menu WHERE perms='knowledge:experience:list' AND NOT EXISTS(SELECT 1 FROM (SELECT perms FROM sys_menu) m WHERE perms='knowledge:experience:remove') LIMIT 1;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) SELECT '发布模板',menu_id,99,'','F','0','0','knowledge:experience:publish','admin',NOW() FROM sys_menu WHERE perms='knowledge:experience:list' AND NOT EXISTS(SELECT 1 FROM (SELECT perms FROM sys_menu) m WHERE perms='knowledge:experience:publish') LIMIT 1;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) SELECT '生成成品',menu_id,99,'','F','0','0','knowledge:render:add','admin',NOW() FROM sys_menu WHERE perms='knowledge:experience:list' AND NOT EXISTS(SELECT 1 FROM (SELECT perms FROM sys_menu) m WHERE perms='knowledge:render:add') LIMIT 1;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) SELECT '查看生成任务',menu_id,99,'','F','0','0','knowledge:render:query','admin',NOW() FROM sys_menu WHERE perms='knowledge:experience:list' AND NOT EXISTS(SELECT 1 FROM (SELECT perms FROM sys_menu) m WHERE perms='knowledge:render:query') LIMIT 1;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) SELECT '重试生成',menu_id,99,'','F','0','0','knowledge:render:retry','admin',NOW() FROM sys_menu WHERE perms='knowledge:experience:list' AND NOT EXISTS(SELECT 1 FROM (SELECT perms FROM sys_menu) m WHERE perms='knowledge:render:retry') LIMIT 1;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) SELECT '下载生成成品',menu_id,99,'','F','0','0','knowledge:render:download','admin',NOW() FROM sys_menu WHERE perms='knowledge:experience:list' AND NOT EXISTS(SELECT 1 FROM (SELECT perms FROM sys_menu) m WHERE perms='knowledge:render:download') LIMIT 1;
