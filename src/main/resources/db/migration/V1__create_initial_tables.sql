CREATE TABLE nuvemshop_installation (
    id UUID PRIMARY KEY,
    app_id VARCHAR(50) NOT NULL,
    store_id BIGINT NOT NULL,
    access_token TEXT NOT NULL,
    token_type VARCHAR(50),
    scope VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_nuvemshop_installation_store_id
    ON nuvemshop_installation (store_id);

CREATE TABLE product_mapping (
    id UUID PRIMARY KEY,
    nuvemshop_product_id BIGINT NOT NULL,
    nuvemshop_variant_id BIGINT NOT NULL,
    sku VARCHAR(255),
    meta_product_retailer_id VARCHAR(255),
    product_name VARCHAR(500) NOT NULL,
    variant_name VARCHAR(500),
    price NUMERIC(10,2),
    stock INTEGER,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_product_mapping_nuvemshop_variant_id
    ON product_mapping (nuvemshop_variant_id);

CREATE INDEX idx_product_mapping_meta_product_retailer_id
    ON product_mapping (meta_product_retailer_id);

CREATE TABLE whatsapp_order (
    id UUID PRIMARY KEY,
    customer_name VARCHAR(255) NOT NULL,
    customer_lastname VARCHAR(255) NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    customer_phone VARCHAR(50) NOT NULL,
    source VARCHAR(100) NOT NULL,
    status VARCHAR(100) NOT NULL,
    nuvemshop_draft_order_id BIGINT,
    checkout_url TEXT,
    abandoned_checkout_url TEXT,
    total NUMERIC(10,2),
    raw_nuvemshop_response TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE whatsapp_order_item (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    product_mapping_id UUID,
    nuvemshop_product_id BIGINT NOT NULL,
    nuvemshop_variant_id BIGINT NOT NULL,
    product_name VARCHAR(500) NOT NULL,
    variant_name VARCHAR(500),
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(10,2),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_whatsapp_order_item_order
        FOREIGN KEY (order_id) REFERENCES whatsapp_order (id),
    CONSTRAINT fk_whatsapp_order_item_product_mapping
        FOREIGN KEY (product_mapping_id) REFERENCES product_mapping (id)
);

CREATE TABLE webhook_event_log (
    id UUID PRIMARY KEY,
    source VARCHAR(100) NOT NULL,
    external_event_id VARCHAR(255),
    event_type VARCHAR(255),
    payload TEXT NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);
