package br.com.llfashion.whatsappcheckout.dto.nuvemshop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NuvemshopProductResponse(
        Long id,
        JsonNode name,
        List<NuvemshopProductImageResponse> images,
        List<NuvemshopVariantResponse> variants
) {
}
