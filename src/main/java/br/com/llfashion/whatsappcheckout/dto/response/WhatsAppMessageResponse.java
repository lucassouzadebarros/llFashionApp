package br.com.llfashion.whatsappcheckout.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppMessageResponse(
        @JsonProperty("messaging_product")
        String messagingProduct,

        List<Contact> contacts,

        List<Message> messages
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contact(
            String input,

            @JsonProperty("wa_id")
            String waId
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String id,

            @JsonProperty("message_status")
            String messageStatus
    ) {
    }
}
