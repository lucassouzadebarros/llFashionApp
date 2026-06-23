package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.config.WhatsAppProperties;
import br.com.llfashion.whatsappcheckout.dto.request.CreateDraftOrderItemRequest;
import br.com.llfashion.whatsappcheckout.dto.request.CreateDraftOrderRequest;
import br.com.llfashion.whatsappcheckout.dto.response.CreateDraftOrderResponse;
import br.com.llfashion.whatsappcheckout.dto.response.WhatsAppWebhookResponse;
import br.com.llfashion.whatsappcheckout.entity.WhatsAppInboundMessageLog;
import br.com.llfashion.whatsappcheckout.entity.WhatsAppFlowSession;
import br.com.llfashion.whatsappcheckout.exception.BusinessException;
import br.com.llfashion.whatsappcheckout.repository.WhatsAppInboundMessageLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.NumberFormat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WhatsAppWebhookService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookService.class);
    private static final String SUBSCRIBE_MODE = "subscribe";
    private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");

    private final WhatsAppProperties properties;
    private final WebhookEventLogService webhookEventLogService;
    private final DraftOrderService draftOrderService;
    private final WhatsAppCheckoutSessionService checkoutSessionService;
    private final WhatsAppPaymentMessageService whatsAppPaymentMessageService;
    private final WhatsAppFlowService whatsAppFlowService;
    private final WhatsAppFlowMessageService whatsAppFlowMessageService;
    private final StorefrontCartService storefrontCartService;
    private final OrderTrackingService orderTrackingService;
    private final WhatsAppInboundMessageLogRepository inboundMessageLogRepository;
    private final ObjectMapper objectMapper;

    public WhatsAppWebhookService(
            WhatsAppProperties properties,
            WebhookEventLogService webhookEventLogService,
            DraftOrderService draftOrderService,
            WhatsAppCheckoutSessionService checkoutSessionService,
            WhatsAppPaymentMessageService whatsAppPaymentMessageService,
            WhatsAppFlowService whatsAppFlowService,
            WhatsAppFlowMessageService whatsAppFlowMessageService,
            StorefrontCartService storefrontCartService,
            OrderTrackingService orderTrackingService,
            WhatsAppInboundMessageLogRepository inboundMessageLogRepository,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.webhookEventLogService = webhookEventLogService;
        this.draftOrderService = draftOrderService;
        this.checkoutSessionService = checkoutSessionService;
        this.whatsAppPaymentMessageService = whatsAppPaymentMessageService;
        this.whatsAppFlowService = whatsAppFlowService;
        this.whatsAppFlowMessageService = whatsAppFlowMessageService;
        this.storefrontCartService = storefrontCartService;
        this.orderTrackingService = orderTrackingService;
        this.inboundMessageLogRepository = inboundMessageLogRepository;
        this.objectMapper = objectMapper;
    }

    public String verifyWebhook(String mode, String verifyToken, String challenge) {
        if (!StringUtils.hasText(properties.verifyToken())) {
            throw new BusinessException("WHATSAPP_VERIFY_TOKEN não configurado nas variáveis de ambiente.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        if (SUBSCRIBE_MODE.equals(mode) && properties.verifyToken().equals(verifyToken)) {
            log.info("Webhook WhatsApp verificado com sucesso.");
            return challenge;
        }

        throw new BusinessException("Token de verificação do webhook WhatsApp inválido.", HttpStatus.FORBIDDEN);
    }

    public WhatsAppWebhookResponse receiveWebhook(JsonNode payload) {
        webhookEventLogService.saveWhatsAppWebhook(payload, false);

        List<CreateDraftOrderResponse> createdOrders = new ArrayList<>();
        List<JsonNode> messages = extractMessages(payload);
        String phoneNumberId = resolvePhoneNumberId(payload);
        int cartSessionsStarted = 0;
        int textRepliesSent = 0;
        int paymentLinksSent = 0;
        int flowsSent = 0;

        for (JsonNode message : messages) {
            String messageType = message.path("type").asText();
            if ("order".equals(messageType)) {
                OrderHandlingResult result = handleOrderMessage(payload, message, phoneNumberId);
                if (result.flowSent()) {
                    flowsSent++;
                }
                if (result.textReplySent()) {
                    cartSessionsStarted++;
                    textRepliesSent++;
                }
                continue;
            }

            if (!claimInboundMessage(message)) {
                continue;
            }

            if ("interactive".equals(messageType)) {
                InteractiveHandlingResult result = handleInteractiveMessage(payload, message, phoneNumberId);
                if (result.createdOrder() != null) {
                    createdOrders.add(result.createdOrder());
                }
                if (result.textReplySent()) {
                    textRepliesSent++;
                }
                if (result.paymentLinkSent()) {
                    paymentLinksSent++;
                }
                continue;
            }

            if ("text".equals(messageType)) {
                String textBody = message.path("text").path("body").asText();
                if (isStatusRequest(textBody)) {
                    String customerPhone = message.path("from").asText();
                    var result = orderTrackingService.findOrdersByPhone(customerPhone);
                    String reply = orderTrackingService.buildWhatsAppStatusMessage(result, customerPhone);
                    if (whatsAppPaymentMessageService.sendText(customerPhone, reply, phoneNumberId)) {
                        textRepliesSent++;
                    }
                    continue;
                }

                if (isBuyNowRequest(textBody)) {
                    if (whatsAppPaymentMessageService.sendShoppingCta(
                            message.path("from").asText(),
                            resolveCustomerName(payload),
                            phoneNumberId
                    )) {
                        textRepliesSent++;
                    }
                    continue;
                }

                WhatsAppCheckoutSessionService.TextSessionResult result = checkoutSessionService.handleCustomerText(
                        message.path("from").asText(),
                        textBody
                );

                if (result.handled()) {
                    if (StringUtils.hasText(result.replyMessage())
                            && whatsAppPaymentMessageService.sendText(result.customerPhone(), result.replyMessage(), phoneNumberId)) {
                        textRepliesSent++;
                    }

                    if (result.createdOrder() != null) {
                        createdOrders.add(result.createdOrder());
                        if (whatsAppPaymentMessageService.sendPaymentLink(
                                result.customerPhone(),
                                result.customerName(),
                                result.createdOrder(),
                                phoneNumberId
                        )) {
                            paymentLinksSent++;
                        }
                    }
                    continue;
                }

                if (isHumanRequest(textBody)) {
                    checkoutSessionService.markWaitingHumanAttendant(
                            message.path("from").asText(),
                            resolveCustomerName(payload),
                            message.path("id").asText(null)
                    );
                    if (whatsAppPaymentMessageService.sendText(
                            message.path("from").asText(),
                            "Perfeito! Vou chamar uma atendente para te ajudar.",
                            phoneNumberId
                    )) {
                        textRepliesSent++;
                    }
                    continue;
                }

                if (isInitialShoppingMessage(textBody)) {
                    boolean sent = whatsAppPaymentMessageService.sendInitialMenu(
                            message.path("from").asText(),
                            resolveCustomerName(payload),
                            phoneNumberId
                    );
                    if (!sent) {
                        sent = whatsAppPaymentMessageService.sendText(
                                message.path("from").asText(),
                                buildInitialMenuMessage(message.path("from").asText(), resolveCustomerName(payload)),
                                phoneNumberId
                        );
                    }
                    if (sent) {
                        textRepliesSent++;
                    }
                }
            }
        }

        return new WhatsAppWebhookResponse(
                createdOrders.size(),
                createdOrders,
                "Webhook recebido. Sessões de carrinho iniciadas: " + cartSessionsStarted
                        + ". Flow(s) enviado(s): " + flowsSent
                        + ". Resposta(s) enviada(s): " + textRepliesSent
                        + ". Pedido(s) criado(s): " + createdOrders.size()
                        + ". Link(s) de pagamento enviado(s): " + paymentLinksSent
        );
    }

    private boolean claimInboundMessage(JsonNode message) {
        String messageId = message.path("id").asText(null);
        if (!StringUtils.hasText(messageId)) {
            return true;
        }

        String customerPhone = message.path("from").asText(null);
        String messageType = message.path("type").asText(null);
        try {
            inboundMessageLogRepository.save(WhatsAppInboundMessageLog.builder()
                    .whatsappMessageId(messageId.trim())
                    .customerPhone(customerPhone)
                    .messageType(messageType)
                    .build());
            return true;
        } catch (DataIntegrityViolationException exception) {
            log.info("Mensagem WhatsApp duplicada ignorada. whatsappMessageId={}, type={}, phone={}",
                    messageId,
                    messageType,
                    maskPhone(customerPhone));
            return false;
        }
    }

    private OrderHandlingResult handleOrderMessage(JsonNode payload, JsonNode message, String phoneNumberId) {
        String whatsappMessageId = message.path("id").asText(null);
        if (draftOrderService.hasOrderForWhatsAppMessage(whatsappMessageId)) {
            log.info("Carrinho WhatsApp já possui pedido local criado. whatsappMessageId={}", whatsappMessageId);
            return OrderHandlingResult.none();
        }

        String customerPhone = message.path("from").asText();
        boolean storefrontLinkSent = whatsAppPaymentMessageService.sendText(
                customerPhone,
                buildCatalogRedirectMessage(customerPhone),
                phoneNumberId
        );
        if (storefrontLinkSent) {
            return new OrderHandlingResult(false, true);
        }

        if (whatsAppFlowMessageService.canSendFlow()) {
            List<WhatsAppFlowCartService.CartItemInput> flowCartItems = toFlowCartItems(message);
            if (!flowCartItems.isEmpty()) {
                WhatsAppFlowSession flowSession = whatsAppFlowService.startFlowForCart(
                        message.path("from").asText(),
                        resolveCustomerName(payload),
                        whatsappMessageId,
                        flowCartItems
                );
                boolean sent = whatsAppFlowMessageService.sendProductPurchaseFlow(
                        flowSession.getCustomerPhone(),
                        flowSession.getCustomerName(),
                        "seu carrinho",
                        flowSession.getFlowToken(),
                        phoneNumberId
                );
                if (sent) {
                    return new OrderHandlingResult(true, false);
                }
                log.warn("WhatsApp Flow não enviado. Usando fluxo conversacional legado. whatsappMessageId={}", whatsappMessageId);
                String messageToCustomer = "Recebi seu carrinho, mas não consegui abrir o checkout interativo agora.\n\n"
                        + "Vou seguir pelo atendimento automático por mensagens.";
                boolean textSent = whatsAppPaymentMessageService.sendText(customerPhone, messageToCustomer, phoneNumberId);
                if (textSent) {
                    return new OrderHandlingResult(false, true);
                }
            }
        }

        WhatsAppCheckoutSessionService.CartSessionResult session;
        try {
            CreateDraftOrderRequest request = toDraftOrderRequest(payload, message);
            session = checkoutSessionService.startCartSession(request, whatsappMessageId);
        } catch (BusinessException exception) {
            log.warn("Não foi possível iniciar pedido do WhatsApp. whatsappMessageId={}, erro={}",
                    whatsappMessageId,
                    exception.getMessage());
            String messageToCustomer = "Não consegui iniciar seu pedido porque um item do carrinho não está sincronizado com a loja.\n\n"
                    + exception.getMessage()
                    + "\n\nAtualize os produtos da loja ou remova esse item do carrinho e tente novamente.";
            boolean sent = whatsAppPaymentMessageService.sendText(customerPhone, messageToCustomer, phoneNumberId);
            return new OrderHandlingResult(false, sent);
        }

        if (session.existing()) {
            log.info("Carrinho WhatsApp já possui sessão registrada. status={}, phone={}", session.status(), maskPhone(session.customerPhone()));
            return OrderHandlingResult.none();
        }

        String messageToCustomer = switch (session.status()) {
            case INSUFFICIENT_STOCK -> buildInsufficientStockMessage(session);
            case BELOW_MINIMUM -> buildMinimumOrderMessage(session);
            case AWAITING_FULL_NAME -> buildFullNameRequestMessage(session);
            default -> null;
        };

        boolean sent = StringUtils.hasText(messageToCustomer)
                && whatsAppPaymentMessageService.sendText(session.customerPhone(), messageToCustomer, phoneNumberId);
        return new OrderHandlingResult(false, sent);
    }

    private boolean isInitialShoppingMessage(String text) {
        String normalized = normalize(text);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return containsAny(normalized,
                "OI",
                "OLA",
                "QUERO COMPRAR",
                "COMPRAR",
                "CATALOGO",
                "PRECO",
                "NOVIDADE",
                "PROMO",
                "PROMOCAO",
                "CARRINHO");
    }

    private boolean isHumanRequest(String text) {
        String normalized = normalize(text);
        return containsAny(normalized, "ATENDENTE", "HUMANO", "FALAR COM ATENDENTE", "SUPORTE");
    }

    private boolean isStatusRequest(String text) {
        String normalized = normalize(text);
        return containsAny(normalized, "STATUS", "MEU PEDIDO", "RASTREIO", "RASTREAR", "ACOMPANHAR PEDIDO");
    }

    private boolean isBuyNowRequest(String text) {
        String normalized = normalize(text);
        return containsAny(normalized,
                "COMPRAR AGORA",
                "COMPRAR POR CATEGORIA",
                "VER CATALOGO",
                "CATALOGO",
                "VER CARRINHO");
    }

    private String entryPointForMessage(String text) {
        String normalized = normalize(text);
        if (containsAny(normalized, "NOVIDADE")) {
            return "VIEW_NEW";
        }
        if (containsAny(normalized, "PROMO", "PROMOCAO")) {
            return "VIEW_PROMOS";
        }
        if (containsAny(normalized, "CARRINHO")) {
            return "VIEW_CART";
        }
        if (containsAny(normalized, "COMPRAR", "CATALOGO", "PRECO")) {
            return "BUY_CATEGORY";
        }
        return "MENU";
    }

    private String buildInitialMenuMessage(String customerPhone, String customerName) {
        String name = StringUtils.hasText(customerName) && !"Cliente WhatsApp".equals(customerName)
                ? ", " + customerName.trim()
                : "";
        return "Bem-vinda a L&LFashion" + name + "!\n\n"
                + "Trabalhamos com moda feminina no atacado.\n"
                + "Pedido mínimo no atacado: R$ 200,00.\n\n"
                + "Como deseja continuar?\n\n"
                + "Responda uma das opções abaixo:\n"
                + "1. Comprar agora\n"
                + "2. Acompanhar pedido\n"
                + "3. Falar com atendente";
    }

    private String buildCatalogRedirectMessage(String customerPhone) {
        return "Recebi seu carrinho pelo catálogo do WhatsApp.\n\n"
                + "Agora estamos finalizando pedidos pelo checkout visual da L&LFashion, com fotos, estoque atualizado e pedido mínimo de R$ 200,00.\n\n"
                + "Abra o link abaixo para montar ou revisar seu pedido:\n"
                + storefrontCartService.storefrontUrlForPhone(customerPhone);
    }

    private List<WhatsAppFlowCartService.CartItemInput> toFlowCartItems(JsonNode message) {
        List<WhatsAppFlowCartService.CartItemInput> items = new ArrayList<>();
        JsonNode productItems = message.path("order").path("product_items");
        if (!productItems.isArray() || productItems.isEmpty()) {
            return items;
        }

        for (JsonNode productItem : productItems) {
            String productRetailerId = productItem.path("product_retailer_id").asText(null);
            int quantity = productItem.path("quantity").asInt(1);
            if (StringUtils.hasText(productRetailerId)) {
                items.add(new WhatsAppFlowCartService.CartItemInput(productRetailerId, quantity));
            }
        }
        return items;
    }

    private InteractiveHandlingResult handleInteractiveMessage(JsonNode payload, JsonNode message, String phoneNumberId) {
        JsonNode buttonReply = message.path("interactive").path("button_reply");
        if (!buttonReply.isMissingNode() && !buttonReply.isNull()) {
            return handleButtonReply(payload, message, buttonReply.path("id").asText(null), phoneNumberId);
        }

        JsonNode nfmReply = message.path("interactive").path("nfm_reply");
        if (nfmReply.isMissingNode() || nfmReply.isNull()) {
            return InteractiveHandlingResult.none();
        }

        String responseJson = nfmReply.path("response_json").asText(null);
        if (!StringUtils.hasText(responseJson)) {
            return InteractiveHandlingResult.none();
        }

        try {
            CreateDraftOrderResponse order = whatsAppFlowService.createOrderFromFlowReply(objectMapper.readTree(responseJson));
            boolean paymentSent = order != null && whatsAppPaymentMessageService.sendPaymentLink(
                    message.path("from").asText(),
                    resolveCustomerName(payload),
                    order,
                    phoneNumberId
            );
            return new InteractiveHandlingResult(order, paymentSent, paymentSent);
        } catch (Exception exception) {
            log.warn("Não foi possível processar retorno final do WhatsApp Flow. erro={}", exception.getMessage());
            return InteractiveHandlingResult.none();
        }
    }

    private InteractiveHandlingResult handleButtonReply(JsonNode payload, JsonNode message, String buttonId, String phoneNumberId) {
        String customerPhone = message.path("from").asText();
        if (!StringUtils.hasText(buttonId)) {
            return InteractiveHandlingResult.none();
        }

        boolean sent = switch (buttonId) {
            case WhatsAppPaymentMessageService.MENU_BUY_NOW -> whatsAppPaymentMessageService.sendShoppingCta(
                    customerPhone,
                    resolveCustomerName(payload),
                    phoneNumberId
            );
            case WhatsAppPaymentMessageService.MENU_TRACK_ORDER -> {
                var result = orderTrackingService.findOrdersByPhone(customerPhone);
                String reply = result.found()
                        ? orderTrackingService.buildWhatsAppStatusMessage(result, customerPhone)
                        : result.message() + "\n\nSe o pedido foi feito por outro telefone, fale com uma atendente para localizar.";
                yield whatsAppPaymentMessageService.sendText(customerPhone, reply, phoneNumberId);
            }
            case WhatsAppPaymentMessageService.MENU_HUMAN_ATTENDANT -> {
                checkoutSessionService.markWaitingHumanAttendant(
                        customerPhone,
                        resolveCustomerName(payload),
                        message.path("id").asText(null)
                );
                yield whatsAppPaymentMessageService.sendText(
                        customerPhone,
                        "Perfeito! Vou chamar uma atendente para te ajudar.",
                        phoneNumberId
                );
            }
            default -> false;
        };

        return new InteractiveHandlingResult(null, false, sent);
    }

    private List<JsonNode> extractMessages(JsonNode payload) {
        List<JsonNode> messages = new ArrayList<>();
        if (payload == null) {
            return messages;
        }

        for (JsonNode messagesNode : payload.findValues("messages")) {
            if (messagesNode.isArray()) {
                messagesNode.forEach(messages::add);
            }
        }
        return messages;
    }

    private CreateDraftOrderRequest toDraftOrderRequest(JsonNode payload, JsonNode message) {
        String phone = message.path("from").asText();
        String customerName = resolveCustomerName(payload);
        List<CreateDraftOrderItemRequest> items = new ArrayList<>();

        JsonNode productItems = message.path("order").path("product_items");
        if (!productItems.isArray() || productItems.isEmpty()) {
            throw new BusinessException("Webhook WhatsApp de pedido não possui product_items.");
        }

        for (JsonNode productItem : productItems) {
            String productRetailerId = productItem.path("product_retailer_id").asText();
            int quantity = productItem.path("quantity").asInt(1);

            if (!StringUtils.hasText(productRetailerId)) {
                throw new BusinessException("Item do pedido WhatsApp sem product_retailer_id.");
            }
            if (quantity <= 0) {
                throw new BusinessException("Quantidade do item WhatsApp deve ser maior que zero.");
            }

            items.add(toDraftOrderItem(productRetailerId, quantity));
        }

        return new CreateDraftOrderRequest(
                customerName,
                "Cliente",
                "",
                phone,
                items
        );
    }

    private CreateDraftOrderItemRequest toDraftOrderItem(String productRetailerId, int quantity) {
        if (isNumeric(productRetailerId)) {
            return new CreateDraftOrderItemRequest(Long.valueOf(productRetailerId), null, quantity);
        }
        return new CreateDraftOrderItemRequest(null, productRetailerId, quantity);
    }

    private boolean isNumeric(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            Long.valueOf(value.trim());
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String resolveCustomerName(JsonNode payload) {
        if (payload == null) {
            return "Cliente WhatsApp";
        }

        for (JsonNode contactsNode : payload.findValues("contacts")) {
            if (!contactsNode.isArray() || contactsNode.isEmpty()) {
                continue;
            }

            String name = contactsNode.get(0).path("profile").path("name").asText();
            if (StringUtils.hasText(name)) {
                return name;
            }
        }

        return "Cliente WhatsApp";
    }

    private String resolvePhoneNumberId(JsonNode payload) {
        if (payload == null) {
            return null;
        }

        JsonNode phoneNumberId = payload.findValue("phone_number_id");
        return phoneNumberId == null || phoneNumberId.isNull() ? null : phoneNumberId.asText();
    }

    private String buildMinimumOrderMessage(WhatsAppCheckoutSessionService.CartSessionResult session) {
        return "Recebi seu carrinho, mas o pedido mínimo da L&LFashion é de "
                + money(session.minimumOrderTotal()) + ".\n\n"
                + "Subtotal do carrinho: " + money(session.subtotal()) + "\n"
                + "Faltam: " + money(session.missingAmount()) + "\n\n"
                + "Adicione mais produtos ao carrinho e envie novamente para eu gerar o link de pagamento.";
    }

    private String buildInsufficientStockMessage(WhatsAppCheckoutSessionService.CartSessionResult session) {
        String itemMessage = StringUtils.hasText(session.stockIssueMessage())
                ? session.stockIssueMessage()
                : "um dos itens do carrinho não tem estoque suficiente";

        return "Recebi seu carrinho, mas não consigo gerar o pedido porque a quantidade solicitada não está disponível.\n\n"
                + itemMessage + ".\n\n"
                + "Ajuste a quantidade no carrinho e envie novamente para eu continuar.";
    }

    private String buildFullNameRequestMessage(WhatsAppCheckoutSessionService.CartSessionResult session) {
        String name = StringUtils.hasText(session.customerName()) ? session.customerName().trim() : "Cliente";
        return "Olá, " + name + "! Recebi seu carrinho no valor de " + money(session.subtotal()) + ".\n\n"
                + "Para gerar seu link de pagamento, me envie o nome completo do cliente.";
    }

    private String money(java.math.BigDecimal value) {
        return NumberFormat.getCurrencyInstance(BRAZIL).format(value == null ? java.math.BigDecimal.ZERO : value);
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "***" + digits.substring(digits.length() - 4);
    }

    private record OrderHandlingResult(boolean flowSent, boolean textReplySent) {
        private static OrderHandlingResult none() {
            return new OrderHandlingResult(false, false);
        }
    }

    private record InteractiveHandlingResult(
            CreateDraftOrderResponse createdOrder,
            boolean paymentLinkSent,
            boolean textReplySent
    ) {
        private static InteractiveHandlingResult none() {
            return new InteractiveHandlingResult(null, false, false);
        }
    }
}
