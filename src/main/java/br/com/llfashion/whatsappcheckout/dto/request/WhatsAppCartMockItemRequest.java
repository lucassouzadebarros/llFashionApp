package br.com.llfashion.whatsappcheckout.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WhatsAppCartMockItemRequest(
        @NotBlank(message = "productRetailerId é obrigatório")
        String productRetailerId,
        @NotNull(message = "quantity é obrigatório")
        @Positive(message = "quantity deve ser maior que zero")
        Integer quantity
) {
}
