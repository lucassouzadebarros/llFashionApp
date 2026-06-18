package br.com.llfashion.whatsappcheckout.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WhatsAppTextMessageRequest(
        @JsonProperty("messaging_product")
        String messagingProduct,

        @JsonProperty("recipient_type")
        String recipientType,

        String to,

        String type,

        Text text
) {

    public static WhatsAppTextMessageRequest of(String to, String body) {
        return new WhatsAppTextMessageRequest(
                "whatsapp",
                "individual",
                to,
                "text",
                new Text(true, body)
        );
    }

    public record Text(
            @JsonProperty("preview_url")
            boolean previewUrl,

            String body
    ) {
    }
}
