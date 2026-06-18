package br.com.llfashion.whatsappcheckout.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

public record WhatsAppFlowMessageRequest(
        @JsonProperty("messaging_product")
        String messagingProduct,
        @JsonProperty("recipient_type")
        String recipientType,
        String to,
        String type,
        Interactive interactive
) {

    public static WhatsAppFlowMessageRequest of(
            String to,
            String body,
            String flowId,
            String flowToken,
            String cta,
            String mode,
            Map<String, Object> initialData
    ) {
        Map<String, Object> flowActionPayload = null;
        Parameters parameters = new Parameters(
                "3",
                flowId,
                flowToken,
                cta,
                "data_exchange",
                hasText(mode) ? mode : null,
                flowActionPayload
        );
        return new WhatsAppFlowMessageRequest(
                "whatsapp",
                "individual",
                to,
                "interactive",
                new Interactive(
                        "flow",
                        new Body(body),
                        new Action("flow", parameters)
                )
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Parameters(
            @JsonProperty("flow_message_version")
            String flowMessageVersion,
            @JsonProperty("flow_id")
            String flowId,
            @JsonProperty("flow_token")
            String flowToken,
            @JsonProperty("flow_cta")
            String flowCta,
            @JsonProperty("flow_action")
            String flowAction,
            String mode,
            @JsonProperty("flow_action_payload")
            Map<String, Object> flowActionPayload
    ) {
    }
}
