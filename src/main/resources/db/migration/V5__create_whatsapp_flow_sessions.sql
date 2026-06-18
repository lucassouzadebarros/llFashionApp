CREATE TABLE whatsapp_flow_session (
    id UUID PRIMARY KEY,
    flow_token VARCHAR(255) NOT NULL UNIQUE,
    customer_phone VARCHAR(50) NOT NULL,
    customer_name VARCHAR(255),
    whatsapp_message_id VARCHAR(255),
    product_retailer_id VARCHAR(255),
    nuvemshop_product_id BIGINT,
    nuvemshop_variant_id BIGINT,
    quantity INTEGER,
    subtotal NUMERIC(10,2),
    status VARCHAR(100) NOT NULL,
    local_order_id UUID,
    checkout_url TEXT,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_whatsapp_flow_session_phone
    ON whatsapp_flow_session (customer_phone, updated_at);

CREATE INDEX idx_whatsapp_flow_session_status
    ON whatsapp_flow_session (status);
