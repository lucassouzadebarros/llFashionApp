package br.com.llfashion.whatsappcheckout.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StorefrontAddCartItemRequest(
        @NotNull(message = "nuvemshopVariantId é obrigatório")
        Long nuvemshopVariantId,
        @NotNull(message = "quantity é obrigatório")
        @Positive(message = "quantity deve ser maior que zero")
        Integer quantity
) {
}
