ALTER TABLE menu_items ADD COLUMN photo_kaft_uuid VARCHAR(36) NULL;
ALTER TABLE menu_items ADD COLUMN photo_filename VARCHAR(255) NULL;
ALTER TABLE menu_items ADD CONSTRAINT uq_menu_items_photo_kaft_uuid UNIQUE (photo_kaft_uuid);
