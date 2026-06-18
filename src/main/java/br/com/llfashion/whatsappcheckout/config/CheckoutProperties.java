package br.com.llfashion.whatsappcheckout.config;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "checkout")
public record CheckoutProperties(
        BigDecimal minimumOrderTotal,
        String frontendBaseUrl,
        Integer cartExpirationMinutes
) {

    private static final BigDecimal DEFAULT_MINIMUM_ORDER_TOTAL = new BigDecimal("200.00");
    private static final int DEFAULT_CART_EXPIRATION_MINUTES = 120;

    public BigDecimal resolvedMinimumOrderTotal() {
        return minimumOrderTotal == null ? DEFAULT_MINIMUM_ORDER_TOTAL : minimumOrderTotal;
    }

    public String resolvedFrontendBaseUrl() {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            return "http://localhost:8080/storefront/";
        }
        return frontendBaseUrl.trim();
    }

    public Duration resolvedCartExpiration() {
        int minutes = cartExpirationMinutes == null || cartExpirationMinutes <= 0
                ? DEFAULT_CART_EXPIRATION_MINUTES
                : cartExpirationMinutes;
        return Duration.ofMinutes(minutes);
    }
}
