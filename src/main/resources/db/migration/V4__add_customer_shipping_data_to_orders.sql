ALTER TABLE whatsapp_checkout_session
    ADD COLUMN IF NOT EXISTS customer_document VARCHAR(30),
    ADD COLUMN IF NOT EXISTS address_street VARCHAR(500),
    ADD COLUMN IF NOT EXISTS address_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS address_complement VARCHAR(255),
    ADD COLUMN IF NOT EXISTS address_neighborhood VARCHAR(255),
    ADD COLUMN IF NOT EXISTS address_city VARCHAR(255),
    ADD COLUMN IF NOT EXISTS address_state VARCHAR(100);

ALTER TABLE whatsapp_order
    ADD COLUMN customer_document VARCHAR(30),
    ADD COLUMN shipping_postal_code VARCHAR(20),
    ADD COLUMN shipping_street VARCHAR(500),
    ADD COLUMN shipping_number VARCHAR(50),
    ADD COLUMN shipping_complement VARCHAR(255),
    ADD COLUMN shipping_neighborhood VARCHAR(255),
    ADD COLUMN shipping_city VARCHAR(255),
    ADD COLUMN shipping_state VARCHAR(100);
