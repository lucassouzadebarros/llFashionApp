package br.com.llfashion.whatsappcheckout.dto.request;

import jakarta.validation.constraints.NotBlank;

public record WhatsAppStartLinkRequest(
        @NotBlank(message = "phone e obrigatorio")
        String phone,
        String customerName
) {
}
