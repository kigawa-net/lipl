CREATE TABLE interview_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    role VARCHAR(10) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_interview_messages_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE CASCADE
);

CREATE INDEX idx_interview_messages_store_id ON interview_messages (store_id);

CREATE TABLE lp_contents (
    store_id BIGINT NOT NULL PRIMARY KEY,
    catchphrase VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_lp_contents_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE CASCADE
);

CREATE TABLE ai_generation_usage (
    owner_sub VARCHAR(255) NOT NULL PRIMARY KEY,
    generation_count INT NOT NULL DEFAULT 0
);
