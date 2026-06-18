package br.com.llfashion.whatsappcheckout.dto.response;

import br.com.llfashion.whatsappcheckout.enums.StorefrontCartStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StorefrontCartResponse(
        String cartToken,
        StorefrontCartStatus status,
        BigDecimal subtotal,
        BigDecimal shippingPrice,
        BigDecimal total,
        BigDecimal minimumOrderValue,
        BigDecimal missingAmount,
        Integer progressPercent,
        Boolean canCheckout,
        String selectedShippingCode,
        String selectedShippingName,
        String selectedShippingEta,
        String checkoutUrl,
        String statusPublicToken,
        String statusUrl,
        String customerName,
        String customerEmail,
        String customerPhone,
        String postalCode,
        String addressStreet,
        String addressNumber,
        String addressComplement,
        String addressNeighborhood,
        String addressCity,
        String addressState,
        LocalDateTime expiresAt,
        List<StorefrontCartItemResponse> items
) {
}
