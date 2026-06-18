package br.com.llfashion.whatsappcheckout.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductMappingResponse(
        UUID id,
        Long nuvemshopProductId,
        Long nuvemshopVariantId,
        String sku,
        String metaProductRetailerId,
        String productName,
        String variantName,
        String imageUrl,
        BigDecimal price,
        Integer stock,
        Boolean active
) {
}
