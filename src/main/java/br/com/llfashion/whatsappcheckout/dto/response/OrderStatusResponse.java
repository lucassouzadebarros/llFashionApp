package br.com.llfashion.whatsappcheckout.dto.response;

import br.com.llfashion.whatsappcheckout.enums.PublicOrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderStatusResponse(
        String statusPublicToken,
        UUID localOrderId,
        Long nuvemshopDraftOrderId,
        String nuvemshopOrderNumber,
        PublicOrderStatus publicStatus,
        String statusTitle,
        String statusMessage,
        String paymentStatus,
        String shippingStatus,
        boolean canPay,
        String checkoutUrl,
        String pixCopyPaste,
        String pixQrCodeUrl,
        String shippingMethod,
        String shippingEta,
        String shippingTrackingNumber,
        String shippingTrackingUrl,
        BigDecimal total,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<OrderStatusItemResponse> items
) {
}
