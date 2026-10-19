-- Additive content classification. Retained business records are classified through APIs.
ALTER TABLE kb_question
 ADD COLUMN content_kind VARCHAR(30) NOT NULL DEFAULT 'EXERCISE' COMMENT '内容类型字典：练习或知识点内容' AFTER subject,
 ADD KEY idx_kb_question_content(content_kind,subject,question_type,status);

INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time)
 SELECT '内容类型','kb_content_kind','0','admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_dict_type WHERE dict_type='kb_content_kind');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time)
 SELECT 1,'练习题','EXERCISE','kb_content_kind','primary','Y','0','admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_content_kind' AND dict_value='EXERCISE');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time)
 SELECT 2,'知识点内容','KNOWLEDGE','kb_content_kind','success','N','0','admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_content_kind' AND dict_value='KNOWLEDGE');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time)
 SELECT 20,'知识点讲解','KNOWLEDGE_EXPLANATION','kb_question_type','success','N','0','admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_question_type' AND dict_value='KNOWLEDGE_EXPLANATION');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time)
 SELECT 21,'知识点梳理','KNOWLEDGE_REVIEW','kb_question_type','success','N','0','admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_question_type' AND dict_value='KNOWLEDGE_REVIEW');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time)
 SELECT 6,'解答题','SOLVING','kb_question_type','default','N','0','admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_question_type' AND dict_value='SOLVING');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time)
 SELECT 7,'探究题','INQUIRY','kb_question_type','default','N','0','admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_question_type' AND dict_value='INQUIRY');
