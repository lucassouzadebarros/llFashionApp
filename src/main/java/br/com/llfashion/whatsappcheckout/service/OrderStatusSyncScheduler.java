package br.com.llfashion.whatsappcheckout.service;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderStatusSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusSyncScheduler.class);

    private final boolean enabled;
    private final int lookbackDays;
    private final OrderTrackingService orderTrackingService;

    public OrderStatusSyncScheduler(
            @Value("${order-status-sync.enabled:true}") boolean enabled,
            @Value("${order-status-sync.lookback-days:30}") int lookbackDays,
            OrderTrackingService orderTrackingService
    ) {
        this.enabled = enabled;
        this.lookbackDays = lookbackDays;
        this.orderTrackingService = orderTrackingService;
    }

    @Scheduled(initialDelayString = "2", fixedDelayString = "${order-status-sync.interval-minutes:5}", timeUnit = TimeUnit.MINUTES)
    public void syncOrderStatuses() {
        if (!enabled) {
            return;
        }
        try {
            int total = orderTrackingService.syncRecentOrderStatuses(lookbackDays);
            log.info("Sincronizacao de status de pedidos concluida. pedidosVerificados={}", total);
        } catch (Exception exception) {
            log.warn("Nao foi possivel sincronizar status dos pedidos automaticamente: {}", exception.getMessage());
        }
    }
}
