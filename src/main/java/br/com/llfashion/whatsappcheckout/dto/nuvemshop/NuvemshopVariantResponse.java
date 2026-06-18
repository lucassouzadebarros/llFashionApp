package br.com.llfashion.whatsappcheckout.dto.nuvemshop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NuvemshopVariantResponse(
        Long id,
        @JsonProperty("product_id")
        Long productId,
        @JsonProperty("image_id")
        Long imageId,
        String sku,
        BigDecimal price,
        Integer stock,
        JsonNode values
) {
}
