package br.com.llfashion.whatsappcheckout.dto.response;

public record StorefrontSessionResponse(
        String cartToken,
        StorefrontCartResponse cart
) {
}
