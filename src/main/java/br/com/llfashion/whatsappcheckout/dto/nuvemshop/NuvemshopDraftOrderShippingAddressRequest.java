package br.com.llfashion.whatsappcheckout.dto.nuvemshop;

public record NuvemshopDraftOrderShippingAddressRequest(
        String address,
        String number,
        String floor,
        String locality,
        String city,
        String province,
        String zipcode
) {
}
