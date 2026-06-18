package br.com.llfashion.whatsappcheckout.dto.nuvemshop;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NuvemshopDraftOrderShippingRequest(
        String cost,
        @JsonProperty("shipping_address")
        NuvemshopDraftOrderShippingAddressRequest shippingAddress
) {
}
