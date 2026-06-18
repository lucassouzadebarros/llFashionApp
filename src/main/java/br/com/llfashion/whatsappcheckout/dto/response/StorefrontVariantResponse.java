package br.com.llfashion.whatsappcheckout.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record StorefrontVariantResponse(
        UUID productMappingId,
        Long nuvemshopProductId,
        Long nuvemshopVariantId,
        String sku,
        String variantName,
        String color,
        String size,
        String model,
        String imageUrl,
        BigDecimal price,
        Integer stock,
        Boolean available
) {
}
