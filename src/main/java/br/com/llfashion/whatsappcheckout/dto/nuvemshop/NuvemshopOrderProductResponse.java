package br.com.llfashion.whatsappcheckout.dto.nuvemshop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NuvemshopOrderProductResponse(
        Long id,
        @JsonProperty("product_id")
        Long productId,
        @JsonProperty("variant_id")
        Long variantId,
        JsonNode name,
        @JsonProperty("variant_name")
        JsonNode variantName,
        Integer quantity,
        BigDecimal price,
        @JsonProperty("image_url")
        String imageUrl,
        JsonNode image
) {
}
