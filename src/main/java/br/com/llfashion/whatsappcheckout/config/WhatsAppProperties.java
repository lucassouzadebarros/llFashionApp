package br.com.llfashion.whatsappcheckout.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "whatsapp")
public record WhatsAppProperties(
        String verifyToken,
        String accessToken,
        String phoneNumberId,
        String catalogId,
        String apiBaseUrl,
        Flows flows
) {

    public record Flows(
            Boolean enabled,
            String flowId,
            String flowCta,
            String mode,
            String privateKey,
            String privateKeyPath
    ) {
        public boolean resolvedEnabled() {
            return Boolean.TRUE.equals(enabled);
        }
    }
}
