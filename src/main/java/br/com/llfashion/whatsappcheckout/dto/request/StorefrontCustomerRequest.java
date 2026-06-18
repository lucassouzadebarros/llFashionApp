package br.com.llfashion.whatsappcheckout.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record StorefrontCustomerRequest(
        @NotBlank(message = "fullName e obrigatorio")
        String fullName,
        @NotBlank(message = "cpfCnpj e obrigatorio")
        String cpfCnpj,
        @NotBlank(message = "email e obrigatorio")
        @Email(message = "email invalido")
        String email,
        @NotBlank(message = "phone e obrigatorio")
        String phone
) {
}
