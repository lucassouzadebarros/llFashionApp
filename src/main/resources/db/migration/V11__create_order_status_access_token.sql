CREATE TABLE order_status_access_token (
    id UUID PRIMARY KEY,
    access_token_hash VARCHAR(128) NOT NULL,
    customer_phone VARCHAR(50),
    order_id UUID,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_order_status_access_token_order
        FOREIGN KEY (order_id) REFERENCES whatsapp_order (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_order_status_access_token_hash
    ON order_status_access_token (access_token_hash);

CREATE INDEX idx_order_status_access_token_phone
    ON order_status_access_token (customer_phone);

CREATE INDEX idx_order_status_access_token_order
    ON order_status_access_token (order_id);

CREATE INDEX idx_order_status_access_token_expires_at
    ON order_status_access_token (expires_at);
