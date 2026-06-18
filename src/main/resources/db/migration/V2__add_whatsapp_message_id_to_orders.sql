ALTER TABLE whatsapp_order
    ADD COLUMN whatsapp_message_id VARCHAR(255);

CREATE UNIQUE INDEX uk_whatsapp_order_message_id
    ON whatsapp_order (whatsapp_message_id)
    WHERE whatsapp_message_id IS NOT NULL;
