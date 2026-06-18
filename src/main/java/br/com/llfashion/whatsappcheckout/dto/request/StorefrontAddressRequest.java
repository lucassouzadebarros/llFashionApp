package br.com.llfashion.whatsappcheckout.dto.request;

import jakarta.validation.constraints.NotBlank;

public record StorefrontAddressRequest(
        @NotBlank(message = "postalCode e obrigatorio")
        String postalCode,
        @NotBlank(message = "street e obrigatorio")
        String street,
        @NotBlank(message = "number e obrigatorio")
        String number,
        String complement,
        @NotBlank(message = "neighborhood e obrigatorio")
        String neighborhood,
        @NotBlank(message = "city e obrigatorio")
        String city,
        @NotBlank(message = "state e obrigatorio")
        String state
) {
}
