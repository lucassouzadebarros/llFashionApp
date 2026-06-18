package br.com.llfashion.whatsappcheckout.dto.response;

import java.math.BigDecimal;

public record OrderStatusItemResponse(
        String productName,
        String variantName,
        String imageUrl,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice
) {
}
