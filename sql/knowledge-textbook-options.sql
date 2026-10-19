-- 年级、学期改为协议固定选项，教材版本改为可输入并复用已有值。
-- 不修改业务字段值、主键或任何关联；执行后通过若依接口刷新字典缓存。
DELETE FROM sys_dict_data WHERE dict_type IN ('kb_grade','kb_term','kb_textbook_version');
DELETE FROM sys_dict_type WHERE dict_type IN ('kb_grade','kb_term','kb_textbook_version');
