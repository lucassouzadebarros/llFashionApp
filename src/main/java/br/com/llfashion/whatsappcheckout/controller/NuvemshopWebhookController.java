package br.com.llfashion.whatsappcheckout.controller;

import br.com.llfashion.whatsappcheckout.service.OrderTrackingService;
import br.com.llfashion.whatsappcheckout.service.ProductSyncService;
import br.com.llfashion.whatsappcheckout.service.NuvemshopSiteOrderSyncService;
import br.com.llfashion.whatsappcheckout.service.WebhookEventLogService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/nuvemshop")
public class NuvemshopWebhookController {

    private static final Logger log = LoggerFactory.getLogger(NuvemshopWebhookController.class);

    private final WebhookEventLogService webhookEventLogService;
    private final OrderTrackingService orderTrackingService;
    private final ProductSyncService productSyncService;
    private final NuvemshopSiteOrderSyncService siteOrderSyncService;

    public NuvemshopWebhookController(
            WebhookEventLogService webhookEventLogService,
            OrderTrackingService orderTrackingService,
            ProductSyncService productSyncService,
            NuvemshopSiteOrderSyncService siteOrderSyncService
    ) {
        this.webhookEventLogService = webhookEventLogService;
        this.orderTrackingService = orderTrackingService;
        this.productSyncService = productSyncService;
        this.siteOrderSyncService = siteOrderSyncService;
    }

    @PostMapping("/orders")
    public ResponseEntity<Void> receiveOrderWebhook(@RequestBody JsonNode payload) {
        webhookEventLogService.saveNuvemshopOrderWebhook(payload);
        orderTrackingService.updateFromNuvemshopWebhook(payload);
        syncSiteOrderFromWebhook(payload);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/order")
    public ResponseEntity<Void> receiveOrderWebhookAlias(@RequestBody JsonNode payload) {
        webhookEventLogService.saveNuvemshopOrderWebhook(payload);
        orderTrackingService.updateFromNuvemshopWebhook(payload);
        syncSiteOrderFromWebhook(payload);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/fulfillment")
    public ResponseEntity<Void> receiveFulfillmentWebhook(@RequestBody JsonNode payload) {
        webhookEventLogService.saveNuvemshopOrderWebhook(payload);
        orderTrackingService.updateFromNuvemshopWebhook(payload);
        syncSiteOrderFromWebhook(payload);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/products")
    public ResponseEntity<Void> receiveProductWebhook(@RequestBody JsonNode payload) {
        syncProductFromWebhook(payload, "product");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/product")
    public ResponseEntity<Void> receiveProductWebhookAlias(@RequestBody JsonNode payload) {
        syncProductFromWebhook(payload, "product");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stock")
    public ResponseEntity<Void> receiveStockWebhook(@RequestBody JsonNode payload) {
        syncProductFromWebhook(payload, "stock");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/store-redact")
    public ResponseEntity<Void> receiveStoreRedactWebhook(@RequestBody JsonNode payload) {
        webhookEventLogService.saveNuvemshopPrivacyWebhook(payload, "store_redact");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/customers-redact")
    public ResponseEntity<Void> receiveCustomersRedactWebhook(@RequestBody JsonNode payload) {
        webhookEventLogService.saveNuvemshopPrivacyWebhook(payload, "customers_redact");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/customers-data-request")
    public ResponseEntity<Void> receiveCustomersDataRequestWebhook(@RequestBody JsonNode payload) {
        webhookEventLogService.saveNuvemshopPrivacyWebhook(payload, "customers_data_request");
        return ResponseEntity.ok().build();
    }

    private void syncProductFromWebhook(JsonNode payload, String eventType) {
        webhookEventLogService.saveNuvemshopWebhook(payload, eventType);
        Long productId = firstLong(payload, "product_id", "product", "id");
        if (productId == null) {
            log.warn("Webhook Nuvemshop de produto/estoque sem product_id. eventType={}", eventType);
            return;
        }
        try {
            int variants = productSyncService.syncProductById(productId);
            log.info("Produto sincronizado por webhook Nuvemshop. productId={}, variants={}", productId, variants);
        } catch (Exception exception) {
            log.warn("Nao foi possivel sincronizar produto por webhook Nuvemshop. productId={}, erro={}",
                    productId,
                    exception.getMessage());
        }
    }

    private void syncSiteOrderFromWebhook(JsonNode payload) {
        try {
            boolean synced = siteOrderSyncService.syncOrderFromWebhook(payload);
            if (synced) {
                log.info("Pedido da loja sincronizado por webhook Nuvemshop.");
            }
        } catch (Exception exception) {
            log.warn("Nao foi possivel sincronizar pedido da loja por webhook Nuvemshop. erro={}", exception.getMessage());
        }
    }

    private Long firstLong(JsonNode payload, String... fieldNames) {
        if (payload == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode node = payload.findValue(fieldName);
            if (node == null || node.isNull()) {
                continue;
            }
            if (node.canConvertToLong()) {
                return node.asLong();
            }
            if (!node.asText("").isBlank()) {
                try {
                    return Long.valueOf(node.asText().trim());
                } catch (NumberFormatException ignored) {
                    continue;
                }
            }
        }
        return null;
    }
}
