package br.com.llfashion.whatsappcheckout.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record WhatsAppButtonMessageRequest(
        @JsonProperty("messaging_product")
        String messagingProduct,

        @JsonProperty("recipient_type")
        String recipientType,

        String to,

        String type,

        Interactive interactive
) {

    public static WhatsAppButtonMessageRequest of(String to, String body, List<ButtonOption> options) {
        List<Button> buttons = options.stream()
                .map(option -> new Button("reply", new Reply(option.id(), option.title())))
                .toList();

        return new WhatsAppButtonMessageRequest(
                "whatsapp",
                "individual",
                to,
                "interactive",
                new Interactive(
                        "button",
                        new Body(body),
                        new Action(buttons)
                )
        );
    }

    public record ButtonOption(String id, String title) {
    }

    public record Interactive(
            String type,
            Body body,
            Action action
    ) {
    }

    public record Body(String text) {
    }

    public record Action(List<Button> buttons) {
    }

    public record Button(String type, Reply reply) {
    }

    public record Reply(String id, String title) {
    }
}
