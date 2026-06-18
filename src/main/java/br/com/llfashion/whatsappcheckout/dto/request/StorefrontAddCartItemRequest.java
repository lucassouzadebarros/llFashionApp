package br.com.llfashion.whatsappcheckout.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StorefrontAddCartItemRequest(
        @NotNull(message = "nuvemshopVariantId e obrigatorio")
        Long nuvemshopVariantId,
        @NotNull(message = "quantity e obrigatorio")
        @Positive(message = "quantity deve ser maior que zero")
        Integer quantity
) {
}
