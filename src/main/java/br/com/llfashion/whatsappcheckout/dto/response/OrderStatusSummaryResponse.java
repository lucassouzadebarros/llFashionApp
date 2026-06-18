package br.com.llfashion.whatsappcheckout.dto.response;

import br.com.llfashion.whatsappcheckout.enums.PublicOrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderStatusSummaryResponse(
        String statusPublicToken,
        UUID localOrderId,
        Long nuvemshopDraftOrderId,
        String nuvemshopOrderNumber,
        String publicOrderNumber,
        PublicOrderStatus publicStatus,
        String statusTitle,
        String paymentStatus,
        String shippingStatus,
        boolean canPay,
        String checkoutUrl,
        String statusUrl,
        BigDecimal total,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
