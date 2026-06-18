package br.com.llfashion.whatsappcheckout.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record LocalOrderItemResponse(
        UUID id,
        UUID productMappingId,
        Long nuvemshopProductId,
        Long nuvemshopVariantId,
        String productName,
        String variantName,
        Integer quantity,
        BigDecimal unitPrice
) {
}
