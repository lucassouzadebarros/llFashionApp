ALTER TABLE storefront_cart
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE whatsapp_order
    ADD COLUMN status_public_token VARCHAR(120),
    ADD COLUMN nuvemshop_order_number VARCHAR(100),
    ADD COLUMN payment_status VARCHAR(100),
    ADD COLUMN shipping_status VARCHAR(100),
    ADD COLUMN shipping_tracking_number VARCHAR(255),
    ADD COLUMN shipping_tracking_url TEXT,
    ADD COLUMN shipping_method VARCHAR(255),
    ADD COLUMN shipping_min_days INTEGER,
    ADD COLUMN shipping_max_days INTEGER;

UPDATE whatsapp_order
SET status_public_token = 'ord_' || replace(gen_random_uuid()::text, '-', '')
WHERE status_public_token IS NULL;

ALTER TABLE whatsapp_order
    ALTER COLUMN status_public_token SET NOT NULL;

CREATE UNIQUE INDEX idx_whatsapp_order_status_public_token
    ON whatsapp_order (status_public_token);

CREATE INDEX idx_whatsapp_order_customer_phone_created_at
    ON whatsapp_order (customer_phone, created_at);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    operation VARCHAR(100) NOT NULL,
    cart_id UUID,
    request_hash VARCHAR(255),
    response_body TEXT,
    status VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_idempotency_keys_cart
        FOREIGN KEY (cart_id) REFERENCES storefront_cart(id)
);

CREATE INDEX idx_idempotency_keys_cart_operation
    ON idempotency_keys (cart_id, operation);

CREATE INDEX idx_idempotency_keys_expires_at
    ON idempotency_keys (expires_at);
