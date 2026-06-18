CREATE TABLE whatsapp_checkout_session (
    id UUID PRIMARY KEY,
    whatsapp_message_id VARCHAR(255),
    customer_name VARCHAR(255) NOT NULL,
    customer_lastname VARCHAR(255) NOT NULL,
    customer_email VARCHAR(255),
    customer_phone VARCHAR(50) NOT NULL,
    postal_code VARCHAR(20),
    status VARCHAR(100) NOT NULL,
    subtotal NUMERIC(10,2) NOT NULL,
    minimum_order_total NUMERIC(10,2) NOT NULL,
    local_order_id UUID,
    checkout_url TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_whatsapp_checkout_session_message_id
    ON whatsapp_checkout_session (whatsapp_message_id)
    WHERE whatsapp_message_id IS NOT NULL;

CREATE INDEX idx_whatsapp_checkout_session_phone_status
    ON whatsapp_checkout_session (customer_phone, status, updated_at);

CREATE TABLE whatsapp_checkout_session_item (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    product_mapping_id UUID,
    nuvemshop_product_id BIGINT NOT NULL,
    nuvemshop_variant_id BIGINT NOT NULL,
    product_name VARCHAR(500) NOT NULL,
    variant_name VARCHAR(500),
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(10,2),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_checkout_session_item_session
        FOREIGN KEY (session_id) REFERENCES whatsapp_checkout_session(id),
    CONSTRAINT fk_checkout_session_item_product_mapping
        FOREIGN KEY (product_mapping_id) REFERENCES product_mapping(id)
);

CREATE INDEX idx_whatsapp_checkout_session_item_session
    ON whatsapp_checkout_session_item (session_id);
