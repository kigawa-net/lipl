-- V7（menu_item_photo_reference）が一部の環境でDDL途中（MariaDBは非トランザクション）に
-- 失敗し、photo_filenameの削除・photo_id追加・外部キー追加が未実施のまま残っていた。
-- IF [NOT] EXISTSおよび動的SQLガードで保護し、V7が正常完了済みの環境では安全なno-opとする。

ALTER TABLE menu_items DROP COLUMN IF EXISTS photo_filename;
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS photo_id BIGINT NULL;

SET @fk_exists = (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'menu_items'
    AND CONSTRAINT_NAME = 'fk_menu_items_photo'
);
SET @add_fk_sql = IF(@fk_exists = 0,
  'ALTER TABLE menu_items ADD CONSTRAINT fk_menu_items_photo FOREIGN KEY (photo_id) REFERENCES photos (id) ON DELETE SET NULL',
  'SELECT 1'
);
PREPARE add_fk_stmt FROM @add_fk_sql;
EXECUTE add_fk_stmt;
DEALLOCATE PREPARE add_fk_stmt;
