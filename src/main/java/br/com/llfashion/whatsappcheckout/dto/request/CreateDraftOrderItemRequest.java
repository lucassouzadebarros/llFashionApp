package br.com.llfashion.whatsappcheckout.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateDraftOrderItemRequest(
        Long nuvemshopVariantId,
        String metaProductRetailerId,
        @NotNull(message = "quantity é obrigatório")
        @Positive(message = "quantity deve ser maior que zero")
        Integer quantity
) {
}
