package br.com.llfashion.whatsappcheckout.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WhatsAppCtaUrlMessageRequest(
        @JsonProperty("messaging_product")
        String messagingProduct,

        @JsonProperty("recipient_type")
        String recipientType,

        String to,

        String type,

        Interactive interactive
) {

    public static WhatsAppCtaUrlMessageRequest of(String to, String body, String buttonText, String url) {
        return new WhatsAppCtaUrlMessageRequest(
                "whatsapp",
                "individual",
                to,
                "interactive",
                new Interactive(
                        "cta_url",
                        new Body(body),
                        new Action(
                                "cta_url",
                                new Parameters(buttonText, url)
                        )
                )
        );
    }

    public record Interactive(
            String type,
            Body body,
            Action action
    ) {
    }

    public record Body(String text) {
    }

    public record Action(
            String name,
            Parameters parameters
    ) {
    }

    public record Parameters(
            @JsonProperty("display_text")
            String displayText,
            String url
    ) {
    }
}
