package br.com.llfashion.whatsappcheckout.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record StorefrontCustomerRequest(
        @NotBlank(message = "fullName é obrigatório")
        String fullName,
        @NotBlank(message = "cpfCnpj é obrigatório")
        String cpfCnpj,
        @NotBlank(message = "email é obrigatório")
        @Email(message = "email inválido")
        String email,
        @NotBlank(message = "phone é obrigatório")
        String phone
) {
}
