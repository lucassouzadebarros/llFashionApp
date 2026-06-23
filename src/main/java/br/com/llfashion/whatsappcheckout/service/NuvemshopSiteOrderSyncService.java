package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.client.NuvemshopApiClient;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopOrderProductResponse;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopOrderResponse;
import br.com.llfashion.whatsappcheckout.dto.response.NuvemshopOrderImportResponse;
import br.com.llfashion.whatsappcheckout.entity.NuvemshopInstallation;
import br.com.llfashion.whatsappcheckout.entity.ProductMapping;
import br.com.llfashion.whatsappcheckout.entity.WhatsappOrder;
import br.com.llfashion.whatsappcheckout.entity.WhatsappOrderItem;
import br.com.llfashion.whatsappcheckout.enums.OrderStatus;
import br.com.llfashion.whatsappcheckout.enums.WebhookSource;
import br.com.llfashion.whatsappcheckout.repository.ProductMappingRepository;
import br.com.llfashion.whatsappcheckout.repository.WhatsappOrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class NuvemshopSiteOrderSyncService {

    private static final Logger log = LoggerFactory.getLogger(NuvemshopSiteOrderSyncService.class);
    private static final int PER_PAGE = 100;

    private final NuvemshopInstallationService installationService;
    private final NuvemshopApiClient apiClient;
    private final WhatsappOrderRepository orderRepository;
    private final ProductMappingRepository productMappingRepository;
    private final ObjectMapper objectMapper;

    public NuvemshopSiteOrderSyncService(
            NuvemshopInstallationService installationService,
            NuvemshopApiClient apiClient,
            WhatsappOrderRepository orderRepository,
            ProductMappingRepository productMappingRepository,
            ObjectMapper objectMapper
    ) {
        this.installationService = installationService;
        this.apiClient = apiClient;
        this.orderRepository = orderRepository;
        this.productMappingRepository = productMappingRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NuvemshopOrderImportResponse importCreatedOrders(int lookbackDays) {
        int days = Math.max(1, lookbackDays);
        String createdAtMin = OffsetDateTime.now().minusDays(days).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return importOrders(createdAtMin, null, "Importacao de pedidos da loja concluida com sucesso");
    }

    @Transactional
    public NuvemshopOrderImportResponse syncRecentlyUpdatedOrders(int lookbackDays) {
        int days = Math.max(1, lookbackDays);
        String updatedAtMin = OffsetDateTime.now().minusDays(days).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return importOrders(null, updatedAtMin, "Sincronizacao de pedidos da loja concluida com sucesso");
    }

    @Transactional
    public boolean syncOrderFromWebhook(JsonNode payload) {
        Long orderId = firstLong(payload, "order_id", "id");
        if (orderId == null) {
            log.warn("Webhook Nuvemshop de pedido sem order_id/id.");
            return false;
        }

        NuvemshopInstallation installation = installationService.getActiveInstallation();
        NuvemshopOrderResponse order = apiClient.buscarOrderDetalhado(
                installation.getStoreId(),
                installation.getAccessToken(),
                orderId
        );
        return upsertOrder(order);
    }

    @Transactional
    public boolean syncOrderById(Long orderId) {
        if (orderId == null) {
            return false;
        }
        NuvemshopInstallation installation = installationService.getActiveInstallation();
        NuvemshopOrderResponse order = apiClient.buscarOrderDetalhado(
                installation.getStoreId(),
                installation.getAccessToken(),
                orderId
        );
        return upsertOrder(order);
    }

    private NuvemshopOrderImportResponse importOrders(String createdAtMin, String updatedAtMin, String successMessage) {
        NuvemshopInstallation installation = installationService.getActiveInstallation();
        int page = 1;
        int totalRead = 0;
        int totalImported = 0;
        int totalSkipped = 0;

        while (true) {
            List<NuvemshopOrderResponse> orders = apiClient.listarOrders(
                    installation.getStoreId(),
                    installation.getAccessToken(),
                    page,
                    PER_PAGE,
                    createdAtMin,
                    updatedAtMin,
                    null
            );

            if (orders.isEmpty()) {
                break;
            }

            totalRead += orders.size();
            for (NuvemshopOrderResponse order : orders) {
                NuvemshopOrderResponse detailedOrder = fetchOrderDetails(installation, order);
                if (upsertOrder(detailedOrder)) {
                    totalImported++;
                } else {
                    totalSkipped++;
                }
            }

            if (orders.size() < PER_PAGE) {
                break;
            }
            page++;
        }

        return new NuvemshopOrderImportResponse(totalRead, totalImported, totalSkipped, successMessage);
    }

    private NuvemshopOrderResponse fetchOrderDetails(NuvemshopInstallation installation, NuvemshopOrderResponse order) {
        if (order == null || order.id() == null) {
            return order;
        }
        try {
            NuvemshopOrderResponse detailedOrder = apiClient.buscarOrderDetalhado(
                    installation.getStoreId(),
                    installation.getAccessToken(),
                    order.id()
            );
            return detailedOrder == null ? order : detailedOrder;
        } catch (RuntimeException exception) {
            log.warn("Não foi possível buscar detalhe do pedido Nuvemshop. orderId={}, erro={}",
                    order.id(),
                    exception.getMessage());
            return order;
        }
    }

    private boolean upsertOrder(NuvemshopOrderResponse nuvemshopOrder) {
        if (nuvemshopOrder == null || nuvemshopOrder.id() == null) {
            return false;
        }

        WhatsappOrder order = orderRepository.findByNuvemshopDraftOrderId(nuvemshopOrder.id()).orElse(null);
        boolean newOrder = order == null;
        String normalizedPhone = onlyDigits(firstText(
                nuvemshopOrder.contactPhone(),
                text(nuvemshopOrder.customer(), "phone", "contact_phone", "mobile")
        ));

        if (newOrder && !StringUtils.hasText(normalizedPhone)) {
            log.info("Pedido da Nuvemshop ignorado porque não possui telefone. orderId={}, number={}",
                    nuvemshopOrder.id(),
                    nuvemshopOrder.number());
            return false;
        }

        if (newOrder) {
            order = newSiteOrder(nuvemshopOrder, normalizedPhone);
        }

        applyOrderFields(order, nuvemshopOrder, normalizedPhone);
        if (order.getSource() == WebhookSource.NUVEMSHOP_SITE || order.getItems().isEmpty()) {
            rebuildItems(order, nuvemshopOrder.products());
        }
        orderRepository.save(order);
        return true;
    }

    private WhatsappOrder newSiteOrder(NuvemshopOrderResponse nuvemshopOrder, String normalizedPhone) {
        String fullName = firstText(
                nuvemshopOrder.contactName(),
                text(nuvemshopOrder.customer(), "name", "contact_name")
        );
        String[] names = splitName(fullName);

        return WhatsappOrder.builder()
                .customerName(names[0])
                .customerLastname(names[1])
                .customerEmail(normalizeEmail(firstText(
                        nuvemshopOrder.contactEmail(),
                        text(nuvemshopOrder.customer(), "email", "contact_email")
                ), normalizedPhone, nuvemshopOrder.id()))
                .customerPhone(normalizedPhone)
                .source(WebhookSource.NUVEMSHOP_SITE)
                .statusPublicToken(generateStatusPublicToken())
                .nuvemshopDraftOrderId(nuvemshopOrder.id())
                .status(resolveOrderStatus(nuvemshopOrder))
                .createdAt(parseDate(nuvemshopOrder.createdAt()))
                .updatedAt(parseDate(nuvemshopOrder.updatedAt()))
                .build();
    }

    private void applyOrderFields(WhatsappOrder order, NuvemshopOrderResponse nuvemshopOrder, String normalizedPhone) {
        if (StringUtils.hasText(normalizedPhone)) {
            order.setCustomerPhone(normalizedPhone);
        }
        updateIfPresent(order::setCustomerDocument, firstText(
                nuvemshopOrder.contactIdentification(),
                text(nuvemshopOrder.customer(), "identification", "contact_identification")
        ));
        updateIfPresent(order::setNuvemshopOrderNumber, validOrderNumber(nuvemshopOrder.number()));
        updateIfPresent(order::setPaymentStatus, nuvemshopOrder.paymentStatus());
        updateIfPresent(order::setShippingStatus, nuvemshopOrder.shippingStatus());
        updateIfPresent(order::setShippingMethod, firstText(nuvemshopOrder.shippingMethod(), nuvemshopOrder.shippingOption()));
        updateIfPresent(order::setShippingTrackingNumber, firstText(nuvemshopOrder.shippingTrackingNumber(), nuvemshopOrder.trackingNumber()));
        updateIfPresent(order::setShippingTrackingUrl, firstText(nuvemshopOrder.shippingTrackingUrl(), nuvemshopOrder.trackingUrl()));
        updateIfPresent(order::setCheckoutUrl, nuvemshopOrder.checkoutUrl());
        updateIfPresent(order::setAbandonedCheckoutUrl, nuvemshopOrder.abandonedCheckoutUrl());
        if (nuvemshopOrder.total() != null) {
            order.setTotal(nuvemshopOrder.total());
        }
        order.setStatus(resolveOrderStatus(nuvemshopOrder));
        applyShippingAddress(order, nuvemshopOrder.shippingAddress());
        order.setRawNuvemshopResponse(writeRaw(nuvemshopOrder));
        LocalDateTime updatedAt = parseDate(nuvemshopOrder.updatedAt());
        if (updatedAt != null) {
            order.setUpdatedAt(updatedAt);
        }
    }

    private void rebuildItems(WhatsappOrder order, List<NuvemshopOrderProductResponse> products) {
        order.getItems().clear();
        if (products == null || products.isEmpty()) {
            return;
        }

        for (NuvemshopOrderProductResponse product : products) {
            Long variantId = firstLong(product.variantId(), product.id());
            Long productId = firstLong(product.productId(), product.id());
            if (variantId == null || productId == null) {
                log.info("Item de pedido Nuvemshop ignorado por falta de product_id/variant_id. orderId={}", order.getNuvemshopDraftOrderId());
                continue;
            }

            ProductMapping mapping = productMappingRepository.findByNuvemshopVariantId(variantId).orElse(null);
            order.addItem(WhatsappOrderItem.builder()
                    .productMapping(mapping)
                    .nuvemshopProductId(productId)
                    .nuvemshopVariantId(variantId)
                    .productName(firstText(localizedText(product.name()), mapping == null ? null : mapping.getProductName(), "Produto"))
                    .variantName(firstText(localizedText(product.variantName()), mapping == null ? null : mapping.getVariantName()))
                    .imageUrl(firstText(imageUrl(product), mapping == null ? null : mapping.getImageUrl()))
                    .quantity(product.quantity() == null ? 1 : Math.max(1, product.quantity()))
                    .unitPrice(firstBigDecimal(product.price(), mapping == null ? null : mapping.getPrice()))
                    .build());
        }
    }

    private void applyShippingAddress(WhatsappOrder order, JsonNode address) {
        if (address == null || address.isMissingNode() || address.isNull()) {
            return;
        }
        updateIfPresent(order::setShippingPostalCode, text(address, "zipcode", "zip_code", "postal_code"));
        updateIfPresent(order::setShippingStreet, text(address, "address", "street", "street_name"));
        updateIfPresent(order::setShippingNumber, text(address, "number", "street_number"));
        updateIfPresent(order::setShippingComplement, text(address, "floor", "apartment", "complement"));
        updateIfPresent(order::setShippingNeighborhood, text(address, "locality", "neighborhood"));
        updateIfPresent(order::setShippingCity, text(address, "city"));
        updateIfPresent(order::setShippingState, text(address, "province", "state"));
    }

    private OrderStatus resolveOrderStatus(NuvemshopOrderResponse order) {
        String status = normalize(order.status());
        String payment = normalize(order.paymentStatus());
        if (containsAny(status, "CANCEL", "VOID", "DELET")
                || containsAny(payment, "CANCEL", "VOID", "REFUND", "ESTORN", "CHARGEBACK")
                || StringUtils.hasText(order.cancelledAt())
                || StringUtils.hasText(order.cancelReason())) {
            return OrderStatus.CANCELADO;
        }
        if (containsAny(payment, "PAID", "PAGO", "APPROVED")) {
            return OrderStatus.PAGO;
        }
        return OrderStatus.AGUARDANDO_PAGAMENTO;
    }

    private String validOrderNumber(String number) {
        if (!StringUtils.hasText(number) || "0".equals(number.trim())) {
            return null;
        }
        return number.trim();
    }

    private String normalizeEmail(String email, String normalizedPhone, Long orderId) {
        if (StringUtils.hasText(email)) {
            return email.trim();
        }
        String suffix = StringUtils.hasText(normalizedPhone) ? normalizedPhone : String.valueOf(orderId);
        return "cliente+" + suffix + "@llfashionmoda.com.br";
    }

    private String[] splitName(String fullName) {
        if (!StringUtils.hasText(fullName)) {
            return new String[]{"Cliente", "Nuvemshop"};
        }
        String trimmed = fullName.trim().replaceAll("\\s+", " ");
        int separator = trimmed.indexOf(' ');
        if (separator < 0) {
            return new String[]{trimmed, "Cliente"};
        }
        return new String[]{trimmed.substring(0, separator), trimmed.substring(separator + 1)};
    }

    private LocalDateTime parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
        )) {
            try {
                return OffsetDateTime.parse(trimmed, formatter).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // try next formatter
            }
        }
        try {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String localizedText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        JsonNode pt = node.path("pt");
        if (!pt.isMissingNode() && !pt.isNull() && StringUtils.hasText(pt.asText())) {
            return pt.asText().trim();
        }
        var fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String value = localizedText(entry.getValue());
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String imageUrl(NuvemshopOrderProductResponse product) {
        if (product == null) {
            return null;
        }
        if (StringUtils.hasText(product.imageUrl())) {
            return product.imageUrl().trim();
        }
        JsonNode image = product.image();
        if (image == null || image.isNull() || image.isMissingNode()) {
            return null;
        }
        if (image.isTextual()) {
            return image.asText();
        }
        if (image.isArray() && !image.isEmpty()) {
            return text(image.get(0), "src", "url", "image_url");
        }
        return text(image, "src", "url", "image_url");
    }

    private String text(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value != null && !value.isMissingNode() && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private String writeRaw(NuvemshopOrderResponse order) {
        try {
            return objectMapper.writeValueAsString(order);
        } catch (Exception exception) {
            return null;
        }
    }

    private String generateStatusPublicToken() {
        return "ord_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private Long firstLong(Long first, Long second) {
        return first != null ? first : second;
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
            if (StringUtils.hasText(node.asText())) {
                try {
                    return Long.valueOf(node.asText().trim());
                } catch (NumberFormatException ignored) {
                    continue;
                }
            }
        }
        return null;
    }

    private BigDecimal firstBigDecimal(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private void updateIfPresent(java.util.function.Consumer<String> setter, String value) {
        if (StringUtils.hasText(value)) {
            setter.accept(value.trim());
        }
    }
}
