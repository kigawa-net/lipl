CREATE TABLE stores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_sub VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    name VARCHAR(50) NOT NULL,
    business_category VARCHAR(20) NOT NULL,
    operation_type VARCHAR(10) NOT NULL,
    address VARCHAR(200) NULL,
    business_area VARCHAR(200) NULL,
    business_hours VARCHAR(200) NULL,
    phone VARCHAR(20) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_stores_slug UNIQUE (slug)
);

CREATE INDEX idx_stores_owner_sub ON stores (owner_sub);
