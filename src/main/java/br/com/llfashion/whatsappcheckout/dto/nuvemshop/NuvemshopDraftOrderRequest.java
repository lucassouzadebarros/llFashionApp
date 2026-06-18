package br.com.llfashion.whatsappcheckout.dto.nuvemshop;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record NuvemshopDraftOrderRequest(
        @JsonProperty("contact_name")
        String contactName,
        @JsonProperty("contact_lastname")
        String contactLastname,
        @JsonProperty("contact_email")
        String contactEmail,
        @JsonProperty("contact_phone")
        String contactPhone,
        @JsonProperty("cpf_cnpj")
        String cpfCnpj,
        @JsonProperty("payment_status")
        String paymentStatus,
        @JsonProperty("sale_channel")
        String saleChannel,
        String note,
        List<NuvemshopDraftOrderProductRequest> products,
        List<NuvemshopDraftOrderShippingRequest> shipping
) {
}
