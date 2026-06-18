package br.com.llfashion.whatsappcheckout.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record StorefrontCartItemResponse(
        UUID id,
        UUID productMappingId,
        Long nuvemshopProductId,
        Long nuvemshopVariantId,
        String productName,
        String variantName,
        String size,
        String color,
        String model,
        String imageUrl,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        Integer stockAvailable
) {
}
