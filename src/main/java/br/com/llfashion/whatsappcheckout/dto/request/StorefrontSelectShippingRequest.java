package br.com.llfashion.whatsappcheckout.dto.request;

import jakarta.validation.constraints.NotBlank;

public record StorefrontSelectShippingRequest(
        @NotBlank(message = "shippingCode é obrigatório")
        String shippingCode
) {
}
