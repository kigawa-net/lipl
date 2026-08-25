CREATE TABLE store_sns_links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    platform VARCHAR(20) NOT NULL,
    url VARCHAR(500) NOT NULL,
    CONSTRAINT fk_store_sns_links_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE CASCADE,
    CONSTRAINT uq_store_sns_links_store_platform UNIQUE (store_id, platform)
);
