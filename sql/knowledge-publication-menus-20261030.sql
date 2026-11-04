-- PRD-012 additive menu migration. Does not grant approval or sale to existing ordinary roles.
SET @kb_parent = (SELECT menu_id FROM sys_menu WHERE path='knowledge' AND parent_id=0 LIMIT 1);
SET @pub_menu = (SELECT menu_id FROM sys_menu WHERE perms='knowledge:publication:list' LIMIT 1);
SET @pub_menu = COALESCE(@pub_menu,(SELECT COALESCE(MAX(menu_id),10000)+1 FROM sys_menu));
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT @pub_menu,'发布台',@kb_parent,0,'publication','knowledge/publication/index',1,0,'C','0','0','knowledge:publication:list','documentation','admin',NOW()
WHERE @kb_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='knowledge:publication:list');
SET @pub_action = (SELECT COALESCE(MAX(menu_id),10000)+1 FROM sys_menu);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT @pub_action,'发布资料查看',@pub_menu,1,'#',1,0,'F','0','0','knowledge:publication:query','#','admin',NOW()
WHERE @kb_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='knowledge:publication:query');
SET @pub_action = (SELECT COALESCE(MAX(menu_id),10000)+1 FROM sys_menu);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT @pub_action,'发布资料新增',@pub_menu,2,'#',1,0,'F','0','0','knowledge:publication:add','#','admin',NOW()
WHERE @kb_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='knowledge:publication:add');
SET @pub_action = (SELECT COALESCE(MAX(menu_id),10000)+1 FROM sys_menu);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT @pub_action,'发布资料修改',@pub_menu,3,'#',1,0,'F','0','0','knowledge:publication:edit','#','admin',NOW()
WHERE @kb_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='knowledge:publication:edit');
SET @pub_action = (SELECT COALESCE(MAX(menu_id),10000)+1 FROM sys_menu);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT @pub_action,'发布资料迁入',@pub_menu,4,'#',1,0,'F','0','0','knowledge:publication:import','#','admin',NOW()
WHERE @kb_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='knowledge:publication:import');
SET @pub_action = (SELECT COALESCE(MAX(menu_id),10000)+1 FROM sys_menu);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT @pub_action,'发布资料自检',@pub_menu,5,'#',1,0,'F','0','0','knowledge:publication:check','#','admin',NOW()
WHERE @kb_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='knowledge:publication:check');
SET @pub_action = (SELECT COALESCE(MAX(menu_id),10000)+1 FROM sys_menu);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT @pub_action,'发布资料发售',@pub_menu,6,'#',1,0,'F','0','0','knowledge:publication:sale','#','admin',NOW()
WHERE @kb_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='knowledge:publication:sale');
SET @pub_action = (SELECT COALESCE(MAX(menu_id),10000)+1 FROM sys_menu);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT @pub_action,'发布资料撤回',@pub_menu,7,'#',1,0,'F','0','0','knowledge:publication:retract','#','admin',NOW()
WHERE @kb_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='knowledge:publication:retract');
SET @pub_action = (SELECT COALESCE(MAX(menu_id),10000)+1 FROM sys_menu);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT @pub_action,'发布资料删除',@pub_menu,8,'#',1,0,'F','0','0','knowledge:publication:remove','#','admin',NOW()
WHERE @kb_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='knowledge:publication:remove');
SET @pub_action = (SELECT COALESCE(MAX(menu_id),10000)+1 FROM sys_menu);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT @pub_action,'发布资料恢复',@pub_menu,9,'#',1,0,'F','0','0','knowledge:publication:restore','#','admin',NOW()
WHERE @kb_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='knowledge:publication:restore');
SET @pub_action = (SELECT COALESCE(MAX(menu_id),10000)+1 FROM sys_menu);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT @pub_action,'发布资料重试生成',@pub_menu,10,'#',1,0,'F','0','0','knowledge:publication:shareRetry','#','admin',NOW()
WHERE @kb_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='knowledge:publication:shareRetry');

-- Explicit full-management scope, independent of sale permission. No ordinary role grant is implicit.
SET @publication_menu_next := (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT @publication_menu_next, '发布资料全量范围', @pub_menu, 99, '#', NULL, 1, 0, 'F', '0', '0', 'knowledge:publication:all', '#', 'admin', NOW(), 'PRD-012显式全量管理范围，不包含发售授权'
WHERE @kb_parent IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='knowledge:publication:all');
