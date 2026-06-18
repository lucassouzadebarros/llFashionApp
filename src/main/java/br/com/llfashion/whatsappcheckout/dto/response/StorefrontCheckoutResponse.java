package br.com.llfashion.whatsappcheckout.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record StorefrontCheckoutResponse(
        String cartToken,
        UUID localOrderId,
        Long nuvemshopDraftOrderId,
        String checkoutUrl,
        String pixCopyPaste,
        String pixQrCodeUrl,
        String statusPublicToken,
        String statusUrl,
        BigDecimal total,
        String message
) {
}
