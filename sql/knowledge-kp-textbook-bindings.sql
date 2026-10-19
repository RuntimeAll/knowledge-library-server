-- Additive only. Grade belongs to a textbook, never to a reusable knowledge point.
-- Keep legacy grade_hint physically for migration audit; application no longer reads/writes it.
ALTER TABLE kb_kp ADD COLUMN subject VARCHAR(30) NULL, ADD KEY idx_kb_kp_subject(subject);
CREATE TABLE kb_kp_outline (
  kp_id BIGINT NOT NULL,
  outline_id BIGINT NOT NULL,
  PRIMARY KEY(kp_id,outline_id),
  KEY idx_kb_kp_outline_outline(outline_id),
  CONSTRAINT fk_kb_kpo_point FOREIGN KEY(kp_id) REFERENCES kb_kp(id),
  CONSTRAINT fk_kb_kpo_outline FOREIGN KEY(outline_id) REFERENCES kb_outline(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Reusable points mounted under textbook outlines';
-- Preserve explicit textbook chapter bindings; do not infer textbook edition or chapters.
INSERT IGNORE INTO kb_kp_outline(kp_id,outline_id)
 SELECT nk.kp_id,n.outline_id FROM kb_outline_node_kp nk
 JOIN kb_outline_node n ON n.id=nk.node_id JOIN kb_outline o ON o.id=n.outline_id
 WHERE o.catalog_kind='TEXTBOOK';
