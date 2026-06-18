package br.com.llfashion.whatsappcheckout.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record WhatsAppCartMockRequest(
        @NotBlank(message = "customerName é obrigatório")
        String customerName,
        @NotBlank(message = "customerPhone é obrigatório")
        String customerPhone,
        @NotEmpty(message = "items é obrigatório e não pode ser vazio")
        List<@Valid WhatsAppCartMockItemRequest> items
) {
}
