package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.client.NuvemshopApiClient;
import br.com.llfashion.whatsappcheckout.config.CheckoutProperties;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopDraftOrderResponse;
import br.com.llfashion.whatsappcheckout.dto.response.LatestOrderStatusResponse;
import br.com.llfashion.whatsappcheckout.dto.response.OrderStatusItemResponse;
import br.com.llfashion.whatsappcheckout.dto.response.OrderStatusListResponse;
import br.com.llfashion.whatsappcheckout.dto.response.OrderStatusResponse;
import br.com.llfashion.whatsappcheckout.dto.response.OrderStatusSummaryResponse;
import br.com.llfashion.whatsappcheckout.entity.NuvemshopInstallation;
import br.com.llfashion.whatsappcheckout.entity.WhatsappOrder;
import br.com.llfashion.whatsappcheckout.entity.WhatsappOrderItem;
import br.com.llfashion.whatsappcheckout.enums.OrderStatus;
import br.com.llfashion.whatsappcheckout.enums.PublicOrderStatus;
import br.com.llfashion.whatsappcheckout.exception.EntityNotFoundException;
import br.com.llfashion.whatsappcheckout.exception.NuvemshopApiException;
import br.com.llfashion.whatsappcheckout.repository.WhatsappOrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrderTrackingService {

    private static final Logger log = LoggerFactory.getLogger(OrderTrackingService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final WhatsappOrderRepository orderRepository;
    private final CheckoutProperties checkoutProperties;
    private final NuvemshopInstallationService installationService;
    private final NuvemshopApiClient nuvemshopApiClient;

    public OrderTrackingService(
            WhatsappOrderRepository orderRepository,
            CheckoutProperties checkoutProperties,
            NuvemshopInstallationService installationService,
            NuvemshopApiClient nuvemshopApiClient
    ) {
        this.orderRepository = orderRepository;
        this.checkoutProperties = checkoutProperties;
        this.installationService = installationService;
        this.nuvemshopApiClient = nuvemshopApiClient;
    }

    @Transactional
    public OrderStatusResponse getStatus(String statusPublicToken) {
        if (!StringUtils.hasText(statusPublicToken)) {
            throw new EntityNotFoundException("Token de status do pedido nao informado.");
        }
        WhatsappOrder order = orderRepository.findByStatusPublicToken(statusPublicToken.trim())
                .orElseThrow(() -> new EntityNotFoundException("Pedido nao encontrado para o token informado."));
        refreshFromNuvemshop(order);
        return toResponse(order);
    }

    @Transactional
    public LatestOrderStatusResponse findLatestByPhone(String phone) {
        OrderStatusListResponse listResponse = findOrdersByPhone(phone);
        return new LatestOrderStatusResponse(
                listResponse.found(),
                listResponse.message(),
                listResponse.order(),
                listResponse.multiple(),
                listResponse.orders()
        );
    }

    @Transactional
    public OrderStatusListResponse findOrdersByPhone(String phone) {
        String normalizedPhone = onlyDigits(phone);
        if (!StringUtils.hasText(normalizedPhone)) {
            return new OrderStatusListResponse(false, false, "Nao encontrei um telefone valido para consultar seu pedido.", 0, null, List.of());
        }
        List<WhatsappOrder> orders = orderRepository.findByCustomerPhoneOrderByCreatedAtDesc(normalizedPhone);
        if (orders.isEmpty()) {
            return new OrderStatusListResponse(false, false, noOrdersMessage(normalizedPhone), 0, null, List.of());
        }

        orders.forEach(this::refreshFromNuvemshop);
        List<WhatsappOrder> visibleOrders = orders.stream()
                .filter(this::showInCustomerHistory)
                .toList();
        if (visibleOrders.isEmpty()) {
            return new OrderStatusListResponse(false, false, noActiveOrdersMessage(normalizedPhone), 0, null, List.of());
        }

        List<OrderStatusSummaryResponse> summaries = visibleOrders.stream()
                .map(this::toSummaryResponse)
                .toList();
        OrderStatusResponse singleOrder = visibleOrders.size() == 1 ? toResponse(visibleOrders.get(0)) : null;
        String message = visibleOrders.size() == 1
                ? "Encontrei 1 pedido para este WhatsApp."
                : "Encontrei " + visibleOrders.size() + " pedidos para este WhatsApp.";

        return new OrderStatusListResponse(true, visibleOrders.size() > 1, message, visibleOrders.size(), singleOrder, summaries);
    }

    @Transactional(readOnly = true)
    public Optional<String> findStatusTokenByLocalOrderId(UUID localOrderId) {
        if (localOrderId == null) {
            return Optional.empty();
        }
        return orderRepository.findById(localOrderId).map(WhatsappOrder::getStatusPublicToken);
    }

    @Transactional
    public int syncRecentOrderStatuses(int lookbackDays) {
        LocalDateTime createdAtFrom = LocalDateTime.now().minusDays(Math.max(1, lookbackDays));
        List<WhatsappOrder> orders = orderRepository.findTrackableOrdersSince(createdAtFrom, OrderStatus.ERRO);
        orders.forEach(this::refreshFromNuvemshop);
        return orders.size();
    }

    @Transactional
    public void updateFromNuvemshopWebhook(JsonNode payload) {
        Long externalId = firstLong(payload, "draft_order_id", "draft_order", "order_id", "id");
        if (externalId == null) {
            return;
        }
        WhatsappOrder order = orderRepository.findByNuvemshopDraftOrderId(externalId).orElse(null);
        if (order == null) {
            return;
        }

        updateIfPresent(order::setPaymentStatus, firstText(payload, "payment_status", "paymentStatus"));
        updateIfPresent(order::setShippingStatus, firstText(payload, "shipping_status", "shippingStatus", "fulfillment_status"));
        updateIfPresent(order::setShippingTrackingNumber, firstText(payload, "tracking_number", "trackingNumber", "shipping_tracking_number"));
        updateIfPresent(order::setShippingTrackingUrl, firstText(payload, "tracking_url", "trackingUrl", "shipping_tracking_url"));
        updateIfPresent(order::setShippingMethod, firstText(payload, "shipping_method", "shippingMethod", "shipping_option"));
        updateOrderNumber(order, firstText(payload, "number", "order_number", "orderNumber"));
        updateOrderStatus(order, firstText(payload, "status", "order_status"), firstText(payload, "payment_status", "paymentStatus"));
        orderRepository.save(order);
        refreshFromNuvemshop(order);
    }

    private void refreshFromNuvemshop(WhatsappOrder order) {
        if (order == null || order.getNuvemshopDraftOrderId() == null) {
            return;
        }

        try {
            NuvemshopInstallation installation = installationService.getActiveInstallation();
            NuvemshopDraftOrderResponse nuvemshopOrder = findCurrentNuvemshopOrder(installation, order.getNuvemshopDraftOrderId());
            if (nuvemshopOrder == null || nuvemshopOrder.id() == null) {
                log.warn("Pedido nao encontrado na Nuvemshop. Marcando pedido local como cancelado. localOrderId={}, draftOrderId={}",
                        order.getId(),
                        order.getNuvemshopDraftOrderId());
                order.setStatus(OrderStatus.CANCELADO);
                order.setPaymentStatus("cancelled");
                orderRepository.save(order);
                return;
            }
            applyDraftOrderStatus(order, nuvemshopOrder);
            orderRepository.save(order);
        } catch (NuvemshopApiException exception) {
            if (isNuvemshopOrderNotFound(exception)) {
                markOrderAsNotFound(order);
                return;
            }
            log.warn("Nao foi possivel atualizar status do pedido na Nuvemshop. localOrderId={}, draftOrderId={}, status={}, erro={}",
                    order.getId(),
                    order.getNuvemshopDraftOrderId(),
                    exception.getStatusCode(),
                    exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("Nao foi possivel atualizar status do pedido na Nuvemshop. localOrderId={}, draftOrderId={}, erro={}",
                    order.getId(),
                    order.getNuvemshopDraftOrderId(),
                    exception.getMessage());
        }
    }

    private NuvemshopDraftOrderResponse findCurrentNuvemshopOrder(NuvemshopInstallation installation, Long nuvemshopId) {
        try {
            NuvemshopDraftOrderResponse draftOrder = nuvemshopApiClient.buscarDraftOrder(
                    installation.getStoreId(),
                    installation.getAccessToken(),
                    nuvemshopId
            );
            if (draftOrder != null && draftOrder.id() != null) {
                return draftOrder;
            }
        } catch (NuvemshopApiException exception) {
            if (!isNuvemshopOrderNotFound(exception)) {
                throw exception;
            }
            log.info("Draft Order nao encontrada. Tentando buscar como venda. draftOrderId={}", nuvemshopId);
        }

        try {
            return nuvemshopApiClient.buscarOrder(
                    installation.getStoreId(),
                    installation.getAccessToken(),
                    nuvemshopId
            );
        } catch (NuvemshopApiException exception) {
            if (isNuvemshopOrderNotFound(exception)) {
                return null;
            }
            throw exception;
        }
    }

    private boolean isNuvemshopOrderNotFound(NuvemshopApiException exception) {
        if (exception == null) {
            return false;
        }
        String responseBody = normalize(exception.getResponseBody());
        return exception.getStatusCode() == 404
                || (exception.getStatusCode() == 500
                && (responseBody.contains("DRAFT ORDER NOT FOUND") || responseBody.contains("ORDER NOT FOUND")));
    }

    private void markOrderAsNotFound(WhatsappOrder order) {
        log.warn("Pedido nao encontrado na Nuvemshop. Marcando pedido local como cancelado. localOrderId={}, draftOrderId={}",
                order.getId(),
                order.getNuvemshopDraftOrderId());
        order.setStatus(OrderStatus.CANCELADO);
        order.setPaymentStatus("cancelled");
        orderRepository.save(order);
    }

    private boolean showInCustomerHistory(WhatsappOrder order) {
        if (order == null || order.getStatus() == OrderStatus.ERRO || order.getNuvemshopDraftOrderId() == null) {
            return false;
        }
        if (order.getStatus() == OrderStatus.CANCELADO) {
            return isValidNuvemshopOrderNumber(order.getNuvemshopOrderNumber());
        }
        return true;
    }

    private void applyDraftOrderStatus(WhatsappOrder order, NuvemshopDraftOrderResponse draftOrder) {
        if (draftOrder == null) {
            return;
        }

        updateIfPresent(order::setPaymentStatus, draftOrder.paymentStatus());
        updateIfPresent(order::setShippingStatus, draftOrder.shippingStatus());
        updateIfPresent(order::setShippingMethod, firstText(draftOrder.shippingMethod(), draftOrder.shippingOption()));
        updateIfPresent(order::setShippingTrackingNumber, firstText(draftOrder.shippingTrackingNumber(), draftOrder.trackingNumber()));
        updateIfPresent(order::setShippingTrackingUrl, firstText(draftOrder.shippingTrackingUrl(), draftOrder.trackingUrl()));
        updateOrderNumber(order, draftOrder.number());
        if (draftOrder.total() != null) {
            order.setTotal(draftOrder.total());
        }
        if (StringUtils.hasText(draftOrder.checkoutUrl())) {
            order.setCheckoutUrl(draftOrder.checkoutUrl());
        }
        if (StringUtils.hasText(draftOrder.abandonedCheckoutUrl())) {
            order.setAbandonedCheckoutUrl(draftOrder.abandonedCheckoutUrl());
        }
        boolean cancelledByNuvemshop = StringUtils.hasText(draftOrder.cancelledAt())
                || StringUtils.hasText(draftOrder.cancelReason());
        if (cancelledByNuvemshop) {
            order.setStatus(OrderStatus.CANCELADO);
            order.setPaymentStatus("cancelled");
        }
        updateIfPresent(order::setPixCopyPaste, draftOrder.pixCopyPaste());
        updateIfPresent(order::setPixQrCodeUrl, draftOrder.pixQrCodeUrl());
        if (!cancelledByNuvemshop) {
            updateOrderStatus(order, draftOrder.status(), draftOrder.paymentStatus());
        }
    }

    private void updateOrderStatus(WhatsappOrder order, String nuvemshopStatus, String paymentStatus) {
        String normalizedStatus = normalize(nuvemshopStatus);
        String normalizedPayment = normalize(paymentStatus);

        if (containsAny(normalizedStatus, "CANCEL", "VOID", "DELET")
                || containsAny(normalizedPayment, "CANCEL", "VOID", "REFUND", "ESTORN", "CHARGEBACK")) {
            order.setStatus(OrderStatus.CANCELADO);
            updateIfPresent(order::setPaymentStatus, firstText(paymentStatus, "cancelled"));
            return;
        }
        if (containsAny(normalizedPayment, "PAID", "PAGO", "APPROVED")) {
            order.setStatus(OrderStatus.PAGO);
        }
    }

    public String statusUrl(String statusPublicToken) {
        if (!StringUtils.hasText(statusPublicToken)) {
            return null;
        }
        String baseUrl = checkoutProperties.resolvedFrontendBaseUrl();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return baseUrl + "pedido/status?token=" + statusPublicToken;
    }

    public String statusListUrl(String phone) {
        String normalizedPhone = onlyDigits(phone);
        if (!StringUtils.hasText(normalizedPhone)) {
            return null;
        }
        String baseUrl = checkoutProperties.resolvedFrontendBaseUrl();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return baseUrl + "pedido/status?phone=" + URLEncoder.encode(normalizedPhone, StandardCharsets.UTF_8);
    }

    public String buildWhatsAppStatusMessage(OrderStatusResponse order) {
        return "Encontrei seu pedido\n\n"
                + "Pedido: " + publicOrderNumber(order) + "\n"
                + "Pagamento: " + friendlyPayment(order.paymentStatus()) + "\n"
                + "Envio: " + friendlyShipping(order.shippingStatus()) + "\n\n"
                + "Acompanhe por aqui:\n"
                + statusUrl(order.statusPublicToken());
    }

    public String buildWhatsAppStatusMessage(OrderStatusListResponse result) {
        return buildWhatsAppStatusMessage(result, null);
    }

    public String buildWhatsAppStatusMessage(OrderStatusListResponse result, String phone) {
        if (result == null || !result.found()) {
            String normalizedPhone = onlyDigits(phone);
            return result == null ? noOrdersMessage(normalizedPhone) : result.message();
        }
        if (!result.multiple() && result.order() != null) {
            return buildWhatsAppStatusMessage(result.order());
        }

        String listUrl = statusListUrl(phone);
        if (StringUtils.hasText(listUrl)) {
            return result.message()
                    + "\n\nAbra sua tela de Meus pedidos para escolher qual pedido acompanhar:\n"
                    + listUrl;
        }

        StringBuilder message = new StringBuilder(result.message())
                .append("\n\nEscolha qual pedido deseja acompanhar:\n");
        int index = 1;
        for (OrderStatusSummaryResponse order : result.orders()) {
            message.append("\n")
                    .append(index++)
                    .append(". Pedido ")
                    .append(order.publicOrderNumber())
                    .append("\n")
                    .append("Data: ")
                    .append(order.createdAt() == null ? "nao informada" : DATE_FORMATTER.format(order.createdAt()))
                    .append("\n")
                    .append("Total: ")
                    .append(money(order.total()))
                    .append("\n")
                    .append("Pagamento: ")
                    .append(order.paymentStatus())
                    .append("\n")
                    .append("Envio: ")
                    .append(order.shippingStatus())
                    .append("\n")
                    .append("Ver detalhes: ")
                    .append(order.statusUrl())
                    .append("\n");
        }
        return message.toString().trim();
    }

    private OrderStatusResponse toResponse(WhatsappOrder order) {
        PublicOrderStatus publicStatus = publicStatus(order);
        return new OrderStatusResponse(
                order.getStatusPublicToken(),
                order.getId(),
                order.getNuvemshopDraftOrderId(),
                validNuvemshopOrderNumber(order.getNuvemshopOrderNumber()),
                publicStatus,
                title(publicStatus),
                message(publicStatus),
                friendlyPayment(firstText(order.getPaymentStatus(), order.getStatus().name())),
                friendlyShipping(firstText(order.getShippingStatus(), "pending")),
                canPay(order, publicStatus),
                order.getCheckoutUrl(),
                order.getPixCopyPaste(),
                order.getPixQrCodeUrl(),
                firstText(order.getShippingMethod(), "Aguardando atualizacao da loja"),
                shippingEta(order),
                order.getShippingTrackingNumber(),
                order.getShippingTrackingUrl(),
                order.getTotal(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getItems().stream().map(this::toItemResponse).toList()
        );
    }

    private OrderStatusSummaryResponse toSummaryResponse(WhatsappOrder order) {
        PublicOrderStatus publicStatus = publicStatus(order);
        return new OrderStatusSummaryResponse(
                order.getStatusPublicToken(),
                order.getId(),
                order.getNuvemshopDraftOrderId(),
                order.getNuvemshopOrderNumber(),
                publicOrderNumber(order),
                publicStatus,
                title(publicStatus),
                friendlyPayment(firstText(order.getPaymentStatus(), order.getStatus().name())),
                friendlyShipping(firstText(order.getShippingStatus(), "pending")),
                canPay(order, publicStatus),
                order.getCheckoutUrl(),
                statusUrl(order.getStatusPublicToken()),
                order.getTotal(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderStatusItemResponse toItemResponse(WhatsappOrderItem item) {
        BigDecimal unitPrice = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
        Integer quantity = item.getQuantity() == null ? 0 : item.getQuantity();
        return new OrderStatusItemResponse(
                item.getProductName(),
                item.getVariantName(),
                firstText(item.getImageUrl(), item.getProductMapping() == null ? null : item.getProductMapping().getImageUrl()),
                quantity,
                unitPrice,
                unitPrice.multiply(BigDecimal.valueOf(quantity))
        );
    }

    private PublicOrderStatus publicStatus(WhatsappOrder order) {
        if (order.getStatus() == OrderStatus.CANCELADO) {
            return PublicOrderStatus.CANCELLED;
        }
        String payment = normalize(order.getPaymentStatus());
        String shipping = normalize(order.getShippingStatus());
        if (containsAny(payment, "CANCEL", "CANCELADO", "VOID")) {
            return PublicOrderStatus.CANCELLED;
        }
        if (containsAny(payment, "REFUND", "ESTORN")) {
            return PublicOrderStatus.REFUNDED;
        }
        if (containsAny(shipping, "DELIVERED", "ENTREGUE")) {
            return PublicOrderStatus.DELIVERED;
        }
        if (containsAny(shipping, "SHIPPED", "ENVIADO", "IN_TRANSIT", "TRANSPORTE")) {
            return PublicOrderStatus.SHIPPED;
        }
        if (containsAny(payment, "PAID", "PAGO", "APPROVED")) {
            return PublicOrderStatus.PAYMENT_CONFIRMED;
        }
        if (StringUtils.hasText(order.getCheckoutUrl())) {
            return PublicOrderStatus.WAITING_PAYMENT;
        }
        return PublicOrderStatus.ORDER_RECEIVED;
    }

    private String title(PublicOrderStatus status) {
        return switch (status) {
            case ORDER_RECEIVED -> "Pedido recebido";
            case WAITING_PAYMENT -> "Aguardando pagamento Pix";
            case PAYMENT_CONFIRMED -> "Pagamento confirmado";
            case SEPARATING_ORDER -> "Estamos separando seu pedido";
            case SHIPPED -> "Pedido enviado";
            case DELIVERED -> "Pedido entregue";
            case CANCELLED -> "Pedido cancelado";
            case REFUNDED -> "Pagamento estornado";
            case ERROR -> "Precisamos revisar seu pedido";
        };
    }

    private String message(PublicOrderStatus status) {
        return switch (status) {
            case ORDER_RECEIVED -> "Recebemos seu pedido e estamos preparando as proximas etapas.";
            case WAITING_PAYMENT -> "Use o link de pagamento para concluir seu Pix no checkout seguro.";
            case PAYMENT_CONFIRMED -> "Seu pagamento foi confirmado. Agora vamos preparar seu pedido.";
            case SEPARATING_ORDER -> "Seu pedido esta em separacao pela equipe da loja.";
            case SHIPPED -> "Seu pedido saiu para entrega. Use o rastreio para acompanhar.";
            case DELIVERED -> "Entrega confirmada. Obrigado por comprar com a LLFashion Moda.";
            case CANCELLED -> "O pedido foi cancelado. Fale com a equipe se precisar de ajuda.";
            case REFUNDED -> "Identificamos estorno do pagamento deste pedido.";
            case ERROR -> "Encontramos um problema e uma atendente pode ajudar.";
        };
    }

    private String shippingEta(WhatsappOrder order) {
        if (order.getShippingMinDays() != null && order.getShippingMaxDays() != null) {
            return order.getShippingMinDays() + " a " + order.getShippingMaxDays() + " dias uteis";
        }
        return "Aguardando atualizacao da loja";
    }

    private String publicOrderNumber(OrderStatusResponse order) {
        if (isValidNuvemshopOrderNumber(order.nuvemshopOrderNumber())) {
            return "#" + order.nuvemshopOrderNumber();
        }
        if (order.nuvemshopDraftOrderId() != null) {
            return "#" + order.nuvemshopDraftOrderId();
        }
        return "#" + order.localOrderId();
    }

    private String publicOrderNumber(WhatsappOrder order) {
        if (isValidNuvemshopOrderNumber(order.getNuvemshopOrderNumber())) {
            return "#" + order.getNuvemshopOrderNumber();
        }
        if (order.getNuvemshopDraftOrderId() != null) {
            return "#" + order.getNuvemshopDraftOrderId();
        }
        return "#" + order.getId();
    }

    private boolean isValidNuvemshopOrderNumber(String number) {
        return StringUtils.hasText(number) && !"0".equals(number.trim());
    }

    private String validNuvemshopOrderNumber(String number) {
        return isValidNuvemshopOrderNumber(number) ? number.trim() : null;
    }

    private void updateOrderNumber(WhatsappOrder order, String number) {
        if (!StringUtils.hasText(number)) {
            return;
        }
        if (isValidNuvemshopOrderNumber(number)) {
            order.setNuvemshopOrderNumber(number.trim());
            return;
        }
        order.setNuvemshopOrderNumber(null);
    }

    private boolean canPay(WhatsappOrder order, PublicOrderStatus publicStatus) {
        if (order == null || !StringUtils.hasText(order.getCheckoutUrl())) {
            return false;
        }
        if (publicStatus != PublicOrderStatus.WAITING_PAYMENT && publicStatus != PublicOrderStatus.ORDER_RECEIVED) {
            return false;
        }
        String payment = normalize(order.getPaymentStatus());
        String status = order.getStatus() == null ? "" : normalize(order.getStatus().name());
        return !containsAny(payment, "PAID", "PAGO", "APPROVED", "CANCEL", "REFUND", "ESTORN")
                && !containsAny(status, "PAGO", "CANCELADO");
    }

    private String noOrdersMessage(String normalizedPhone) {
        return "Nao encontrei nenhum pedido para este numero de WhatsApp"
                + phoneSuffix(normalizedPhone)
                + ".";
    }

    private String noActiveOrdersMessage(String normalizedPhone) {
        return "Nao encontrei nenhum pedido ativo para este numero de WhatsApp"
                + phoneSuffix(normalizedPhone)
                + ".";
    }

    private String phoneSuffix(String normalizedPhone) {
        if (!StringUtils.hasText(normalizedPhone)) {
            return "";
        }
        return ": +" + normalizedPhone;
    }

    private String money(BigDecimal value) {
        return java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("pt-BR"))
                .format(value == null ? BigDecimal.ZERO : value);
    }

    private String friendlyPayment(String status) {
        String normalized = normalize(status);
        if (containsAny(normalized, "PAID", "PAGO", "APPROVED")) {
            return "Pago";
        }
        if (containsAny(normalized, "CANCEL", "CANCELADO")) {
            return "Cancelado";
        }
        if (containsAny(normalized, "REFUND", "ESTORN")) {
            return "Estornado";
        }
        return "Aguardando pagamento";
    }

    private String friendlyShipping(String status) {
        String normalized = normalize(status);
        if (containsAny(normalized, "DELIVERED", "ENTREGUE")) {
            return "Entregue";
        }
        if (containsAny(normalized, "SHIPPED", "ENVIADO", "TRANSPORTE", "IN_TRANSIT")) {
            return "Em transporte";
        }
        if (containsAny(normalized, "SEPARATING", "PACKED", "SEPARANDO")) {
            return "Separando pedido";
        }
        return "Aguardando atualizacao da loja";
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : second;
    }

    private String firstText(JsonNode payload, String... fieldNames) {
        if (payload == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode node = payload.findValue(fieldName);
            if (node != null && !node.isNull() && StringUtils.hasText(node.asText())) {
                return node.asText().trim();
            }
        }
        return null;
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

    private void updateIfPresent(java.util.function.Consumer<String> setter, String value) {
        if (StringUtils.hasText(value)) {
            setter.accept(value.trim());
        }
    }
}
