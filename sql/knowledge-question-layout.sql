-- Additive v1-compatible layout metadata. Apply once; no classification is inferred from image presence.
ALTER TABLE kb_question
 ADD COLUMN layout_mode VARCHAR(30) NOT NULL DEFAULT 'PLAIN' COMMENT '排版方式字典' AFTER content_kind,
 ADD COLUMN unit_kind VARCHAR(30) NOT NULL DEFAULT 'SINGLE' COMMENT '单题或成组题字典' AFTER layout_mode,
 ADD COLUMN pagination_mode VARCHAR(30) NOT NULL DEFAULT 'FLOW' COMMENT '分页方式字典' AFTER unit_kind,
 ADD COLUMN layout_note VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '排版说明供人和Agent复用' AFTER pagination_mode,
 ADD COLUMN item_count INT NOT NULL DEFAULT 1 COMMENT '受控group直属子项数量；单题为1' AFTER layout_note,
 ADD KEY idx_kb_question_layout(layout_mode,unit_kind,pagination_mode);

INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time)
 SELECT '题目排版方式','kb_layout_mode','0','admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_dict_type WHERE dict_type='kb_layout_mode');
INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time)
 SELECT '题目单元','kb_unit_kind','0','admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_dict_type WHERE dict_type='kb_unit_kind');
INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time)
 SELECT '题目分页方式','kb_pagination_mode','0','admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_dict_type WHERE dict_type='kb_pagination_mode');

INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time)
 SELECT 1,'普通排版','PLAIN','kb_layout_mode','default','Y','0','admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_layout_mode' AND dict_value='PLAIN');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time)
 SELECT 2,'结构化排版','STRUCTURED','kb_layout_mode','primary','N','0','admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_layout_mode' AND dict_value='STRUCTURED');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time)
 SELECT 1,'单题','SINGLE','kb_unit_kind','default','Y','0','admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_unit_kind' AND dict_value='SINGLE');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time)
 SELECT 2,'成组题','GROUP','kb_unit_kind','primary','N','0','admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_unit_kind' AND dict_value='GROUP');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time)
 SELECT 1,'自然流式','FLOW','kb_pagination_mode','default','Y','0','admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_pagination_mode' AND dict_value='FLOW');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time)
 SELECT 2,'尽量整题同页','KEEP_TOGETHER','kb_pagination_mode','primary','N','0','admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_pagination_mode' AND dict_value='KEEP_TOGETHER');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time)
 SELECT 3,'允许跨页','ALLOW_SPLIT','kb_pagination_mode','info','N','0','admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_pagination_mode' AND dict_value='ALLOW_SPLIT');
