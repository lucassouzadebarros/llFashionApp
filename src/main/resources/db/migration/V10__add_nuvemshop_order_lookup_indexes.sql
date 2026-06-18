CREATE INDEX IF NOT EXISTS idx_whatsapp_order_nuvemshop_external_id
    ON whatsapp_order (nuvemshop_draft_order_id);

CREATE INDEX IF NOT EXISTS idx_whatsapp_order_source_created_at
    ON whatsapp_order (source, created_at);
