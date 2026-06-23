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
import br.com.llfashion.whatsappcheckout.entity.OrderStatusAccessToken;
import br.com.llfashion.whatsappcheckout.entity.WhatsappOrder;
import br.com.llfashion.whatsappcheckout.entity.WhatsappOrderItem;
import br.com.llfashion.whatsappcheckout.enums.OrderStatus;
import br.com.llfashion.whatsappcheckout.enums.PublicOrderStatus;
import br.com.llfashion.whatsappcheckout.exception.BusinessException;
import br.com.llfashion.whatsappcheckout.exception.EntityNotFoundException;
import br.com.llfashion.whatsappcheckout.exception.NuvemshopApiException;
import br.com.llfashion.whatsappcheckout.repository.OrderStatusAccessTokenRepository;
import br.com.llfashion.whatsappcheckout.repository.WhatsappOrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrderTrackingService {

    private static final Logger log = LoggerFactory.getLogger(OrderTrackingService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final WhatsappOrderRepository orderRepository;
    private final OrderStatusAccessTokenRepository accessTokenRepository;
    private final CheckoutProperties checkoutProperties;
    private final NuvemshopInstallationService installationService;
    private final NuvemshopApiClient nuvemshopApiClient;

    public OrderTrackingService(
            WhatsappOrderRepository orderRepository,
            OrderStatusAccessTokenRepository accessTokenRepository,
            CheckoutProperties checkoutProperties,
            NuvemshopInstallationService installationService,
            NuvemshopApiClient nuvemshopApiClient
    ) {
        this.orderRepository = orderRepository;
        this.accessTokenRepository = accessTokenRepository;
        this.checkoutProperties = checkoutProperties;
        this.installationService = installationService;
        this.nuvemshopApiClient = nuvemshopApiClient;
    }

    @Transactional
    public OrderStatusResponse getStatus(String statusPublicToken) {
        if (!StringUtils.hasText(statusPublicToken)) {
            throw new EntityNotFoundException("Token de status do pedido não informado.");
        }
        WhatsappOrder order = orderRepository.findByStatusPublicToken(statusPublicToken.trim())
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado para o token informado."));
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
            return new OrderStatusListResponse(false, false, "Não encontrei um telefone válido para consultar seu pedido.", 0, null, List.of());
        }
        List<String> phoneCandidates = phoneCandidates(normalizedPhone);
        List<WhatsappOrder> orders = orderRepository.findByCustomerPhoneInOrderByCreatedAtDesc(phoneCandidates);
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

    @Transactional
    public OrderStatusListResponse findOrdersByTemporaryAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new EntityNotFoundException("Token temporário de acompanhamento não informado.");
        }
        OrderStatusAccessToken savedToken = accessTokenRepository
                .findByAccessTokenHashAndExpiresAtAfter(hash(accessToken.trim()), LocalDateTime.now())
                .orElseThrow(() -> new BusinessException("Link de acompanhamento expirado ou inválido. Solicite um novo link pelo WhatsApp.", HttpStatus.GONE));

        if (savedToken.getOrderId() != null) {
            WhatsappOrder order = orderRepository.findById(savedToken.getOrderId())
                    .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado para o token temporário informado."));
            refreshFromNuvemshop(order);
            OrderStatusResponse response = toResponse(order);
            return new OrderStatusListResponse(true, false, "Encontrei seu pedido.", 1, response, List.of(toSummaryResponse(order)));
        }

        if (StringUtils.hasText(savedToken.getCustomerPhone())) {
            return findOrdersByPhone(savedToken.getCustomerPhone());
        }

        throw new EntityNotFoundException("Token temporário de acompanhamento sem pedido ou telefone vinculado.");
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
                log.warn("Pedido não encontrado na Nuvemshop. Marcando pedido local como cancelado. localOrderId={}, draftOrderId={}",
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
            log.warn("Não foi possível atualizar status do pedido na Nuvemshop. localOrderId={}, draftOrderId={}, status={}, erro={}",
                    order.getId(),
                    order.getNuvemshopDraftOrderId(),
                    exception.getStatusCode(),
                    exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("Não foi possível atualizar status do pedido na Nuvemshop. localOrderId={}, draftOrderId={}, erro={}",
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
            log.info("Draft Order não encontrada. Tentando buscar como venda. draftOrderId={}", nuvemshopId);
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
        log.warn("Pedido não encontrado na Nuvemshop. Marcando pedido local como cancelado. localOrderId={}, draftOrderId={}",
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
        return orderRepository.findByStatusPublicToken(statusPublicToken.trim())
                .map(order -> temporaryOrderStatusUrl(order.getId()))
                .orElseGet(() -> permanentStatusUrl(statusPublicToken));
    }

    public String temporaryOrderStatusUrl(UUID orderId) {
        if (orderId == null) {
            return null;
        }
        return accessUrl(createTemporaryAccessToken(orderId, null));
    }

    private String permanentStatusUrl(String statusPublicToken) {
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
        return accessUrl(createTemporaryAccessToken(null, normalizedPhone));
    }

    public String buildWhatsAppStatusMessage(OrderStatusResponse order) {
        return "Encontrei seu pedido\n\n"
                + "Pedido: " + publicOrderNumber(order) + "\n"
                + "Status: " + order.statusTitle() + "\n"
                + "Pagamento: " + order.paymentStatus() + "\n"
                + "Envio: " + order.shippingStatus() + "\n\n"
                + "Acompanhe por aqui:\n"
                + statusUrl(order);
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
                    .append(order.createdAt() == null ? "não informada" : DATE_FORMATTER.format(order.createdAt()))
                    .append("\n")
                    .append("Total: ")
                    .append(money(order.total()))
                    .append("\n")
                    .append("Status: ")
                    .append(order.statusTitle())
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
                displayPaymentStatus(order, publicStatus),
                displayShippingStatus(order, publicStatus),
                canPay(order, publicStatus),
                order.getCheckoutUrl(),
                order.getPixCopyPaste(),
                order.getPixQrCodeUrl(),
                displayShippingMethod(order, publicStatus),
                displayShippingEta(order, publicStatus),
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
                displayPaymentStatus(order, publicStatus),
                displayShippingStatus(order, publicStatus),
                canPay(order, publicStatus),
                order.getCheckoutUrl(),
                temporaryOrderStatusUrl(order.getId()),
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
            case ORDER_RECEIVED -> "Recebemos seu pedido e estamos preparando as próximas etapas.";
            case WAITING_PAYMENT -> "Use o link de pagamento para concluir seu Pix no checkout seguro.";
            case PAYMENT_CONFIRMED -> "Seu pagamento foi confirmado. Agora vamos preparar seu pedido.";
            case SEPARATING_ORDER -> "Seu pedido está em separação pela equipe da loja.";
            case SHIPPED -> "Seu pedido saiu para entrega. Use o rastreio para acompanhar.";
            case DELIVERED -> "Entrega confirmada. Obrigado por comprar com a L&LFashion.";
            case CANCELLED -> "O pedido foi cancelado. Fale com a equipe se precisar de ajuda.";
            case REFUNDED -> "Identificamos estorno do pagamento deste pedido.";
            case ERROR -> "Encontramos um problema e uma atendente pode ajudar.";
        };
    }

    private String shippingEta(WhatsappOrder order) {
        if (order.getShippingMinDays() != null && order.getShippingMaxDays() != null) {
            return order.getShippingMinDays() + " a " + order.getShippingMaxDays() + " dias úteis";
        }
        return "Aguardando atualização da loja";
    }

    private String displayPaymentStatus(WhatsappOrder order, PublicOrderStatus publicStatus) {
        if (publicStatus == PublicOrderStatus.CANCELLED) {
            return "Cancelado";
        }
        if (publicStatus == PublicOrderStatus.REFUNDED) {
            return "Estornado";
        }
        return friendlyPayment(firstText(order.getPaymentStatus(), order.getStatus() == null ? null : order.getStatus().name()));
    }

    private String displayShippingStatus(WhatsappOrder order, PublicOrderStatus publicStatus) {
        if (publicStatus == PublicOrderStatus.CANCELLED) {
            return "Pedido cancelado";
        }
        if (publicStatus == PublicOrderStatus.REFUNDED) {
            return "Pedido cancelado";
        }
        return friendlyShipping(firstText(order.getShippingStatus(), "pending"));
    }

    private String displayShippingMethod(WhatsappOrder order, PublicOrderStatus publicStatus) {
        if (publicStatus == PublicOrderStatus.CANCELLED || publicStatus == PublicOrderStatus.REFUNDED) {
            return "Pedido cancelado";
        }
        return firstText(order.getShippingMethod(), "Aguardando atualização da loja");
    }

    private String displayShippingEta(WhatsappOrder order, PublicOrderStatus publicStatus) {
        if (publicStatus == PublicOrderStatus.CANCELLED || publicStatus == PublicOrderStatus.REFUNDED) {
            return "Este pedido não será enviado.";
        }
        return shippingEta(order);
    }

    private String statusUrl(OrderStatusResponse order) {
        if (order == null) {
            return null;
        }
        if (order.localOrderId() != null) {
            return temporaryOrderStatusUrl(order.localOrderId());
        }
        return statusUrl(order.statusPublicToken());
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
        return "Não encontrei nenhum pedido para este número de WhatsApp"
                + phoneSuffix(normalizedPhone)
                + ".";
    }

    private String noActiveOrdersMessage(String normalizedPhone) {
        return "Não encontrei nenhum pedido ativo para este número de WhatsApp"
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
        return "Aguardando atualização da loja";
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

    private String accessUrl(String rawAccessToken) {
        String baseUrl = checkoutProperties.resolvedFrontendBaseUrl();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return baseUrl + "pedido/status?access=" + URLEncoder.encode(rawAccessToken, StandardCharsets.UTF_8);
    }

    private String createTemporaryAccessToken(UUID orderId, String customerPhone) {
        accessTokenRepository.deleteExpired(LocalDateTime.now());
        String rawToken = generateRawAccessToken();
        accessTokenRepository.save(OrderStatusAccessToken.builder()
                .accessTokenHash(hash(rawToken))
                .orderId(orderId)
                .customerPhone(trimToNull(customerPhone))
                .expiresAt(LocalDateTime.now().plus(checkoutProperties.resolvedOrderStatusAccessExpiration()))
                .build());
        return rawToken;
    }

    private String generateRawAccessToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "osa_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte item : hashed) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não disponível para tokens temporários.", exception);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> phoneCandidates(String normalizedPhone) {
        if (!StringUtils.hasText(normalizedPhone)) {
            return List.of();
        }

        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(normalizedPhone);

        String withoutCountryCode = normalizedPhone.startsWith("55") && normalizedPhone.length() > 11
                ? normalizedPhone.substring(2)
                : normalizedPhone;
        candidates.add(withoutCountryCode);

        if (!normalizedPhone.startsWith("55")) {
            candidates.add("55" + normalizedPhone);
        }

        for (String candidate : new ArrayList<>(candidates)) {
            addNinthDigitVariants(candidates, candidate);
            if (candidate.startsWith("55")) {
                addNinthDigitVariants(candidates, candidate.substring(2));
            } else {
                addNinthDigitVariants(candidates, "55" + candidate);
            }
        }

        return candidates.stream()
                .filter(StringUtils::hasText)
                .toList();
    }

    private void addNinthDigitVariants(Set<String> candidates, String phone) {
        if (!StringUtils.hasText(phone)) {
            return;
        }
        String digits = onlyDigits(phone);
        if (digits.length() == 10) {
            candidates.add(digits.substring(0, 2) + "9" + digits.substring(2));
            candidates.add("55" + digits.substring(0, 2) + "9" + digits.substring(2));
        }
        if (digits.length() == 11 && digits.charAt(2) == '9') {
            candidates.add(digits.substring(0, 2) + digits.substring(3));
            candidates.add("55" + digits.substring(0, 2) + digits.substring(3));
        }
        if (digits.length() == 12 && digits.startsWith("55")) {
            String national = digits.substring(2);
            candidates.add(national);
            addNinthDigitVariants(candidates, national);
        }
        if (digits.length() == 13 && digits.startsWith("55") && digits.charAt(4) == '9') {
            String nationalWithoutNinth = digits.substring(2, 4) + digits.substring(5);
            candidates.add(nationalWithoutNinth);
            candidates.add("55" + nationalWithoutNinth);
        }
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
