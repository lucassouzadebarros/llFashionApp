ALTER TABLE whatsapp_order_item
    ADD COLUMN IF NOT EXISTS image_url VARCHAR(1000);

ALTER TABLE whatsapp_order
    ADD COLUMN IF NOT EXISTS pix_copy_paste TEXT,
    ADD COLUMN IF NOT EXISTS pix_qr_code_url TEXT;
