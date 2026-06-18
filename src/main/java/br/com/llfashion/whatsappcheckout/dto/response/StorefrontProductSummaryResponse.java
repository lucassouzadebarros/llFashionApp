package br.com.llfashion.whatsappcheckout.dto.response;

import java.math.BigDecimal;

public record StorefrontProductSummaryResponse(
        Long nuvemshopProductId,
        String productName,
        String imageUrl,
        BigDecimal startingPrice,
        Integer totalStock,
        Integer variantsCount,
        Boolean available
) {
}
