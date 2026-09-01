CREATE TABLE photos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    kaft_uuid VARCHAR(36) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_photos_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE CASCADE,
    CONSTRAINT uq_photos_kaft_uuid UNIQUE (kaft_uuid)
);

CREATE INDEX idx_photos_store_id ON photos (store_id);
