-- One NEST parent per point. COMBINE remains a separate reusable relationship.
-- Existing retained points have no NEST edges; no business rows are rewritten.
ALTER TABLE kb_kp_edge
    ADD COLUMN nest_child_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN kind='NEST' THEN to_kp_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_kp_nest_parent (nest_child_id);
