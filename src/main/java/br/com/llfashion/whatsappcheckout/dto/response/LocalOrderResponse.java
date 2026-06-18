package br.com.llfashion.whatsappcheckout.dto.response;

import br.com.llfashion.whatsappcheckout.enums.OrderStatus;
import br.com.llfashion.whatsappcheckout.enums.WebhookSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record LocalOrderResponse(
        UUID id,
        String customerName,
        String customerLastname,
        String customerEmail,
        String customerPhone,
        String customerDocument,
        String shippingPostalCode,
        String shippingStreet,
        String shippingNumber,
        String shippingComplement,
        String shippingNeighborhood,
        String shippingCity,
        String shippingState,
        WebhookSource source,
        OrderStatus status,
        Long nuvemshopDraftOrderId,
        String checkoutUrl,
        String abandonedCheckoutUrl,
        BigDecimal total,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<LocalOrderItemResponse> items
) {
}
