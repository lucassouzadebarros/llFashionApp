package br.com.llfashion.whatsappcheckout.dto.request;

import jakarta.validation.constraints.NotBlank;

public record StorefrontAddressRequest(
        @NotBlank(message = "postalCode é obrigatório")
        String postalCode,
        @NotBlank(message = "street é obrigatório")
        String street,
        @NotBlank(message = "number é obrigatório")
        String number,
        String complement,
        @NotBlank(message = "neighborhood é obrigatório")
        String neighborhood,
        @NotBlank(message = "city é obrigatório")
        String city,
        @NotBlank(message = "state é obrigatório")
        String state
) {
}
