package br.com.llfashion.whatsappcheckout.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record StorefrontProductDetailResponse(
        Long nuvemshopProductId,
        String productName,
        String imageUrl,
        BigDecimal startingPrice,
        Integer totalStock,
        String categoryId,
        List<StorefrontVariantResponse> variants
) {
}
