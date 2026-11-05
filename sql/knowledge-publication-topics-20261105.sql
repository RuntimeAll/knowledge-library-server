-- PRD-012 / KBM-016: independent, ordered topic keyword configuration.
-- Additive only. Existing NULL means []; do not extract hashtags from old bodies.
-- Run tools/publication-topics-migrate.py for backups, identity/hash checks and replay.
ALTER TABLE kb_publication
  ADD COLUMN topic_keywords_json JSON NULL COMMENT '有序话题纯关键词；NULL或[]为空；不代表平台已绑定';
