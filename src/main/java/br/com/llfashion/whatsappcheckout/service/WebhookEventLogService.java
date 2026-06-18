package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.entity.WebhookEventLog;
import br.com.llfashion.whatsappcheckout.entity.WhatsappOrder;
import br.com.llfashion.whatsappcheckout.enums.OrderStatus;
import br.com.llfashion.whatsappcheckout.enums.WebhookSource;
import br.com.llfashion.whatsappcheckout.repository.WebhookEventLogRepository;
import br.com.llfashion.whatsappcheckout.repository.WhatsappOrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WebhookEventLogService {

    private final WebhookEventLogRepository webhookEventLogRepository;
    private final WhatsappOrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public WebhookEventLogService(
            WebhookEventLogRepository webhookEventLogRepository,
            WhatsappOrderRepository orderRepository,
            ObjectMapper objectMapper
    ) {
        this.webhookEventLogRepository = webhookEventLogRepository;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void saveNuvemshopOrderWebhook(JsonNode payload) {
        saveNuvemshopWebhook(payload, firstText(payload, "event", "topic", "type"));

        updateLocalOrderStatusIfPossible(payload);
    }

    @Transactional
    public void saveNuvemshopWebhook(JsonNode payload, String eventType) {
        webhookEventLogRepository.save(WebhookEventLog.builder()
                .source(WebhookSource.NUVEMSHOP)
                .externalEventId(firstText(payload, "event_id", "id", "order_id", "draft_order_id", "product_id"))
                .eventType(StringUtils.hasText(eventType) ? eventType : firstText(payload, "event", "topic", "type"))
                .payload(writePayload(payload))
                .processed(false)
                .build());
    }

    @Transactional
    public void saveNuvemshopPrivacyWebhook(JsonNode payload, String eventType) {
        webhookEventLogRepository.save(WebhookEventLog.builder()
                .source(WebhookSource.NUVEMSHOP)
                .externalEventId(firstText(payload, "event_id", "id", "store_id", "customer_id", "email"))
                .eventType(eventType)
                .payload(writePayload(payload))
                .processed(false)
                .build());
    }

    @Transactional
    public void saveWhatsAppWebhook(JsonNode payload, boolean processed) {
        webhookEventLogRepository.save(WebhookEventLog.builder()
                .source(WebhookSource.WHATSAPP)
                .externalEventId(firstWhatsAppMessageId(payload))
                .eventType(firstText(payload, "type", "field"))
                .payload(writePayload(payload))
                .processed(processed)
                .build());
    }

    private void updateLocalOrderStatusIfPossible(JsonNode payload) {
        Long draftOrderId = firstLong(payload, "draft_order_id", "order_id", "id");
        if (draftOrderId == null) {
            return;
        }

        Optional<WhatsappOrder> order = orderRepository.findByNuvemshopDraftOrderId(draftOrderId);
        order.ifPresent(whatsappOrder -> {
            OrderStatus status = mapStatus(firstText(payload, "payment_status", "status"));
            if (status != null) {
                whatsappOrder.setStatus(status);
                orderRepository.save(whatsappOrder);
            }
        });
    }

    private OrderStatus mapStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("cancel") || normalized.contains("void") || normalized.contains("refunded")) {
            return OrderStatus.CANCELADO;
        }
        if (normalized.contains("paid") || normalized.contains("pago") || normalized.contains("approved")) {
            return OrderStatus.PAGO;
        }
        return null;
    }

    private String writePayload(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String firstText(JsonNode payload, String... fields) {
        if (payload == null) {
            return null;
        }

        for (String field : fields) {
            JsonNode value = payload.findValue(field);
            if (value != null && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
        }
        return null;
    }

    private String firstWhatsAppMessageId(JsonNode payload) {
        if (payload == null) {
            return null;
        }

        for (JsonNode messagesNode : payload.findValues("messages")) {
            if (messagesNode.isArray() && !messagesNode.isEmpty()) {
                String messageId = messagesNode.get(0).path("id").asText();
                if (StringUtils.hasText(messageId)) {
                    return messageId;
                }
            }
        }

        for (JsonNode statusesNode : payload.findValues("statuses")) {
            if (statusesNode.isArray() && !statusesNode.isEmpty()) {
                String statusId = statusesNode.get(0).path("id").asText();
                if (StringUtils.hasText(statusId)) {
                    return statusId;
                }
            }
        }

        return firstText(payload, "wamid", "id");
    }

    private Long firstLong(JsonNode payload, String... fields) {
        if (payload == null) {
            return null;
        }

        for (String field : fields) {
            JsonNode value = payload.findValue(field);
            if (value != null && value.canConvertToLong()) {
                return value.asLong();
            }
            if (value != null && StringUtils.hasText(value.asText())) {
                try {
                    return Long.valueOf(value.asText());
                } catch (NumberFormatException ignored) {
                    continue;
                }
            }
        }
        return null;
    }
}
