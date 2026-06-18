package br.com.llfashion.whatsappcheckout.dto.nuvemshop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NuvemshopDraftOrderProductResponse(
        Long id,
        @JsonProperty("product_id")
        Long productId,
        @JsonProperty("variant_id")
        Long variantId,
        String name,
        Integer quantity,
        BigDecimal price
) {
}
