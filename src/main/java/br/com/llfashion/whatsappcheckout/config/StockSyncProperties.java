package br.com.llfashion.whatsappcheckout.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stock-sync")
public record StockSyncProperties(
        boolean enabled,
        Long intervalMinutes
) {
}
