package br.com.llfashion.whatsappcheckout.dto.response;

import java.math.BigDecimal;

public record DraftOrderSummaryResponse(
        Long id,
        String status,
        String paymentStatus,
        String checkoutUrl,
        BigDecimal total
) {
}
