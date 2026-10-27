-- PRD-010: permission and dictionary DML only. No knowledge table/schema/history changes.
-- Preflight: connect to knowledge_ruoyi; verify 6267..6269 are unused or exactly match
-- these permission identities, parent 6110 is the question menu, and existing applied
-- migration hashes match. Apply as one transaction; preserve an external SHA-256 receipt.
-- Existing reader/editor/Agent roles deliberately receive no new review privileges.
START TRANSACTION;

INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT 6267,'题目批准',6110,40,'#',1,0,'F','0','0','knowledge:question:approve','#','admin',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='knowledge:question:approve');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT 6268,'题目恢复',6110,41,'#',1,0,'F','0','0','knowledge:question:restore','#','admin',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='knowledge:question:restore');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT 6269,'计算练习数量例外',6110,42,'#',1,0,'F','0','0','knowledge:question:granularityException','#','admin',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='knowledge:question:granularityException');

INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time)
SELECT 4,'已删除','DELETED','kb_question_status','danger','N','0','admin',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_question_status' AND dict_value='DELETED');

COMMIT;
-- After applying: refresh only kb_question_status via the existing dictionary admin
-- action and re-login review users. Do not FLUSH Redis or automatically grant roles.
