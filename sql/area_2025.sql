-- PRD-003: user-supplied area_2025.sql structure, without DROP or FK bypass.
-- Data source SHA-256: 9a1575d1e96ccedaa605876873d5064b4fa8c698c2fb41e9f4eb5b30e9466f76
-- Run tools/import-area-2025.py --check-db, then --apply to import and project
-- all 49,563 original code/name/full_name rows with conflict checks and receipt.
-- The original source is read only. This file does not contain business data.
CREATE TABLE IF NOT EXISTS `area_2025` (
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `full_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  INDEX `code`(`code`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;
