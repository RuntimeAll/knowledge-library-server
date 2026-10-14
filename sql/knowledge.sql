-- Official RuoYi knowledge module. Run knowledge-assets.sql first, then this file.
-- Intended schema: knowledge_ruoyi on the existing MySQL 3307. No DROP statements.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS kb_kp (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
  row_version BIGINT NOT NULL DEFAULT 0 COMMENT '编辑版本',
  create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time DATETIME DEFAULT NULL COMMENT '更新时间',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  name VARCHAR(200) NOT NULL COMMENT '考点正式名称',
  description TEXT COMMENT '内容说明',
  grade_hint VARCHAR(100) COMMENT '适用年级提示',
  KEY idx_kb_kp_name(name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考点';

CREATE TABLE IF NOT EXISTS kb_question (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
  row_version BIGINT NOT NULL DEFAULT 0 COMMENT '编辑版本',
  create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time DATETIME DEFAULT NULL COMMENT '更新时间',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  title VARCHAR(300) NOT NULL COMMENT '名称',
  stem_html MEDIUMTEXT NOT NULL COMMENT '标准题干HTML',
  answer_html MEDIUMTEXT COMMENT '标准答案HTML',
  analysis_html MEDIUMTEXT COMMENT '标准解析HTML',
  stem_text MEDIUMTEXT COMMENT '服务端派生题干检索文本',
  stem_hash CHAR(64) NOT NULL COMMENT '当前题干SHA256指纹',
  has_image BOOLEAN DEFAULT FALSE COMMENT '是否包含图片',
  status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' COMMENT '题目状态DRAFT READY RETIRED',
  subject VARCHAR(40) COMMENT '学科字典值',
  question_type VARCHAR(40) COMMENT '题型字典值',
  difficulty VARCHAR(40) COMMENT '难度字典值',
  creation_kind VARCHAR(12) NOT NULL DEFAULT 'UNKNOWN' COMMENT '创作身份UNKNOWN EXTRACTED AUTHORED DERIVED',
  source_note TEXT COMMENT '待整理来源线索',
  KEY idx_kb_question_hash(stem_hash),
  KEY idx_kb_question_class(status,subject,question_type),
  FULLTEXT KEY ft_kb_question_text(stem_text) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共题目';

CREATE TABLE IF NOT EXISTS kb_source (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
  row_version BIGINT NOT NULL DEFAULT 0 COMMENT '编辑版本',
  create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time DATETIME DEFAULT NULL COMMENT '更新时间',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  title VARCHAR(300) NOT NULL COMMENT '名称',
  source_kind VARCHAR(20) NOT NULL COMMENT '来源类型字典值',
  extraction_status VARCHAR(20) NOT NULL DEFAULT 'ORIGINAL_ONLY' COMMENT '整理进度ORIGINAL_ONLY PARTIAL EXTRACTED',
  origin_url TEXT COMMENT '取得地址',
  origin_note TEXT COMMENT '来源说明',
  KEY idx_kb_source_kind(source_kind,extraction_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='来源资料';

CREATE TABLE IF NOT EXISTS kb_outline (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
  row_version BIGINT NOT NULL DEFAULT 0 COMMENT '编辑版本',
  create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time DATETIME DEFAULT NULL COMMENT '更新时间',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  title VARCHAR(200) NOT NULL COMMENT '名称',
  description TEXT COMMENT '内容说明'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学大纲';

CREATE TABLE IF NOT EXISTS kb_outline_node (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
  row_version BIGINT NOT NULL DEFAULT 0 COMMENT '编辑版本',
  create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time DATETIME DEFAULT NULL COMMENT '更新时间',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  outline_id BIGINT NOT NULL COMMENT '所属大纲',
  parent_id BIGINT COMMENT '父章节编号',
  title VARCHAR(200) NOT NULL COMMENT '名称',
  sort_order INT DEFAULT 0 COMMENT '显示顺序',
  KEY idx_kb_node_tree(outline_id,parent_id,sort_order),
  CONSTRAINT fk_kb_node_outline FOREIGN KEY(outline_id) REFERENCES kb_outline(id),
  CONSTRAINT fk_kb_node_parent FOREIGN KEY(parent_id) REFERENCES kb_outline_node(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大纲章节';

CREATE TABLE IF NOT EXISTS kb_material (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
  row_version BIGINT NOT NULL DEFAULT 0 COMMENT '编辑版本',
  create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time DATETIME DEFAULT NULL COMMENT '更新时间',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  title VARCHAR(300) NOT NULL COMMENT '名称',
  description TEXT COMMENT '内容说明',
  kp_id BIGINT COMMENT '考点编号',
  selection_mode VARCHAR(12) NOT NULL DEFAULT 'MIXED' COMMENT '选题模式MIXED EXAM_ONLY',
  CONSTRAINT fk_kb_material_kp FOREIGN KEY(kp_id) REFERENCES kb_kp(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专项资料';

CREATE TABLE IF NOT EXISTS kb_product (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
  row_version BIGINT NOT NULL DEFAULT 0 COMMENT '编辑版本',
  create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time DATETIME DEFAULT NULL COMMENT '更新时间',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  title VARCHAR(300) NOT NULL COMMENT '名称',
  material_id BIGINT COMMENT '专项编号',
  asset_id BIGINT NOT NULL COMMENT 'OSS文件台账编号',
  previous_product_id BIGINT COMMENT '上一成品版本编号',
  root_product_id BIGINT COMMENT '成品版本组根编号',
  version_no INT NOT NULL DEFAULT 1 COMMENT '组内版本序号',
  snapshot_json JSON COMMENT '创建成品时专项及题目版本溯源',
  UNIQUE KEY uk_kb_product_version(root_product_id,version_no),
  CONSTRAINT fk_kb_product_material FOREIGN KEY(material_id) REFERENCES kb_material(id),
  CONSTRAINT fk_kb_product_previous FOREIGN KEY(previous_product_id) REFERENCES kb_product(id),
  CONSTRAINT fk_kb_product_asset FOREIGN KEY(asset_id) REFERENCES kb_asset(asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='冻结成品版本';

CREATE TABLE IF NOT EXISTS kb_question_source (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
  row_version BIGINT NOT NULL DEFAULT 0 COMMENT '编辑版本',
  create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time DATETIME DEFAULT NULL COMMENT '更新时间',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  question_id BIGINT NOT NULL COMMENT '公共题目编号',
  source_id BIGINT NOT NULL COMMENT '来源资料编号',
  question_no VARCHAR(40) COMMENT '原件题号',
  page_from INT COMMENT '开始页码',
  page_to INT COMMENT '结束页码',
  score DECIMAL(5,1) COMMENT '本次卷面分值',
  match_status VARCHAR(12) NOT NULL DEFAULT 'UNVERIFIED' COMMENT '出处核实状态UNVERIFIED VERIFIED REJECTED',
  matched_stem_hash CHAR(64) COMMENT '核实时题干指纹',
  evidence_asset_id BIGINT COMMENT '同来源证据文件编号',
  verification_note TEXT COMMENT '核实说明',
  verified_at DATETIME COMMENT '核实时间',
  KEY idx_kb_occurrence_question(question_id),
  KEY idx_kb_occurrence_source(source_id,match_status),
  CONSTRAINT fk_kb_occ_question FOREIGN KEY(question_id) REFERENCES kb_question(id),
  CONSTRAINT fk_kb_occ_source FOREIGN KEY(source_id) REFERENCES kb_source(id),
  CONSTRAINT fk_kb_occ_evidence FOREIGN KEY(evidence_asset_id) REFERENCES kb_asset(asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目实际出处';

CREATE TABLE IF NOT EXISTS kb_kp_edge (
  from_kp_id BIGINT NOT NULL COMMENT '父考点或组合考点',
  to_kp_id BIGINT NOT NULL COMMENT '子考点或组成考点',
  kind VARCHAR(10) NOT NULL COMMENT '关系类型NEST COMBINE',
  PRIMARY KEY(from_kp_id,to_kp_id,kind),
  CHECK(from_kp_id <> to_kp_id),
  CHECK(kind IN ('NEST','COMBINE')),
  FOREIGN KEY(from_kp_id) REFERENCES kb_kp(id),
  FOREIGN KEY(to_kp_id) REFERENCES kb_kp(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考点嵌套组合关系';

CREATE TABLE IF NOT EXISTS kb_question_kp (
  question_id BIGINT NOT NULL COMMENT '公共题目编号',
  kp_id BIGINT NOT NULL COMMENT '考点编号',
  PRIMARY KEY(question_id,kp_id),
  KEY idx_kb_qkp_reverse(kp_id,question_id),
  FOREIGN KEY(question_id) REFERENCES kb_question(id),
  FOREIGN KEY(kp_id) REFERENCES kb_kp(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目考点关联';

CREATE TABLE IF NOT EXISTS kb_question_asset (
  question_id BIGINT NOT NULL COMMENT '公共题目编号',
  asset_id BIGINT NOT NULL COMMENT 'OSS文件台账编号',
  PRIMARY KEY(question_id,asset_id),
  FOREIGN KEY(question_id) REFERENCES kb_question(id),
  FOREIGN KEY(asset_id) REFERENCES kb_asset(asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目资源关联';

CREATE TABLE IF NOT EXISTS kb_source_asset (
  source_id BIGINT NOT NULL COMMENT '来源资料编号',
  asset_id BIGINT NOT NULL COMMENT 'OSS文件台账编号',
  role VARCHAR(10) NOT NULL DEFAULT 'ORIGINAL' COMMENT '附件用途ORIGINAL EXCERPT EVIDENCE',
  PRIMARY KEY(source_id,asset_id,role),
  FOREIGN KEY(source_id) REFERENCES kb_source(id),
  FOREIGN KEY(asset_id) REFERENCES kb_asset(asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='来源附件关联';

CREATE TABLE IF NOT EXISTS kb_exam_source (
  source_id BIGINT NOT NULL PRIMARY KEY COMMENT '来源资料编号',
  subject VARCHAR(40) COMMENT '学科字典值',
  grade VARCHAR(20) COMMENT '年级原文',
  exam_type VARCHAR(40) COMMENT '考试类型字典值',
  exam_year INT COMMENT '考试年份',
  school_name VARCHAR(200) COMMENT '学校或组织者',
  scope_note TEXT COMMENT '原卷范围声明',
  authenticity_status VARCHAR(12) NOT NULL DEFAULT 'UNVERIFIED' COMMENT '考试身份核实状态',
  verification_note TEXT COMMENT '核实说明',
  FOREIGN KEY(source_id) REFERENCES kb_source(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试来源属性';

CREATE TABLE IF NOT EXISTS kb_outline_node_kp (
  node_id BIGINT NOT NULL COMMENT '章节编号',
  kp_id BIGINT NOT NULL COMMENT '考点编号',
  PRIMARY KEY(node_id,kp_id),
  FOREIGN KEY(node_id) REFERENCES kb_outline_node(id),
  FOREIGN KEY(kp_id) REFERENCES kb_kp(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节考点关联';

CREATE TABLE IF NOT EXISTS kb_material_item (
  material_id BIGINT NOT NULL COMMENT '专项编号',
  question_id BIGINT NOT NULL COMMENT '公共题目编号',
  seq INT NOT NULL COMMENT '题目排列顺序',
  PRIMARY KEY(material_id,question_id),
  UNIQUE KEY uk_kb_material_seq(material_id,seq),
  FOREIGN KEY(material_id) REFERENCES kb_material(id),
  FOREIGN KEY(question_id) REFERENCES kb_question(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专项选题顺序';

CREATE TABLE IF NOT EXISTS kb_material_reference_source (
  material_id BIGINT NOT NULL COMMENT '专项编号',
  source_id BIGINT NOT NULL COMMENT '来源资料编号',
  note VARCHAR(500) COMMENT '参考理由',
  PRIMARY KEY(material_id,source_id),
  FOREIGN KEY(material_id) REFERENCES kb_material(id),
  FOREIGN KEY(source_id) REFERENCES kb_source(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专项参考来源';

CREATE TABLE IF NOT EXISTS kb_question_derivation (
  parent_question_id BIGINT NOT NULL COMMENT '母题编号',
  child_question_id BIGINT NOT NULL COMMENT '变式题编号',
  is_primary BOOLEAN NOT NULL COMMENT '是否主母题',
  operation VARCHAR(40) COMMENT '变式操作字典值',
  change_note TEXT NOT NULL COMMENT '改动说明',
  preserved_note TEXT NOT NULL COMMENT '保留说明',
  create_by VARCHAR(64) COMMENT '创建者',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  primary_child_id BIGINT GENERATED ALWAYS AS (CASE WHEN is_primary=1 THEN child_question_id ELSE NULL END) STORED COMMENT '主母题唯一约束生成列',
  PRIMARY KEY(parent_question_id,child_question_id),
  UNIQUE KEY uk_kb_primary_parent(primary_child_id),
  CHECK(parent_question_id <> child_question_id),
  FOREIGN KEY(parent_question_id) REFERENCES kb_question(id),
  FOREIGN KEY(child_question_id) REFERENCES kb_question(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='母题变式关系';

INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
VALUES(6100,'知识库管理',0,4,'knowledge',NULL,1,0,'M','0','0','','education','admin',NOW(),'知识库业务入口');
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6110,'题库',6100,1,'question','knowledge/question/index',1,0,'C','0','0','knowledge:question:list','question','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6111,'题库查询',6110,1,'#',1,0,'F','0','0','knowledge:question:query','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6112,'题库新增',6110,2,'#',1,0,'F','0','0','knowledge:question:add','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6113,'题库修改',6110,3,'#',1,0,'F','0','0','knowledge:question:edit','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6114,'题库删除',6110,4,'#',1,0,'F','0','0','knowledge:question:remove','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6115,'题库导出',6110,5,'#',1,0,'F','0','0','knowledge:question:export','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6116,'题库导入',6110,6,'#',1,0,'F','0','0','knowledge:question:import','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6130,'来源资料',6100,2,'source','knowledge/source/index',1,0,'C','0','0','knowledge:source:list','documentation','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6131,'来源资料查询',6130,1,'#',1,0,'F','0','0','knowledge:source:query','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6132,'来源资料新增',6130,2,'#',1,0,'F','0','0','knowledge:source:add','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6133,'来源资料修改',6130,3,'#',1,0,'F','0','0','knowledge:source:edit','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6134,'来源资料删除',6130,4,'#',1,0,'F','0','0','knowledge:source:remove','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6135,'来源资料导出',6130,5,'#',1,0,'F','0','0','knowledge:source:export','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6136,'来源资料导入',6130,6,'#',1,0,'F','0','0','knowledge:source:import','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6150,'成品库',6100,3,'product','knowledge/product/index',1,0,'C','0','0','knowledge:product:list','zip','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6151,'成品库查询',6150,1,'#',1,0,'F','0','0','knowledge:product:query','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6152,'成品库新增',6150,2,'#',1,0,'F','0','0','knowledge:product:add','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6153,'成品库修改',6150,3,'#',1,0,'F','0','0','knowledge:product:edit','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6154,'成品库删除',6150,4,'#',1,0,'F','0','0','knowledge:product:remove','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6155,'成品库导出',6150,5,'#',1,0,'F','0','0','knowledge:product:export','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6156,'成品库导入',6150,6,'#',1,0,'F','0','0','knowledge:product:import','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6170,'考点管理',6100,4,'kp','knowledge/kp/index',1,0,'C','0','0','knowledge:kp:list','tree','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6171,'考点管理查询',6170,1,'#',1,0,'F','0','0','knowledge:kp:query','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6172,'考点管理新增',6170,2,'#',1,0,'F','0','0','knowledge:kp:add','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6173,'考点管理修改',6170,3,'#',1,0,'F','0','0','knowledge:kp:edit','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6174,'考点管理删除',6170,4,'#',1,0,'F','0','0','knowledge:kp:remove','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6175,'考点管理导出',6170,5,'#',1,0,'F','0','0','knowledge:kp:export','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6176,'考点管理导入',6170,6,'#',1,0,'F','0','0','knowledge:kp:import','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6190,'大纲管理',6100,5,'outline','knowledge/outline/index',1,0,'C','0','0','knowledge:outline:list','tree-table','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6191,'大纲管理查询',6190,1,'#',1,0,'F','0','0','knowledge:outline:query','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6192,'大纲管理新增',6190,2,'#',1,0,'F','0','0','knowledge:outline:add','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6193,'大纲管理修改',6190,3,'#',1,0,'F','0','0','knowledge:outline:edit','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6194,'大纲管理删除',6190,4,'#',1,0,'F','0','0','knowledge:outline:remove','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6195,'大纲管理导出',6190,5,'#',1,0,'F','0','0','knowledge:outline:export','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6196,'大纲管理导入',6190,6,'#',1,0,'F','0','0','knowledge:outline:import','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6210,'专项资料',6100,6,'material','knowledge/material/index',1,0,'C','0','0','knowledge:material:list','list','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6211,'专项资料查询',6210,1,'#',1,0,'F','0','0','knowledge:material:query','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6212,'专项资料新增',6210,2,'#',1,0,'F','0','0','knowledge:material:add','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6213,'专项资料修改',6210,3,'#',1,0,'F','0','0','knowledge:material:edit','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6214,'专项资料删除',6210,4,'#',1,0,'F','0','0','knowledge:material:remove','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6215,'专项资料导出',6210,5,'#',1,0,'F','0','0','knowledge:material:export','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6216,'专项资料导入',6210,6,'#',1,0,'F','0','0','knowledge:material:import','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6250,'文件查看',6100,20,'#',1,0,'F','0','0','knowledge:asset:query','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6251,'文件上传',6100,21,'#',1,0,'F','0','0','knowledge:asset:upload','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6252,'文件删除',6100,22,'#',1,0,'F','0','0','knowledge:asset:remove','#','admin',NOW());
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6253,'文件下载',6100,23,'#',1,0,'F','0','0','knowledge:asset:download','#','admin',NOW());
INSERT INTO sys_role(role_id,role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark) SELECT 610,'知识库只读','knowledge_reader',20,'1',1,1,'0','0','admin',NOW(),'知识库默认角色' WHERE NOT EXISTS(SELECT 1 FROM sys_role WHERE role_key='knowledge_reader');
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6100 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6110 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6111 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6115 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6130 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6131 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6135 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6150 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6151 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6155 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6170 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6171 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6175 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6190 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6191 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6195 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6210 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6211 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6215 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6250 FROM sys_role WHERE role_key='knowledge_reader';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6253 FROM sys_role WHERE role_key='knowledge_reader';
INSERT INTO sys_role(role_id,role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark) SELECT 611,'知识库编辑','knowledge_editor',20,'1',1,1,'0','0','admin',NOW(),'知识库默认角色' WHERE NOT EXISTS(SELECT 1 FROM sys_role WHERE role_key='knowledge_editor');
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6100 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6110 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6111 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6112 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6113 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6114 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6115 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6116 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6130 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6131 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6132 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6133 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6134 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6135 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6136 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6150 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6151 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6152 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6153 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6154 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6155 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6156 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6170 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6171 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6172 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6173 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6174 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6175 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6176 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6190 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6191 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6192 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6193 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6194 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6195 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6196 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6210 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6211 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6212 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6213 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6214 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6215 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6216 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6250 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6251 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6252 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6253 FROM sys_role WHERE role_key='knowledge_editor';
INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time) SELECT '学科','kb_subject','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_type WHERE dict_type='kb_subject');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 1,'数学','MATH','kb_subject','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_subject' AND dict_value='MATH');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 2,'语文','CHINESE','kb_subject','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_subject' AND dict_value='CHINESE');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 3,'英语','ENGLISH','kb_subject','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_subject' AND dict_value='ENGLISH');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 4,'科学','SCIENCE','kb_subject','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_subject' AND dict_value='SCIENCE');
INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time) SELECT '题型','kb_question_type','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_type WHERE dict_type='kb_question_type');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 1,'选择题','CHOICE','kb_question_type','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_question_type' AND dict_value='CHOICE');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 2,'填空题','FILL','kb_question_type','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_question_type' AND dict_value='FILL');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 3,'计算题','CALCULATION','kb_question_type','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_question_type' AND dict_value='CALCULATION');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 4,'应用题','APPLICATION','kb_question_type','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_question_type' AND dict_value='APPLICATION');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 5,'其他','OTHER','kb_question_type','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_question_type' AND dict_value='OTHER');
INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time) SELECT '难度','kb_difficulty','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_type WHERE dict_type='kb_difficulty');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 1,'基础','EASY','kb_difficulty','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_difficulty' AND dict_value='EASY');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 2,'中等','MEDIUM','kb_difficulty','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_difficulty' AND dict_value='MEDIUM');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 3,'较难','HARD','kb_difficulty','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_difficulty' AND dict_value='HARD');
INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time) SELECT '来源类型','kb_source_kind','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_type WHERE dict_type='kb_source_kind');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 1,'真题卷','EXAM','kb_source_kind','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_source_kind' AND dict_value='EXAM');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 2,'讲义','HANDOUT','kb_source_kind','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_source_kind' AND dict_value='HANDOUT');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 3,'教辅','WORKBOOK','kb_source_kind','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_source_kind' AND dict_value='WORKBOOK');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 4,'自编','AUTHORED','kb_source_kind','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_source_kind' AND dict_value='AUTHORED');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 5,'其他','OTHER','kb_source_kind','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_source_kind' AND dict_value='OTHER');
INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time) SELECT '考试类型','kb_exam_type','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_type WHERE dict_type='kb_exam_type');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 1,'期中','MIDTERM','kb_exam_type','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_exam_type' AND dict_value='MIDTERM');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 2,'期末','FINAL','kb_exam_type','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_exam_type' AND dict_value='FINAL');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 3,'升学考试','ENTRANCE','kb_exam_type','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_exam_type' AND dict_value='ENTRANCE');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 4,'模拟考试','MOCK','kb_exam_type','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_exam_type' AND dict_value='MOCK');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 5,'其他','OTHER','kb_exam_type','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_exam_type' AND dict_value='OTHER');
INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time) SELECT '变式操作','kb_derivation_operation','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_type WHERE dict_type='kb_derivation_operation');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 1,'改数字','NUMBER','kb_derivation_operation','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_derivation_operation' AND dict_value='NUMBER');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 2,'改条件','CONDITION','kb_derivation_operation','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_derivation_operation' AND dict_value='CONDITION');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 3,'改设问','QUESTION','kb_derivation_operation','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_derivation_operation' AND dict_value='QUESTION');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,list_class,is_default,status,create_by,create_time) SELECT 4,'其他','OTHER','kb_derivation_operation','default','N','0','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='kb_derivation_operation' AND dict_value='OTHER');

INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time) VALUES(6254,'HTML导入',6110,30,'#',1,0,'F','0','0','knowledge:import:execute','#','admin',NOW()),(6255,'导入批次查询',6110,31,'#',1,0,'F','0','0','knowledge:import:query','#','admin',NOW());
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6254 FROM sys_role WHERE role_key='knowledge_editor';
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6255 FROM sys_role WHERE role_key='knowledge_editor';
