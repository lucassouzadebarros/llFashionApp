package br.com.llfashion.whatsappcheckout.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateDraftOrderRequest(
        @NotBlank(message = "customerName é obrigatório")
        String customerName,
        String customerLastname,
        String customerEmail,
        @NotBlank(message = "customerPhone é obrigatório")
        String customerPhone,
        @NotEmpty(message = "items é obrigatório e não pode ser vazio")
        List<@Valid CreateDraftOrderItemRequest> items,
        String cpfCnpj,
        String shippingPostalCode,
        String shippingStreet,
        String shippingNumber,
        String shippingComplement,
        String shippingNeighborhood,
        String shippingCity,
        String shippingState
) {

    public CreateDraftOrderRequest(
            String customerName,
            String customerLastname,
            String customerEmail,
            String customerPhone,
            List<@Valid CreateDraftOrderItemRequest> items
    ) {
        this(
                customerName,
                customerLastname,
                customerEmail,
                customerPhone,
                items,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
