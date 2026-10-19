-- Align operational labels with the storage adapter's actual durable states.
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,status,create_by,create_time)
SELECT 7,'等待重试清理','DELETE_PENDING','kb_asset_status','0','admin',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_asset_status' AND dict_value='DELETE_PENDING');
UPDATE sys_dict_data SET status='1',update_by='admin',update_time=NOW()
WHERE dict_type='kb_asset_status' AND dict_value IN ('PENDING','DELETING');
