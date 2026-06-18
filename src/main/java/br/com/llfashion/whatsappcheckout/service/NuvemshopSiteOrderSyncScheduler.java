package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.dto.response.NuvemshopOrderImportResponse;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NuvemshopSiteOrderSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(NuvemshopSiteOrderSyncScheduler.class);

    private final boolean enabled;
    private final int lookbackDays;
    private final NuvemshopSiteOrderSyncService orderSyncService;

    public NuvemshopSiteOrderSyncScheduler(
            @Value("${nuvemshop-site-orders-sync.enabled:true}") boolean enabled,
            @Value("${nuvemshop-site-orders-sync.lookback-days:7}") int lookbackDays,
            NuvemshopSiteOrderSyncService orderSyncService
    ) {
        this.enabled = enabled;
        this.lookbackDays = lookbackDays;
        this.orderSyncService = orderSyncService;
    }

    @Scheduled(initialDelayString = "3", fixedDelayString = "${nuvemshop-site-orders-sync.interval-minutes:10}", timeUnit = TimeUnit.MINUTES)
    public void syncSiteOrders() {
        if (!enabled) {
            return;
        }
        try {
            NuvemshopOrderImportResponse response = orderSyncService.syncRecentlyUpdatedOrders(lookbackDays);
            log.info("Sincronizacao de pedidos feitos no site concluida. lidos={}, importadosOuAtualizados={}, ignorados={}",
                    response.totalOrdersRead(),
                    response.totalOrdersImportedOrUpdated(),
                    response.totalOrdersSkipped());
        } catch (Exception exception) {
            log.warn("Nao foi possivel sincronizar pedidos feitos no site: {}", exception.getMessage());
        }
    }
}
