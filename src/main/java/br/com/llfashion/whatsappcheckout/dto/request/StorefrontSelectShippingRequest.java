package br.com.llfashion.whatsappcheckout.dto.request;

import jakarta.validation.constraints.NotBlank;

public record StorefrontSelectShippingRequest(
        @NotBlank(message = "shippingCode e obrigatorio")
        String shippingCode
) {
}
