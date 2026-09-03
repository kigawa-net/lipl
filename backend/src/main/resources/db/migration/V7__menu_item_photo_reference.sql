ALTER TABLE menu_items DROP CONSTRAINT uq_menu_items_photo_kaft_uuid;
ALTER TABLE menu_items DROP COLUMN photo_kaft_uuid;
ALTER TABLE menu_items DROP COLUMN photo_filename;

ALTER TABLE menu_items ADD COLUMN photo_id BIGINT NULL;
ALTER TABLE menu_items ADD CONSTRAINT fk_menu_items_photo FOREIGN KEY (photo_id) REFERENCES photos (id) ON DELETE SET NULL;
