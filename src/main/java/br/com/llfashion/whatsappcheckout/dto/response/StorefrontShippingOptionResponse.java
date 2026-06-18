package br.com.llfashion.whatsappcheckout.dto.response;

import java.math.BigDecimal;

public record StorefrontShippingOptionResponse(
        String code,
        String name,
        String description,
        String eta,
        BigDecimal price,
        Boolean requiresHumanAttendant
) {
}
