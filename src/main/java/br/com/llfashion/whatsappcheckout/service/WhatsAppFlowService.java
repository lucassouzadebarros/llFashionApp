package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.dto.response.CreateDraftOrderResponse;
import br.com.llfashion.whatsappcheckout.entity.ProductMapping;
import br.com.llfashion.whatsappcheckout.entity.WhatsAppFlowSession;
import br.com.llfashion.whatsappcheckout.enums.WhatsAppFlowSessionStatus;
import br.com.llfashion.whatsappcheckout.exception.BusinessException;
import br.com.llfashion.whatsappcheckout.repository.WhatsAppFlowSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WhatsAppFlowService {

    private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");
    private static final int MAX_QUANTITY_OPTIONS = 20;
    private static final String CART_FLOW_MARKER = "CART";
    private static final String SHOP_FLOW_MARKER = "SHOP";
    private static final String PRODUCT_PLACEHOLDER_IMAGE_URL =
            "https://placehold.co/1200x800/f3faf6/047857.png?text=L%26LFashion";
    private static final Logger log = LoggerFactory.getLogger(WhatsAppFlowService.class);

    private final WhatsAppFlowSessionRepository flowSessionRepository;
    private final ProductMappingService productMappingService;
    private final WhatsAppFlowCartService flowCartService;
    private final WhatsAppPaymentMessageService paymentMessageService;
    private final WhatsAppProductSearchService productSearchService;

    public WhatsAppFlowService(
            WhatsAppFlowSessionRepository flowSessionRepository,
            ProductMappingService productMappingService,
            WhatsAppFlowCartService flowCartService,
            WhatsAppPaymentMessageService paymentMessageService,
            WhatsAppProductSearchService productSearchService
    ) {
        this.flowSessionRepository = flowSessionRepository;
        this.productMappingService = productMappingService;
        this.flowCartService = flowCartService;
        this.paymentMessageService = paymentMessageService;
        this.productSearchService = productSearchService;
    }

    @Transactional
    public WhatsAppFlowSession startFlowForCart(
            String customerPhone,
            String customerName,
            String whatsappMessageId,
            List<WhatsAppFlowCartService.CartItemInput> items
    ) {
        if (StringUtils.hasText(whatsappMessageId)) {
            java.util.Optional<WhatsAppFlowSession> existingFlow = flowSessionRepository.findByWhatsappMessageId(whatsappMessageId.trim());
            if (existingFlow.isPresent()) {
                return existingFlow.get();
            }
        }

        flowCartService.startCartFromWhatsAppOrder(
                customerPhone,
                customerName,
                whatsappMessageId,
                items
        );

        WhatsAppFlowSession session = WhatsAppFlowSession.builder()
                .flowToken(UUID.randomUUID().toString())
                .customerPhone(onlyDigits(customerPhone))
                .customerName(trimToNull(customerName))
                .whatsappMessageId(trimToNull(whatsappMessageId))
                .productRetailerId(CART_FLOW_MARKER)
                .status(WhatsAppFlowSessionStatus.FLOW_STARTED)
                .build();

        return flowSessionRepository.save(session);
    }

    @Transactional
    public WhatsAppFlowSession startShoppingFlow(
            String customerPhone,
            String customerName,
            String whatsappMessageId,
            String entryPoint
    ) {
        if (StringUtils.hasText(whatsappMessageId)) {
            java.util.Optional<WhatsAppFlowSession> existingFlow = flowSessionRepository.findByWhatsappMessageId(whatsappMessageId.trim());
            if (existingFlow.isPresent()) {
                return existingFlow.get();
            }
        }

        WhatsAppFlowSession session = WhatsAppFlowSession.builder()
                .flowToken(UUID.randomUUID().toString())
                .customerPhone(onlyDigits(customerPhone))
                .customerName(trimToNull(customerName))
                .whatsappMessageId(trimToNull(whatsappMessageId))
                .productRetailerId(SHOP_FLOW_MARKER + ":" + (StringUtils.hasText(entryPoint) ? entryPoint.trim().toUpperCase(Locale.ROOT) : "MENU"))
                .status(WhatsAppFlowSessionStatus.FLOW_STARTED)
                .build();

        return flowSessionRepository.save(session);
    }

    @Transactional
    public WhatsAppFlowSession startFlowForProduct(
            String customerPhone,
            String customerName,
            String whatsappMessageId,
            String productRetailerId
    ) {
        ProductMapping selectedMapping = resolveProductMapping(productRetailerId);
        WhatsAppFlowSession session = WhatsAppFlowSession.builder()
                .flowToken(UUID.randomUUID().toString())
                .customerPhone(onlyDigits(customerPhone))
                .customerName(trimToNull(customerName))
                .whatsappMessageId(trimToNull(whatsappMessageId))
                .productRetailerId(trimToNull(productRetailerId))
                .nuvemshopProductId(selectedMapping.getNuvemshopProductId())
                .nuvemshopVariantId(selectedMapping.getNuvemshopVariantId())
                .status(WhatsAppFlowSessionStatus.PRODUCT_SELECTED)
                .build();

        return flowSessionRepository.save(session);
    }

    @Transactional
    public Map<String, Object> handleDataExchange(JsonNode request) {
        if (isHealthCheck(request)) {
            return Map.of(
                    "version", "3.0",
                    "data", Map.of(
                            "status", "active"
                    )
            );
        }

        JsonNode data = request.path("data");
        String flowToken = firstText(request, data, "flow_token", "flowToken");
        if (!StringUtils.hasText(flowToken)) {
            throw new BusinessException("flow_token é obrigatório para o WhatsApp Flow.");
        }

        WhatsAppFlowSession session = flowSessionRepository.findByFlowToken(flowToken.trim())
                .orElseThrow(() -> new BusinessException("Sessão do WhatsApp Flow não encontrada: " + flowToken));

        String action = text(request, "action");
        String screen = text(request, "screen");

        if (hasData(data, "error", "error_message", "errorMessage")) {
            return flowClientErrorResponse(session, data);
        }

        if (hasData(data, "main_action", "mainAction")) {
            return handleMainMenuAction(session, data);
        }

        if (hasData(data, "category_id", "categoryId")) {
            return productsResponse(session, text(data, "category_id", "categoryId"));
        }

        if (hasData(data, "product_id", "productId")) {
            return productVariantsResponse(session, longValue(data, "product_id", "productId"));
        }

        if (hasData(data, "shipping_action", "shippingAction")) {
            return handleShippingAction(session, data);
        }

        if (hasData(data, "confirm_final_action", "confirmFinalAction")) {
            return handleFinalConfirmationAction(session, data);
        }

        if (hasData(data, "confirm_final", "confirmFinal")) {
            return confirmOrder(session, data);
        }

        if (hasData(data, "postal_code", "cep", "zipcode", "shipping_postal_code")) {
            return shippingFallbackResponse(session, data);
        }

        if (hasData(data, "full_name", "fullName", "customer_name", "name")) {
            return addressDataResponse(session, data);
        }

        if (hasData(data, "next_action", "nextAction")) {
            return handleCartAction(session, data, screen);
        }

        if (hasData(data, "cart_quantity", "cartQuantity")) {
            return updateCartItemQuantity(session, data);
        }

        if (hasData(data, "selected_item", "selectedItem", "selected_variant_id")) {
            return selectCartItem(session, data);
        }

        if (hasData(data, "variant_id", "variantId", "nuvemshop_variant_id")) {
            return selectVariant(session, data);
        }

        if (hasData(data, "quantity")) {
            return selectQuantity(session, data);
        }

        if (isConfirmation(screen, action, data)) {
            return confirmOrder(session, data);
        }

        if ("INIT".equalsIgnoreCase(action) || !StringUtils.hasText(screen)) {
            if (isShoppingFlow(session)) {
                return initialShoppingResponse(session, data);
            }
            if (isCartFlow(session)) {
                return cartReviewResponse(session);
            }
            return variantsResponse(session);
        }

        if (isShoppingFlow(session)) {
            return mainMenuResponse(session);
        }
        if (isCartFlow(session)) {
            return cartReviewResponse(session);
        }
        return variantsResponse(session);
    }

    private boolean isHealthCheck(JsonNode request) {
        String action = text(request, "action");
        String screen = text(request, "screen");
        JsonNode data = request == null ? null : request.path("data");
        String dataAction = text(data, "action");
        String dataType = text(data, "type");
        String requestType = text(request, "type");

        return !StringUtils.hasText(text(request, "flow_token", "flowToken"))
                && !StringUtils.hasText(text(data, "flow_token", "flowToken"))
                && ("PING".equalsIgnoreCase(action)
                || "HEALTH_CHECK".equalsIgnoreCase(action)
                || "INIT".equalsIgnoreCase(action)
                || "PING".equalsIgnoreCase(dataAction)
                || "HEALTH_CHECK".equalsIgnoreCase(dataAction)
                || "HEALTH_CHECK".equalsIgnoreCase(dataType)
                || "HEALTH_CHECK".equalsIgnoreCase(requestType)
                || (!StringUtils.hasText(action) && !StringUtils.hasText(screen)));
    }

    @Transactional
    public CreateDraftOrderResponse createOrderFromFlowReply(JsonNode flowReplyData) {
        String flowToken = text(flowReplyData, "flow_token", "flowToken");
        if (!StringUtils.hasText(flowToken)) {
            throw new BusinessException("flow_token não encontrado no retorno do Flow.");
        }

        WhatsAppFlowSession session = flowSessionRepository.findByFlowToken(flowToken.trim())
                .orElseThrow(() -> new BusinessException("Sessão do WhatsApp Flow não encontrada: " + flowToken));

        if (session.getLocalOrderId() != null) {
            return null;
        }

        if (hasData(flowReplyData, "confirm_order", "full_name", "cpf", "email")) {
            return createCartOrder(session, flowReplyData);
        }
        return null;
    }

    private Map<String, Object> flowClientErrorResponse(WhatsAppFlowSession session, JsonNode data) {
        String error = text(data, "error");
        String errorMessage = text(data, "error_message", "errorMessage");
        String lastError = preview((StringUtils.hasText(error) ? error : "flow_client_error")
                + (StringUtils.hasText(errorMessage) ? ": " + errorMessage : ""));

        session.setLastError(lastError);
        session.setStatus(WhatsAppFlowSessionStatus.ERROR);
        flowSessionRepository.save(session);

        log.warn("WhatsApp Flow client error. flowToken={}, error={}", mask(session.getFlowToken()), lastError);

        return Map.of(
                "version", "3.0",
                "data", Map.of(
                        "status", "acknowledged"
                )
        );
    }

    private Map<String, Object> initialShoppingResponse(WhatsAppFlowSession session, JsonNode data) {
        String entryPoint = firstText(data, "entry_point", "entryPoint");
        if (!StringUtils.hasText(entryPoint)) {
            entryPoint = sessionEntryPoint(session);
        }

        return switch (normalize(entryPoint)) {
            case "BUY_CATEGORY", "CATEGORIES" -> categoriesResponse(session);
            case "VIEW_NEW", "NEW", "NOVIDADES" -> productsResponse(session, WhatsAppProductSearchService.CATEGORY_NOVELTIES);
            case "VIEW_PROMOS", "PROMOS", "PROMOCOES" -> productsResponse(session, WhatsAppProductSearchService.CATEGORY_PROMOTIONS);
            case "VIEW_CART", "CART" -> cartReviewResponse(session);
            case "HUMAN" -> humanAttendantResponse(session);
            default -> mainMenuResponse(session);
        };
    }

    private Map<String, Object> mainMenuResponse(WhatsAppFlowSession session) {
        session.setStatus(WhatsAppFlowSessionStatus.FLOW_STARTED);
        flowSessionRepository.save(session);
        return Map.of(
                "version", "3.0",
                "screen", "MAIN_MENU",
                "data", Map.of(
                        "flow_token", session.getFlowToken(),
                        "minimum_order_total", money(flowCartService.minimumOrderTotal()),
                        "intro_text", "Trabalhamos com moda feminina no atacado. Pedido mínimo: "
                                + money(flowCartService.minimumOrderTotal()) + ".",
                        "main_actions", productSearchService.mainMenuOptions()
                )
        );
    }

    private Map<String, Object> handleMainMenuAction(WhatsAppFlowSession session, JsonNode data) {
        String action = normalize(text(data, "main_action", "mainAction"));
        return switch (action) {
            case "BUY_CATEGORY" -> categoriesResponse(session);
            case "VIEW_NEW" -> productsResponse(session, WhatsAppProductSearchService.CATEGORY_NOVELTIES);
            case "VIEW_PROMOS" -> productsResponse(session, WhatsAppProductSearchService.CATEGORY_PROMOTIONS);
            case "VIEW_CART" -> cartReviewResponse(session);
            case "HUMAN" -> humanAttendantResponse(session);
            default -> throw new BusinessException("Opção inicial inválida no Flow: " + action);
        };
    }

    private Map<String, Object> categoriesResponse(WhatsAppFlowSession session) {
        session.setStatus(WhatsAppFlowSessionStatus.PRODUCT_SELECTED);
        flowSessionRepository.save(session);
        return Map.of(
                "version", "3.0",
                "screen", "CATEGORIES",
                "data", Map.of(
                        "flow_token", session.getFlowToken(),
                        "categories", productSearchService.categoryOptions()
                )
        );
    }

    private Map<String, Object> productsResponse(WhatsAppFlowSession session, String categoryId) {
        List<Map<String, Object>> products = productSearchService.productOptions(categoryId);
        if (products.isEmpty()) {
            return Map.of(
                    "version", "3.0",
                    "screen", "OUT_OF_STOCK",
                    "data", Map.of(
                            "flow_token", session.getFlowToken(),
                            "message", "Não encontrei produtos disponíveis nessa seleção agora."
                    )
            );
        }

        session.setStatus(WhatsAppFlowSessionStatus.PRODUCT_SELECTED);
        flowSessionRepository.save(session);
        return Map.of(
                "version", "3.0",
                "screen", "PRODUCT_LIST",
                "data", Map.of(
                        "flow_token", session.getFlowToken(),
                        "category_title", productSearchService.titleForCategory(categoryId),
                        "products", products
                )
        );
    }

    private Map<String, Object> productVariantsResponse(WhatsAppFlowSession session, Long productId) {
        session.setNuvemshopProductId(productId);
        session.setNuvemshopVariantId(null);
        session.setQuantity(null);
        session.setSubtotal(null);
        session.setStatus(WhatsAppFlowSessionStatus.PRODUCT_SELECTED);
        flowSessionRepository.save(session);
        return variantsResponse(session);
    }

    private Map<String, Object> humanAttendantResponse(WhatsAppFlowSession session) {
        session.setStatus(WhatsAppFlowSessionStatus.WAITING_HUMAN_ATTENDANT);
        flowSessionRepository.save(session);
        return Map.of(
                "version", "3.0",
                "screen", "HUMAN_ATTENDANT",
                "data", Map.of(
                        "flow_token", session.getFlowToken(),
                        "message", "Perfeito! Vou chamar uma atendente para te ajudar."
                )
        );
    }

    private Map<String, Object> selectVariant(WhatsAppFlowSession session, JsonNode data) {
        Long variantId = longValue(data, "variant_id", "variantId", "nuvemshop_variant_id");
        ProductMapping mapping = productMappingService.findActiveByNuvemshopVariantId(variantId);
        if (!mapping.getNuvemshopProductId().equals(session.getNuvemshopProductId())) {
            throw new BusinessException("Variação selecionada não pertence ao produto da sessão do Flow.");
        }

        session.setNuvemshopVariantId(mapping.getNuvemshopVariantId());
        session.setStatus(WhatsAppFlowSessionStatus.VARIANT_SELECTED);
        flowSessionRepository.save(session);

        return quantityResponse(session, mapping);
    }

    private Map<String, Object> selectQuantity(WhatsAppFlowSession session, JsonNode data) {
        Integer quantity = intValue(data, "quantity");
        ProductMapping mapping = productMappingService.findActiveByNuvemshopVariantId(session.getNuvemshopVariantId());
        validateQuantityAgainstStock(session, mapping, quantity);

        session.setQuantity(quantity);
        session.setSubtotal(price(mapping).multiply(BigDecimal.valueOf(quantity)));
        WhatsAppFlowCartService.CartSummary summary = flowCartService.addSelectedItem(
                session.getCustomerPhone(),
                session.getCustomerName(),
                session.getWhatsappMessageId(),
                mapping,
                quantity
        );
        session.setStatus(WhatsAppFlowSessionStatus.WAITING_CONFIRMATION);
        flowSessionRepository.save(session);

        return itemAddedResponse(session, summary);
    }

    private Map<String, Object> handleCartAction(WhatsAppFlowSession session, JsonNode data, String screen) {
        String nextAction = text(data, "next_action", "nextAction");
        if (isCartFlow(session) || isCartReviewScreen(screen)) {
            return handleCartReviewAction(session, nextAction);
        }

        if ("CANCEL".equalsIgnoreCase(nextAction)) {
            return cancelledResponse(session);
        }

        WhatsAppFlowCartService.CartSummary summary = addCurrentItemToCartIfNeeded(session);

        if ("ADD_MORE".equalsIgnoreCase(nextAction)) {
            return categoriesResponse(session);
        }

        if ("VIEW_CART".equalsIgnoreCase(nextAction)) {
            return cartReviewResponse(session);
        }

        if ("CHECKOUT".equalsIgnoreCase(nextAction)) {
            if (!summary.canCheckout()) {
                return minimumNotReachedResponse(session, summary);
            }
            return customerDataResponse(session, summary);
        }

        throw new BusinessException("Acao do carrinho invalida no Flow: " + nextAction);
    }

    private boolean isCartReviewScreen(String screen) {
        String normalized = normalize(screen);
        return normalized.startsWith("CART_REVIEW") || "MINIMUM_NOT_REACHED".equals(normalized);
    }

    private Map<String, Object> handleCartReviewAction(WhatsAppFlowSession session, String nextAction) {
        if ("CANCEL".equalsIgnoreCase(nextAction)) {
            return cancelledResponse(session);
        }

        if ("ADJUST_ITEM".equalsIgnoreCase(nextAction)) {
            return selectCartItemResponse(session);
        }

        if ("ADD_MORE".equalsIgnoreCase(nextAction)) {
            return categoriesResponse(session);
        }

        if ("VIEW_CART".equalsIgnoreCase(nextAction)) {
            return cartReviewResponse(session);
        }

        if ("CHECKOUT".equalsIgnoreCase(nextAction)) {
            WhatsAppFlowCartService.CartSummary summary = flowCartService.currentSummary(session.getCustomerPhone());
            if (!summary.canCheckout()) {
                return minimumNotReachedResponse(session, summary);
            }
            session.setStatus(WhatsAppFlowSessionStatus.WAITING_CONFIRMATION);
            flowSessionRepository.save(session);
            return customerDataResponse(session, summary);
        }

        throw new BusinessException("Acao do carrinho invalida no Flow: " + nextAction);
    }

    private Map<String, Object> selectCartItem(WhatsAppFlowSession session, JsonNode data) {
        Long variantId = longValue(data, "selected_item", "selectedItem", "selected_variant_id");
        WhatsAppFlowCartService.CartItemOption option = flowCartService.cartItemOption(session.getCustomerPhone(), variantId);
        return cartQuantityResponse(session, option);
    }

    private Map<String, Object> updateCartItemQuantity(WhatsAppFlowSession session, JsonNode data) {
        Long variantId = longValue(data, "selected_item", "selectedItem", "selected_variant_id");
        Integer quantity = intValue(data, "cart_quantity", "cartQuantity");
        flowCartService.updateItemQuantity(session.getCustomerPhone(), variantId, quantity);
        return cartReviewResponse(session, "CART_REVIEW_UPDATED");
    }

    private WhatsAppFlowCartService.CartSummary addCurrentItemToCartIfNeeded(WhatsAppFlowSession session) {
        if (session.getStatus() == WhatsAppFlowSessionStatus.WAITING_CONFIRMATION
                || session.getStatus() == WhatsAppFlowSessionStatus.ORDER_CREATED) {
            return flowCartService.currentSummary(session.getCustomerPhone());
        }

        ProductMapping mapping = productMappingService.findActiveByNuvemshopVariantId(session.getNuvemshopVariantId());
        Integer quantity = session.getQuantity();
        validateQuantityAgainstStock(session, mapping, quantity);

        WhatsAppFlowCartService.CartSummary summary = flowCartService.addSelectedItem(
                session.getCustomerPhone(),
                session.getCustomerName(),
                session.getWhatsappMessageId(),
                mapping,
                quantity
        );
        session.setStatus(WhatsAppFlowSessionStatus.WAITING_CONFIRMATION);
        flowSessionRepository.save(session);
        return summary;
    }

    private Map<String, Object> itemAddedResponse(WhatsAppFlowSession session, WhatsAppFlowCartService.CartSummary summary) {
        return Map.of(
                "version", "3.0",
                "screen", "ITEM_ADDED",
                "data", Map.of(
                        "flow_token", session.getFlowToken(),
                        "cart_summary", summaryTextWithNotice(summary),
                        "message", summary.canCheckout()
                                ? "Produto adicionado ao carrinho. Pedido mínimo atingido."
                                : "Produto adicionado ao carrinho. Adicione mais peças para finalizar.",
                        "next_actions", summary.canCheckout()
                                ? List.of(
                                Map.of("id", "CHECKOUT", "title", "Finalizar pedido"),
                                Map.of("id", "ADD_MORE", "title", "Adicionar mais peças"),
                                Map.of("id", "VIEW_CART", "title", "Ver carrinho"),
                                Map.of("id", "CANCEL", "title", "Cancelar pedido")
                        )
                                : List.of(
                                Map.of("id", "ADD_MORE", "title", "Adicionar mais peças"),
                                Map.of("id", "VIEW_CART", "title", "Ver carrinho"),
                                Map.of("id", "CANCEL", "title", "Cancelar pedido")
                        )
                )
        );
    }

    private Map<String, Object> cancelledResponse(WhatsAppFlowSession session) {
        WhatsAppFlowCartService.CartSummary summary = flowCartService.cancelOpenCart(session.getCustomerPhone());
        session.setStatus(WhatsAppFlowSessionStatus.CANCELLED);
        flowSessionRepository.save(session);

        String message = "Pedido cancelado. Seu carrinho foi encerrado e nenhum pedido foi criado.";

        return Map.of(
                "version", "3.0",
                "screen", "CANCELLED",
                "data", Map.of(
                        "flow_token", session.getFlowToken(),
                        "cart_summary", summary.summaryText(),
                        "message", message
                )
        );
    }

    private Map<String, Object> minimumNotReachedResponse(WhatsAppFlowSession session, WhatsAppFlowCartService.CartSummary summary) {
        return Map.of(
                "version", "3.0",
                "screen", "MINIMUM_NOT_REACHED",
                "data", Map.of(
                        "flow_token", session.getFlowToken(),
                        "cart_summary", summary.summaryText(),
                        "message", "Seu carrinho ainda não atingiu o pedido mínimo. Adicione mais produtos para finalizar.",
                        "next_actions", List.of(
                                Map.of("id", "ADD_MORE", "title", "Adicionar mais peças"),
                                Map.of("id", "CANCEL", "title", "Cancelar pedido")
                        )
                )
        );
    }

    private Map<String, Object> customerDataResponse(WhatsAppFlowSession session, WhatsAppFlowCartService.CartSummary summary) {
        return Map.of(
                "version", "3.0",
                "screen", "CUSTOMER_DATA",
                "data", Map.of(
                        "flow_token", session.getFlowToken(),
                        "cart_summary", summaryTextWithNotice(summary),
                        "cart_total", money(summary.subtotal()),
                        "item_count", summary.itemCount()
                )
        );
    }

    private Map<String, Object> addressDataResponse(WhatsAppFlowSession session, JsonNode data) {
        WhatsAppFlowCartService.CartSummary summary = flowCartService.currentSummary(session.getCustomerPhone());
        String fullName = firstText(data, "full_name", "fullName", "customer_name", "name");
        String document = onlyDigits(firstText(data, "cpf_cnpj", "cpf", "document"));
        String email = firstText(data, "email", "customer_email");

        if (!StringUtils.hasText(fullName) || !fullName.trim().contains(" ")) {
            throw new BusinessException("Nome completo é obrigatório para continuar.");
        }
        if (document.length() != 11 && document.length() != 14) {
            throw new BusinessException("CPF/CNPJ inválido para continuar.");
        }
        if (!StringUtils.hasText(email)) {
            throw new BusinessException("E-mail é obrigatório para continuar.");
        }

        return Map.of(
                "version", "3.0",
                "screen", "ADDRESS_DATA",
                "data", Map.of(
                        "flow_token", session.getFlowToken(),
                        "cart_summary", summaryTextWithNotice(summary),
                        "full_name", fullName.trim(),
                        "cpf", document,
                        "email", email.trim()
                )
        );
    }

    private Map<String, Object> shippingFallbackResponse(WhatsAppFlowSession session, JsonNode data) {
        WhatsAppFlowCartService.CartSummary summary = flowCartService.currentSummary(session.getCustomerPhone());
        return Map.of(
                "version", "3.0",
                "screen", "SHIPPING_OPTIONS",
                "data", Map.<String, Object>ofEntries(
                        Map.entry("flow_token", session.getFlowToken()),
                        Map.entry("cart_summary", summaryTextWithNotice(summary)),
                        Map.entry("shipping_message", "O frete e o Pix serao finalizados no checkout seguro da Nuvemshop."),
                        Map.entry("shipping_options", List.of(
                                Map.of("id", "CHECKOUT_NUVEMSHOP", "title", "Finalizar no checkout", "description", "Calcular frete e pagar Pix na Nuvemshop"),
                                Map.of("id", "HUMAN", "title", "Chamar atendente", "description", "Ajuda para frete ou pedido")
                        )),
                        Map.entry("full_name", blankIfNull(firstText(data, "full_name", "fullName"))),
                        Map.entry("cpf", onlyDigits(firstText(data, "cpf_cnpj", "cpf", "document"))),
                        Map.entry("email", blankIfNull(firstText(data, "email"))),
                        Map.entry("postal_code", onlyDigits(firstText(data, "postal_code", "cep", "zipcode"))),
                        Map.entry("street", blankIfNull(firstText(data, "street", "address", "shipping_street"))),
                        Map.entry("number", blankIfNull(firstText(data, "number", "address_number", "shipping_number"))),
                        Map.entry("complement", blankIfNull(firstText(data, "complement", "address_complement"))),
                        Map.entry("neighborhood", blankIfNull(firstText(data, "neighborhood", "locality", "bairro"))),
                        Map.entry("city", blankIfNull(firstText(data, "city", "cidade"))),
                        Map.entry("state", blankIfNull(firstText(data, "state", "province", "uf")))
                )
        );
    }

    private Map<String, Object> handleShippingAction(WhatsAppFlowSession session, JsonNode data) {
        String action = normalize(text(data, "shipping_action", "shippingAction"));
        if ("HUMAN".equals(action)) {
            return humanAttendantResponse(session);
        }
        if (!"CHECKOUT_NUVEMSHOP".equals(action)) {
            throw new BusinessException("Opção de frete inválida no Flow: " + action);
        }
        return confirmationResponse(session, data);
    }

    private Map<String, Object> confirmationResponse(WhatsAppFlowSession session, JsonNode data) {
        WhatsAppFlowCartService.CartSummary summary = flowCartService.currentSummary(session.getCustomerPhone());
        return Map.of(
                "version", "3.0",
                "screen", "CONFIRM_ORDER",
                "data", Map.<String, Object>ofEntries(
                        Map.entry("flow_token", session.getFlowToken()),
                        Map.entry("cart_summary", summaryTextWithNotice(summary)),
                        Map.entry("shipping_summary", "Frete e Pix serao finalizados no checkout da Nuvemshop."),
                        Map.entry("full_name", blankIfNull(firstText(data, "full_name", "fullName"))),
                        Map.entry("cpf", onlyDigits(firstText(data, "cpf_cnpj", "cpf", "document"))),
                        Map.entry("email", blankIfNull(firstText(data, "email"))),
                        Map.entry("postal_code", onlyDigits(firstText(data, "postal_code", "cep", "zipcode"))),
                        Map.entry("street", blankIfNull(firstText(data, "street", "address", "shipping_street"))),
                        Map.entry("number", blankIfNull(firstText(data, "number", "address_number", "shipping_number"))),
                        Map.entry("complement", blankIfNull(firstText(data, "complement", "address_complement"))),
                        Map.entry("neighborhood", blankIfNull(firstText(data, "neighborhood", "locality", "bairro"))),
                        Map.entry("city", blankIfNull(firstText(data, "city", "cidade"))),
                        Map.entry("state", blankIfNull(firstText(data, "state", "province", "uf"))),
                        Map.entry("confirm_actions", List.of(
                                Map.of("id", "CONFIRM", "title", "Criar pedido"),
                                Map.of("id", "ALTER_CART", "title", "Alterar carrinho"),
                                Map.of("id", "CANCEL", "title", "Cancelar pedido")
                        ))
                )
        );
    }

    private Map<String, Object> handleFinalConfirmationAction(WhatsAppFlowSession session, JsonNode data) {
        String action = normalize(text(data, "confirm_final_action", "confirmFinalAction"));
        return switch (action) {
            case "CONFIRM" -> confirmOrder(session, data);
            case "ALTER_CART" -> cartReviewResponse(session);
            case "CANCEL" -> cancelledResponse(session);
            default -> throw new BusinessException("Acao de confirmacao invalida no Flow: " + action);
        };
    }

    private Map<String, Object> cartReviewResponse(WhatsAppFlowSession session) {
        return cartReviewResponse(session, "CART_REVIEW");
    }

    private Map<String, Object> cartReviewResponse(WhatsAppFlowSession session, String screen) {
        WhatsAppFlowCartService.CartSummary summary = flowCartService.currentSummary(session.getCustomerPhone());
        session.setStatus(WhatsAppFlowSessionStatus.PRODUCT_SELECTED);
        session.setSubtotal(summary.subtotal());
        flowSessionRepository.save(session);

        return Map.of(
                "version", "3.0",
                "screen", screen,
                "data", Map.of(
                        "flow_token", session.getFlowToken(),
                        "cart_summary", summaryTextWithNotice(summary),
                        "cart_total", money(summary.subtotal()),
                        "minimum_order_total", money(summary.minimumOrderTotal()),
                        "minimum_order_summary", "Pedido mínimo: " + money(summary.minimumOrderTotal()),
                        "item_count", summary.itemCount(),
                        "next_actions", cartReviewActions()
                )
        );
    }

    private Map<String, Object> selectCartItemResponse(WhatsAppFlowSession session) {
        List<Map<String, Object>> items = flowCartService.cartItemOptions(session.getCustomerPhone())
                .stream()
                .map(item -> Map.<String, Object>of(
                        "id", item.id(),
                        "title", item.title(),
                        "description", item.description()
                ))
                .toList();

        if (items.isEmpty()) {
            return Map.of(
                    "version", "3.0",
                    "screen", "OUT_OF_STOCK",
                    "data", Map.of(
                            "flow_token", session.getFlowToken(),
                            "message", "Não encontrei itens disponíveis no carrinho."
                    )
            );
        }

        return Map.of(
                "version", "3.0",
                "screen", "SELECT_CART_ITEM",
                "data", Map.of(
                        "flow_token", session.getFlowToken(),
                        "cart_items", items
                )
        );
    }

    private Map<String, Object> cartQuantityResponse(WhatsAppFlowSession session, WhatsAppFlowCartService.CartItemOption option) {
        if (option.stock() == null || option.stock() <= 0) {
            return Map.of(
                    "version", "3.0",
                    "screen", "OUT_OF_STOCK",
                    "data", Map.of(
                            "flow_token", session.getFlowToken(),
                            "message", "Esse item ficou indisponível."
                    )
            );
        }

        return Map.of(
                "version", "3.0",
                "screen", "ADJUST_QUANTITY",
                "data", Map.of(
                        "flow_token", session.getFlowToken(),
                        "selected_item", option.id(),
                        "selected_item_name", option.title(),
                        "selected_item_summary", "Quantidade atual: " + option.quantity()
                                + " | Estoque: " + option.stock()
                                + " | Valor unitário: " + money(option.unitPrice()),
                        "quantities", cartQuantityOptions(option.stock())
                )
        );
    }

    private Map<String, Object> confirmOrder(WhatsAppFlowSession session, JsonNode data) {
        CreateDraftOrderResponse order = createCartOrder(session, data);
        return Map.of(
                "version", "3.0",
                "screen", "ORDER_CREATED",
                "data", Map.of(
                        "flow_token", session.getFlowToken(),
                        "local_order_id", order.localOrderId().toString(),
                        "checkout_url", order.checkoutUrl(),
                        "checkout_message", "Link de pagamento: " + order.checkoutUrl(),
                        "message", "Pedido criado com sucesso. Enviamos o link de pagamento no WhatsApp."
                )
        );
    }

    private CreateDraftOrderResponse createCartOrder(WhatsAppFlowSession session, JsonNode data) {
        WhatsAppFlowCartService.CartSummary summary = isCartFlow(session)
                ? flowCartService.currentSummary(session.getCustomerPhone())
                : addCurrentItemToCartIfNeeded(session);
        if (!summary.canCheckout()) {
            throw new BusinessException("Pedido mínimo não atingido. Adicione mais produtos antes de finalizar.");
        }
        CustomerData customer = customerData(session, data);
        CreateDraftOrderResponse order = flowCartService.createOrderFromOpenCart(
                session.getCustomerPhone(),
                new WhatsAppFlowCartService.CartCustomerData(
                customer.firstName(),
                customer.lastName(),
                customer.document(),
                customer.email(),
                customer.postalCode(),
                customer.street(),
                customer.number(),
                customer.complement(),
                customer.neighborhood(),
                customer.city(),
                customer.state()
                )
        );
        session.setStatus(WhatsAppFlowSessionStatus.ORDER_CREATED);
        session.setLocalOrderId(order.localOrderId());
        session.setCheckoutUrl(order.checkoutUrl());
        flowSessionRepository.save(session);
        paymentMessageService.sendPaymentLink(session.getCustomerPhone(), customer.firstName(), order, null);
        return order;
    }

    private Map<String, Object> variantsResponse(WhatsAppFlowSession session) {
        List<ProductMapping> variants = productMappingService.findActiveVariantsByProductId(session.getNuvemshopProductId());
        List<Map<String, Object>> availableVariants = variants.stream()
                .filter(mapping -> availableStock(session, mapping) > 0)
                .map(mapping -> variantOption(session, mapping))
                .toList();

        if (availableVariants.isEmpty()) {
            session.setStatus(WhatsAppFlowSessionStatus.OUT_OF_STOCK);
            flowSessionRepository.save(session);
            return Map.of(
                    "version", "3.0",
                    "screen", "OUT_OF_STOCK",
                    "data", Map.of(
                            "flow_token", session.getFlowToken(),
                            "message", "Produto indisponível no momento."
                    )
            );
        }

        session.setStatus(WhatsAppFlowSessionStatus.PRODUCT_SELECTED);
        flowSessionRepository.save(session);

        ProductMapping first = variants.get(0);
        return Map.of(
                "version", "3.0",
                "screen", "CHOOSE_VARIANT",
                "data", Map.ofEntries(
                        Map.entry("flow_token", session.getFlowToken()),
                        Map.entry("product_name", first.getProductName()),
                        Map.entry("product_image_url", productImageUrl(first)),
                        Map.entry("product_price_summary", productPriceSummary(variants)),
                        Map.entry("product_stock_summary", productStockSummary(session, variants)),
                        Map.entry("product_detail_hint", "Escolha a variação disponível. O estoque mostrado é atualizado antes de criar o pedido."),
                        Map.entry("variants", availableVariants)
                )
        );
    }

    private Map<String, Object> quantityResponse(WhatsAppFlowSession session, ProductMapping mapping) {
        int availableStock = availableStock(session, mapping);
        if (availableStock <= 0) {
            session.setStatus(WhatsAppFlowSessionStatus.OUT_OF_STOCK);
            flowSessionRepository.save(session);
            return Map.of(
                    "version", "3.0",
                    "screen", "OUT_OF_STOCK",
                    "data", Map.of(
                            "flow_token", session.getFlowToken(),
                            "message", "Essa variação ficou indisponível."
                    )
            );
        }

        return Map.of(
                "version", "3.0",
                "screen", "CHOOSE_QUANTITY",
                "data", Map.ofEntries(
                        Map.entry("flow_token", session.getFlowToken()),
                        Map.entry("product_name", mapping.getProductName()),
                        Map.entry("variant_name", displayVariant(mapping)),
                        Map.entry("product_image_url", productImageUrl(mapping)),
                        Map.entry("unit_price", money(mapping.getPrice())),
                        Map.entry("variant_summary", "Variação: " + displayVariant(mapping)),
                        Map.entry("unit_price_summary", "Valor unitário: " + money(mapping.getPrice())),
                        Map.entry("available_stock", availableStock),
                        Map.entry("stock_summary", "Estoque disponível: " + availableStock + " unidade(s)"),
                        Map.entry("quantity_hint", "Escolha a quantidade. O Flow mostra no máximo o estoque disponível."),
                        Map.entry("quantities", quantityOptions(availableStock))
                )
        );
    }

    private List<Map<String, Object>> quantityOptions(Integer stock) {
        int max = Math.max(0, Math.min(stock == null ? 0 : stock, MAX_QUANTITY_OPTIONS));
        List<Map<String, Object>> options = new ArrayList<>();
        for (int quantity = 1; quantity <= max; quantity++) {
            options.add(Map.of(
                    "id", String.valueOf(quantity),
                    "title", quantity == 1 ? "1 unidade" : quantity + " unidades"
            ));
        }
        return options;
    }

    private List<Map<String, Object>> cartQuantityOptions(Integer stock) {
        List<Map<String, Object>> options = new ArrayList<>();
        options.add(Map.of("id", "0", "title", "Remover item"));
        options.addAll(quantityOptions(stock));
        return options;
    }

    private Map<String, Object> variantOption(WhatsAppFlowSession session, ProductMapping mapping) {
        int availableStock = availableStock(session, mapping);
        return Map.of(
                "id", String.valueOf(mapping.getNuvemshopVariantId()),
                "title", displayVariant(mapping),
                "description", "Disponível: " + availableStock + " | " + money(mapping.getPrice())
        );
    }

    private ProductMapping resolveProductMapping(String productRetailerId) {
        if (!StringUtils.hasText(productRetailerId)) {
            throw new BusinessException("product_retailer_id não informado para iniciar o Flow.");
        }
        String value = productRetailerId.trim();
        if (isNumeric(value)) {
            return productMappingService.findActiveByNuvemshopVariantId(Long.valueOf(value));
        }
        return productMappingService.findActiveByMetaProductRetailerId(value);
    }

    private void validateQuantityAgainstStock(WhatsAppFlowSession session, ProductMapping mapping, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException("Quantidade selecionada no Flow deve ser maior que zero.");
        }
        if (mapping.getStock() == null || quantity > mapping.getStock()) {
            throw new BusinessException("Estoque mudou. Disponível agora para "
                    + mapping.getProductName() + variantSuffix(mapping) + ": "
                    + (mapping.getStock() == null ? 0 : mapping.getStock()) + " unidade(s).");
        }
        int availableStock = availableStock(session, mapping);
        if (quantity > availableStock) {
            throw new BusinessException("Essa quantidade já está reservada no carrinho. Disponível agora: "
                    + availableStock + " unidade(s).");
        }
    }

    private int availableStock(WhatsAppFlowSession session, ProductMapping mapping) {
        String phone = session == null ? null : session.getCustomerPhone();
        return flowCartService.availableStock(phone, mapping);
    }

    private CustomerData customerData(WhatsAppFlowSession session, JsonNode data) {
        String fullName = firstText(data, "full_name", "fullName", "customer_name", "name");
        String firstName = firstText(data, "first_name", "customerName");
        String lastName = firstText(data, "last_name", "customerLastname");

        if (!StringUtils.hasText(firstName)) {
            NameParts nameParts = splitName(StringUtils.hasText(fullName) ? fullName : session.getCustomerName());
            firstName = nameParts.firstName();
            lastName = StringUtils.hasText(lastName) ? lastName : nameParts.lastName();
        }

        String document = onlyDigits(firstText(data, "cpf_cnpj", "cpf", "document"));
        String email = firstText(data, "email", "customer_email");
        String postalCode = onlyDigits(firstText(data, "postal_code", "cep", "zipcode"));
        String street = firstText(data, "street", "address", "shipping_street");
        String number = firstText(data, "number", "address_number", "shipping_number");
        String complement = firstText(data, "complement", "address_complement");
        String neighborhood = firstText(data, "neighborhood", "locality", "bairro");
        String city = firstText(data, "city", "cidade");
        String state = firstText(data, "state", "province", "uf");

        if (!StringUtils.hasText(firstName) || !StringUtils.hasText(lastName)) {
            throw new BusinessException("Nome completo é obrigatório para confirmar o pedido no Flow.");
        }
        if (document.length() != 11 && document.length() != 14) {
            throw new BusinessException("CPF/CNPJ inválido no Flow.");
        }
        if (!StringUtils.hasText(email)) {
            throw new BusinessException("E-mail é obrigatório para confirmar o pedido no Flow.");
        }
        if (postalCode.length() != 8) {
            throw new BusinessException("CEP inválido no Flow.");
        }
        if (!StringUtils.hasText(number)) {
            throw new BusinessException("Número do endereço é obrigatório para confirmar o pedido no Flow.");
        }

        return new CustomerData(firstName, lastName, document, email, postalCode, street, number, complement, neighborhood, city, state);
    }

    private boolean isConfirmation(String screen, String action, JsonNode data) {
        String normalizedScreen = screen == null ? "" : screen.trim().toUpperCase();
        String normalizedAction = action == null ? "" : action.trim().toUpperCase();
        return normalizedScreen.contains("CONFIRM")
                || normalizedScreen.contains("CUSTOMER")
                || "COMPLETE".equals(normalizedAction)
                || "CONFIRM_ORDER".equalsIgnoreCase(text(data, "action"))
                || hasData(data, "confirm_order");
    }

    private boolean isCartFlow(WhatsAppFlowSession session) {
        return session != null
                && CART_FLOW_MARKER.equalsIgnoreCase(session.getProductRetailerId());
    }

    private boolean isShoppingFlow(WhatsAppFlowSession session) {
        return session != null
                && StringUtils.hasText(session.getProductRetailerId())
                && session.getProductRetailerId().toUpperCase(Locale.ROOT).startsWith(SHOP_FLOW_MARKER + ":");
    }

    private String sessionEntryPoint(WhatsAppFlowSession session) {
        if (session == null || !StringUtils.hasText(session.getProductRetailerId())) {
            return "MENU";
        }
        String marker = session.getProductRetailerId();
        int separator = marker.indexOf(':');
        return separator >= 0 && separator + 1 < marker.length() ? marker.substring(separator + 1) : "MENU";
    }

    private List<Map<String, Object>> cartReviewActions() {
        return List.of(
                Map.of("id", "CHECKOUT", "title", "Finalizar pedido"),
                Map.of("id", "ADJUST_ITEM", "title", "Ajustar quantidade"),
                Map.of("id", "ADD_MORE", "title", "Adicionar mais produtos"),
                Map.of("id", "CANCEL", "title", "Cancelar pedido")
        );
    }

    private String summaryTextWithNotice(WhatsAppFlowCartService.CartSummary summary) {
        if (summary == null) {
            return "Carrinho vazio.";
        }
        if (!StringUtils.hasText(summary.notice())) {
            return summary.summaryText();
        }
        return summary.notice() + "\n\n" + summary.summaryText();
    }

    private boolean hasStock(ProductMapping mapping) {
        return mapping.getStock() != null && mapping.getStock() > 0;
    }

    private BigDecimal price(ProductMapping mapping) {
        return mapping.getPrice() == null ? BigDecimal.ZERO : mapping.getPrice();
    }

    private String displayVariant(ProductMapping mapping) {
        return StringUtils.hasText(mapping.getVariantName()) ? mapping.getVariantName() : mapping.getProductName();
    }

    private String productImageUrl(ProductMapping mapping) {
        if (mapping != null && StringUtils.hasText(mapping.getImageUrl())) {
            return mapping.getImageUrl();
        }
        return PRODUCT_PLACEHOLDER_IMAGE_URL;
    }

    private String productPriceSummary(List<ProductMapping> variants) {
        BigDecimal minPrice = variants.stream()
                .map(ProductMapping::getPrice)
                .filter(java.util.Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        return "A partir de " + money(minPrice);
    }

    private String productStockSummary(WhatsAppFlowSession session, List<ProductMapping> variants) {
        int stock = variants.stream()
                .mapToInt(mapping -> availableStock(session, mapping))
                .sum();
        return "Estoque total disponível: " + stock + " unidade(s)";
    }

    private String variantSuffix(ProductMapping mapping) {
        return StringUtils.hasText(mapping.getVariantName()) ? " - " + mapping.getVariantName() : "";
    }

    private String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(BRAZIL).format(value == null ? BigDecimal.ZERO : value);
    }

    private boolean hasData(JsonNode data, String... names) {
        if (data == null || data.isMissingNode() || data.isNull()) {
            return false;
        }
        for (String name : names) {
            if (data.hasNonNull(name) && StringUtils.hasText(data.path(name).asText())) {
                return true;
            }
        }
        return false;
    }

    private String text(JsonNode node, String... names) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... names) {
        return text(node, names);
    }

    private String firstText(JsonNode firstNode, JsonNode secondNode, String... names) {
        String first = text(firstNode, names);
        return StringUtils.hasText(first) ? first : text(secondNode, names);
    }

    private Long longValue(JsonNode node, String... names) {
        String value = text(node, names);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("Valor numérico não encontrado no Flow.");
        }
        return Long.valueOf(value);
    }

    private Integer intValue(JsonNode node, String... names) {
        String value = text(node, names);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("Quantidade não encontrada no Flow.");
        }
        return Integer.valueOf(value);
    }

    private boolean isNumeric(String value) {
        try {
            Long.valueOf(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String preview(String value) {
        if (value == null) {
            return "";
        }
        return value.substring(0, Math.min(value.length(), 500));
    }

    private String mask(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        int visible = Math.min(8, trimmed.length());
        return trimmed.substring(0, visible) + "...";
    }

    private NameParts splitName(String fullName) {
        String normalized = StringUtils.hasText(fullName) ? fullName.trim().replaceAll("\\s+", " ") : "Cliente WhatsApp";
        int firstSpace = normalized.indexOf(' ');
        if (firstSpace < 0) {
            return new NameParts(normalized, "Cliente");
        }
        return new NameParts(normalized.substring(0, firstSpace), normalized.substring(firstSpace + 1));
    }

    private record NameParts(String firstName, String lastName) {
    }

    private record CustomerData(
            String firstName,
            String lastName,
            String document,
            String email,
            String postalCode,
            String street,
            String number,
            String complement,
            String neighborhood,
            String city,
            String state
    ) {
    }
}
