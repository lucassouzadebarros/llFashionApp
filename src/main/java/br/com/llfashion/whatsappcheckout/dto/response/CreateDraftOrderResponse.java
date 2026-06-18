package br.com.llfashion.whatsappcheckout.dto.response;

import br.com.llfashion.whatsappcheckout.enums.OrderStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateDraftOrderResponse(
        UUID localOrderId,
        Long nuvemshopDraftOrderId,
        OrderStatus status,
        String checkoutUrl,
        String abandonedCheckoutUrl,
        String statusPublicToken,
        BigDecimal total,
        String message
) {
}
