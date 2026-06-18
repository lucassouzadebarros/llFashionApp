package br.com.llfashion.whatsappcheckout.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateMetaRetailerIdRequest(
        @NotBlank(message = "metaProductRetailerId é obrigatório")
        String metaProductRetailerId
) {
}
