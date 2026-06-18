package br.com.llfashion.whatsappcheckout.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nuvemshop")
public record NuvemshopProperties(
        String appId,
        String clientSecret,
        String tokenUrl,
        String authorizeUrl,
        String apiBaseUrl,
        String userAgent
) {
}
