CREATE TABLE storefront_cart (
    id UUID PRIMARY KEY,
    cart_token VARCHAR(80) NOT NULL UNIQUE,
    phone_number VARCHAR(50),
    customer_name VARCHAR(255),
    customer_lastname VARCHAR(255),
    customer_document VARCHAR(30),
    customer_email VARCHAR(255),
    customer_phone VARCHAR(50),
    postal_code VARCHAR(20),
    address_street VARCHAR(500),
    address_number VARCHAR(50),
    address_complement VARCHAR(255),
    address_neighborhood VARCHAR(255),
    address_city VARCHAR(255),
    address_state VARCHAR(100),
    status VARCHAR(100) NOT NULL,
    subtotal NUMERIC(10,2) NOT NULL,
    shipping_price NUMERIC(10,2),
    total NUMERIC(10,2) NOT NULL,
    minimum_order_value NUMERIC(10,2) NOT NULL,
    selected_shipping_code VARCHAR(255),
    selected_shipping_name VARCHAR(255),
    selected_shipping_eta VARCHAR(255),
    checkout_url TEXT,
    local_order_id UUID,
    nuvemshop_draft_order_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_storefront_cart_token ON storefront_cart (cart_token);
CREATE INDEX idx_storefront_cart_phone_status ON storefront_cart (phone_number, status, updated_at);
CREATE INDEX idx_storefront_cart_expires_at ON storefront_cart (expires_at);

CREATE TABLE storefront_cart_item (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL,
    product_mapping_id UUID,
    nuvemshop_product_id BIGINT NOT NULL,
    nuvemshop_variant_id BIGINT NOT NULL,
    product_name VARCHAR(500) NOT NULL,
    variant_name VARCHAR(500),
    size VARCHAR(100),
    color VARCHAR(100),
    model VARCHAR(100),
    image_url VARCHAR(1000),
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(10,2) NOT NULL,
    total_price NUMERIC(10,2) NOT NULL,
    stock_at_selection INTEGER,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_storefront_cart_item_cart
        FOREIGN KEY (cart_id) REFERENCES storefront_cart(id),
    CONSTRAINT fk_storefront_cart_item_product_mapping
        FOREIGN KEY (product_mapping_id) REFERENCES product_mapping(id)
);

CREATE INDEX idx_storefront_cart_item_cart ON storefront_cart_item (cart_id);
CREATE INDEX idx_storefront_cart_item_variant ON storefront_cart_item (nuvemshop_variant_id);
