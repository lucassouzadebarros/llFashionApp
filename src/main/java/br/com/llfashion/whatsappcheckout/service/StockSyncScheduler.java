package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.config.StockSyncProperties;
import br.com.llfashion.whatsappcheckout.dto.response.ProductSyncResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
public class StockSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(StockSyncScheduler.class);

    private final StockSyncProperties properties;
    private final ProductSyncService productSyncService;

    public StockSyncScheduler(StockSyncProperties properties, ProductSyncService productSyncService) {
        this.properties = properties;
        this.productSyncService = productSyncService;
    }

    @Scheduled(initialDelayString = "1", fixedDelayString = "${stock-sync.interval-minutes:10}", timeUnit = TimeUnit.MINUTES)
    public void syncStock() {
        if (!properties.enabled()) {
            return;
        }
        try {
            ProductSyncResponse response = productSyncService.syncProducts();
            log.info("Sincronizacao de estoque concluida. produtos={}, variantes={}",
                    response.totalProductsRead(),
                    response.totalVariantsSynced());
        } catch (Exception exception) {
            log.warn("Nao foi possivel sincronizar estoque automaticamente: {}", exception.getMessage());
        }
    }
}
