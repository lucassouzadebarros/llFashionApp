package br.com.llfashion.whatsappcheckout.dto.response;

import java.util.List;

public record WhatsAppWebhookResponse(
        int ordersCreated,
        List<CreateDraftOrderResponse> orders,
        String message
) {
}
