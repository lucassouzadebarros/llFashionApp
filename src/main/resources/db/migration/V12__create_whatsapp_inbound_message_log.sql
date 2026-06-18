CREATE TABLE whatsapp_inbound_message_log (
    id UUID PRIMARY KEY,
    whatsapp_message_id VARCHAR(255) NOT NULL UNIQUE,
    customer_phone VARCHAR(50),
    message_type VARCHAR(50),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_whatsapp_inbound_message_log_customer_phone
    ON whatsapp_inbound_message_log (customer_phone);

CREATE INDEX idx_whatsapp_inbound_message_log_created_at
    ON whatsapp_inbound_message_log (created_at);
