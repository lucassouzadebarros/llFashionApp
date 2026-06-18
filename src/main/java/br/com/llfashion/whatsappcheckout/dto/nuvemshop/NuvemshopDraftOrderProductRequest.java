package br.com.llfashion.whatsappcheckout.dto.nuvemshop;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NuvemshopDraftOrderProductRequest(
        @JsonProperty("variant_id")
        Long variantId,
        Integer quantity
) {
}
