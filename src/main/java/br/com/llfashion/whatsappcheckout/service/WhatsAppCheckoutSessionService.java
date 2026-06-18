package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.client.ViaCepClient;
import br.com.llfashion.whatsappcheckout.config.CheckoutProperties;
import br.com.llfashion.whatsappcheckout.dto.request.CreateDraftOrderItemRequest;
import br.com.llfashion.whatsappcheckout.dto.request.CreateDraftOrderRequest;
import br.com.llfashion.whatsappcheckout.dto.response.CreateDraftOrderResponse;
import br.com.llfashion.whatsappcheckout.dto.response.ViaCepAddressResponse;
import br.com.llfashion.whatsappcheckout.entity.ProductMapping;
import br.com.llfashion.whatsappcheckout.entity.WhatsAppCheckoutSession;
import br.com.llfashion.whatsappcheckout.entity.WhatsAppCheckoutSessionItem;
import br.com.llfashion.whatsappcheckout.enums.WhatsAppCheckoutSessionStatus;
import br.com.llfashion.whatsappcheckout.exception.BusinessException;
import br.com.llfashion.whatsappcheckout.repository.WhatsAppCheckoutSessionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WhatsAppCheckoutSessionService {

    private static final String DEFAULT_LASTNAME = "Cliente";
    private static final Set<WhatsAppCheckoutSessionStatus> PENDING_STATUSES = Set.of(
            WhatsAppCheckoutSessionStatus.AWAITING_FULL_NAME,
            WhatsAppCheckoutSessionStatus.AWAITING_CPF,
            WhatsAppCheckoutSessionStatus.AWAITING_EMAIL,
            WhatsAppCheckoutSessionStatus.AWAITING_POSTAL_CODE,
            WhatsAppCheckoutSessionStatus.AWAITING_ADDRESS_NUMBER,
            WhatsAppCheckoutSessionStatus.AWAITING_ADDRESS_COMPLEMENT
    );
    private static final Set<WhatsAppCheckoutSessionStatus> CANCELABLE_STATUSES = Set.of(
            WhatsAppCheckoutSessionStatus.CART_OPEN,
            WhatsAppCheckoutSessionStatus.INSUFFICIENT_STOCK,
            WhatsAppCheckoutSessionStatus.BELOW_MINIMUM,
            WhatsAppCheckoutSessionStatus.AWAITING_FULL_NAME,
            WhatsAppCheckoutSessionStatus.AWAITING_CPF,
            WhatsAppCheckoutSessionStatus.AWAITING_EMAIL,
            WhatsAppCheckoutSessionStatus.AWAITING_POSTAL_CODE,
            WhatsAppCheckoutSessionStatus.AWAITING_ADDRESS_NUMBER,
            WhatsAppCheckoutSessionStatus.AWAITING_ADDRESS_COMPLEMENT
    );
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final CheckoutProperties checkoutProperties;
    private final ProductMappingService productMappingService;
    private final DraftOrderService draftOrderService;
    private final WhatsAppCheckoutSessionRepository sessionRepository;
    private final ViaCepClient viaCepClient;

    public WhatsAppCheckoutSessionService(
            CheckoutProperties checkoutProperties,
            ProductMappingService productMappingService,
            DraftOrderService draftOrderService,
            WhatsAppCheckoutSessionRepository sessionRepository,
            ViaCepClient viaCepClient
    ) {
        this.checkoutProperties = checkoutProperties;
        this.productMappingService = productMappingService;
        this.draftOrderService = draftOrderService;
        this.sessionRepository = sessionRepository;
        this.viaCepClient = viaCepClient;
    }

    @Transactional
    public CartSessionResult startCartSession(CreateDraftOrderRequest request, String whatsappMessageId) {
        Optional<WhatsAppCheckoutSession> existingSession = findExistingSession(whatsappMessageId);
        if (existingSession.isPresent()) {
            return CartSessionResult.from(existingSession.get(), true);
        }

        BigDecimal minimumOrderTotal = checkoutProperties.resolvedMinimumOrderTotal();
        List<ResolvedSessionItem> resolvedItems = request.items()
                .stream()
                .map(this::resolveItem)
                .toList();
        BigDecimal subtotal = subtotal(resolvedItems);
        String stockIssueMessage = stockIssueMessage(resolvedItems);
        WhatsAppCheckoutSessionStatus status = StringUtils.hasText(stockIssueMessage)
                ? WhatsAppCheckoutSessionStatus.INSUFFICIENT_STOCK
                : subtotal.compareTo(minimumOrderTotal) < 0
                ? WhatsAppCheckoutSessionStatus.BELOW_MINIMUM
                : WhatsAppCheckoutSessionStatus.AWAITING_FULL_NAME;

        WhatsAppCheckoutSession session = WhatsAppCheckoutSession.builder()
                .whatsappMessageId(trimToNull(whatsappMessageId))
                .customerName(trimOrDefault(request.customerName(), "Cliente WhatsApp"))
                .customerLastname(trimOrDefault(request.customerLastname(), DEFAULT_LASTNAME))
                .customerPhone(onlyDigits(request.customerPhone()))
                .status(status)
                .subtotal(subtotal)
                .minimumOrderTotal(minimumOrderTotal)
                .build();

        for (ResolvedSessionItem resolvedItem : resolvedItems) {
            ProductMapping mapping = resolvedItem.mapping();
            session.addItem(WhatsAppCheckoutSessionItem.builder()
                    .productMapping(mapping)
                    .nuvemshopProductId(mapping.getNuvemshopProductId())
                    .nuvemshopVariantId(mapping.getNuvemshopVariantId())
                    .productName(mapping.getProductName())
                    .variantName(mapping.getVariantName())
                    .quantity(resolvedItem.quantity())
                    .unitPrice(mapping.getPrice())
                    .build());
        }

        return CartSessionResult.from(sessionRepository.save(session), false);
    }

    @Transactional
    public void markWaitingHumanAttendant(String customerPhone, String customerName, String whatsappMessageId) {
        Optional<WhatsAppCheckoutSession> existingSession = findExistingSession(whatsappMessageId);
        if (existingSession.isPresent()) {
            WhatsAppCheckoutSession session = existingSession.get();
            session.setStatus(WhatsAppCheckoutSessionStatus.WAITING_HUMAN_ATTENDANT);
            sessionRepository.save(session);
            return;
        }

        WhatsAppCheckoutSession session = WhatsAppCheckoutSession.builder()
                .whatsappMessageId(trimToNull(whatsappMessageId))
                .customerName(trimOrDefault(customerName, "Cliente WhatsApp"))
                .customerLastname(DEFAULT_LASTNAME)
                .customerPhone(onlyDigits(customerPhone))
                .status(WhatsAppCheckoutSessionStatus.WAITING_HUMAN_ATTENDANT)
                .subtotal(BigDecimal.ZERO)
                .minimumOrderTotal(checkoutProperties.resolvedMinimumOrderTotal())
                .build();
        sessionRepository.save(session);
    }

    @Transactional
    public TextSessionResult handleCustomerText(String customerPhone, String text) {
        String phone = onlyDigits(customerPhone);
        String value = text == null ? "" : text.trim();

        if (isCancellationRequest(value)) {
            Optional<WhatsAppCheckoutSession> cancelableSession = sessionRepository
                    .findFirstByCustomerPhoneAndStatusInOrderByUpdatedAtDesc(phone, CANCELABLE_STATUSES);

            if (cancelableSession.isEmpty()) {
                return TextSessionResult.notHandled();
            }

            WhatsAppCheckoutSession session = cancelableSession.get();
            session.setStatus(WhatsAppCheckoutSessionStatus.CANCELLED);
            sessionRepository.save(session);
            return TextSessionResult.reply(
                    session,
                    "Pedido cancelado. Nenhum pedido foi criado. Quando quiser, envie um novo carrinho pelo WhatsApp."
            );
        }

        Optional<WhatsAppCheckoutSession> pendingSession = sessionRepository
                .findFirstByCustomerPhoneAndStatusInOrderByUpdatedAtDesc(phone, PENDING_STATUSES);

        if (pendingSession.isEmpty()) {
            return TextSessionResult.notHandled();
        }

        WhatsAppCheckoutSession session = pendingSession.get();

        if (session.getStatus() == WhatsAppCheckoutSessionStatus.AWAITING_FULL_NAME) {
            return handleFullName(session, value);
        }

        if (session.getStatus() == WhatsAppCheckoutSessionStatus.AWAITING_CPF) {
            return handleCpf(session, value);
        }

        if (session.getStatus() == WhatsAppCheckoutSessionStatus.AWAITING_EMAIL) {
            return handleEmail(session, value);
        }

        if (session.getStatus() == WhatsAppCheckoutSessionStatus.AWAITING_POSTAL_CODE) {
            return handlePostalCode(session, value);
        }

        if (session.getStatus() == WhatsAppCheckoutSessionStatus.AWAITING_ADDRESS_NUMBER) {
            return handleAddressNumber(session, value);
        }

        if (session.getStatus() == WhatsAppCheckoutSessionStatus.AWAITING_ADDRESS_COMPLEMENT) {
            return handleAddressComplement(session, value);
        }

        return TextSessionResult.notHandled();
    }

    private TextSessionResult handleFullName(WhatsAppCheckoutSession session, String fullName) {
        String normalizedName = normalizeSpaces(fullName);
        if (!StringUtils.hasText(normalizedName) || !normalizedName.contains(" ")) {
            return TextSessionResult.reply(
                    session,
                    "Me envie o nome completo, com nome e sobrenome, para eu cadastrar no pedido."
            );
        }

        NameParts nameParts = splitName(normalizedName);
        session.setCustomerName(nameParts.firstName());
        session.setCustomerLastname(nameParts.lastName());
        session.setStatus(WhatsAppCheckoutSessionStatus.AWAITING_CPF);
        sessionRepository.save(session);

        return TextSessionResult.reply(
                session,
                "Obrigado. Agora me envie o CPF do cliente, somente numeros."
        );
    }

    private TextSessionResult handleCpf(WhatsAppCheckoutSession session, String cpf) {
        String digits = onlyDigits(cpf);
        if (digits.length() != 11 && digits.length() != 14) {
            return TextSessionResult.reply(
                    session,
                    "Esse documento nao parece valido. Envie o CPF com 11 numeros ou CNPJ com 14 numeros."
            );
        }

        session.setCustomerDocument(digits);
        session.setStatus(WhatsAppCheckoutSessionStatus.AWAITING_EMAIL);
        sessionRepository.save(session);

        return TextSessionResult.reply(
                session,
                "Perfeito. Agora me envie o e-mail do cliente."
        );
    }

    private TextSessionResult handleEmail(WhatsAppCheckoutSession session, String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return TextSessionResult.reply(
                    session,
                    "Esse e-mail nao parece valido. Me envie apenas o e-mail para eu continuar com seu pedido."
            );
        }

        session.setCustomerEmail(email.toLowerCase());
        session.setStatus(WhatsAppCheckoutSessionStatus.AWAITING_POSTAL_CODE);
        sessionRepository.save(session);

        return TextSessionResult.reply(
                session,
                "Agora me envie o CEP de entrega com 8 digitos."
        );
    }

    private TextSessionResult handlePostalCode(WhatsAppCheckoutSession session, String postalCode) {
        String digits = onlyDigits(postalCode);
        if (digits.length() != 8) {
            return TextSessionResult.reply(
                    session,
                    "Esse CEP nao parece valido. Me envie o CEP com 8 digitos, por exemplo: 22041001."
            );
        }

        java.util.Optional<ViaCepAddressResponse> address = viaCepClient.buscarEndereco(digits);
        if (address.isEmpty()) {
            return TextSessionResult.reply(
                    session,
                    "Nao encontrei esse CEP. Confira os 8 digitos e me envie novamente."
            );
        }

        ViaCepAddressResponse viaCepAddress = address.get();
        session.setPostalCode(digits);
        session.setAddressStreet(trimToNull(viaCepAddress.logradouro()));
        session.setAddressNeighborhood(trimToNull(viaCepAddress.bairro()));
        session.setAddressCity(trimToNull(viaCepAddress.localidade()));
        session.setAddressState(trimToNull(viaCepAddress.uf()));
        session.setStatus(WhatsAppCheckoutSessionStatus.AWAITING_ADDRESS_NUMBER);
        sessionRepository.save(session);

        return TextSessionResult.reply(
                session,
                "Encontrei o endereco: " + formatAddress(session) + ".\n\n"
                        + "Agora me envie o numero da casa ou apartamento."
        );
    }

    private TextSessionResult handleAddressNumber(WhatsAppCheckoutSession session, String addressNumber) {
        String normalizedNumber = normalizeSpaces(addressNumber);
        if (!StringUtils.hasText(normalizedNumber)) {
            return TextSessionResult.reply(
                    session,
                    "Me envie o numero da casa ou apartamento para eu cadastrar no pedido."
            );
        }

        session.setAddressNumber(normalizedNumber);
        session.setStatus(WhatsAppCheckoutSessionStatus.AWAITING_ADDRESS_COMPLEMENT);
        sessionRepository.save(session);

        return TextSessionResult.reply(
                session,
                "Tem complemento? Exemplo: bloco, casa, apto ou referencia.\n\n"
                        + "Se nao tiver, responda: sem complemento"
        );
    }

    private TextSessionResult handleAddressComplement(WhatsAppCheckoutSession session, String complement) {
        session.setAddressComplement(normalizeComplement(complement));
        CreateDraftOrderResponse createdOrder = draftOrderService.createDraftOrderFromWhatsAppWebhook(
                toDraftOrderRequest(session),
                session.getWhatsappMessageId()
        );

        session.setStatus(WhatsAppCheckoutSessionStatus.DRAFT_ORDER_CREATED);
        session.setLocalOrderId(createdOrder.localOrderId());
        session.setCheckoutUrl(createdOrder.checkoutUrl());
        sessionRepository.save(session);

        return TextSessionResult.createdOrder(session, createdOrder);
    }

    private CreateDraftOrderRequest toDraftOrderRequest(WhatsAppCheckoutSession session) {
        List<CreateDraftOrderItemRequest> items = session.getItems()
                .stream()
                .map(item -> new CreateDraftOrderItemRequest(item.getNuvemshopVariantId(), null, item.getQuantity()))
                .toList();

        return new CreateDraftOrderRequest(
                session.getCustomerName(),
                session.getCustomerLastname(),
                session.getCustomerEmail(),
                session.getCustomerPhone(),
                items,
                session.getCustomerDocument(),
                session.getPostalCode(),
                session.getAddressStreet(),
                session.getAddressNumber(),
                session.getAddressComplement(),
                session.getAddressNeighborhood(),
                session.getAddressCity(),
                session.getAddressState()
        );
    }

    private Optional<WhatsAppCheckoutSession> findExistingSession(String whatsappMessageId) {
        if (!StringUtils.hasText(whatsappMessageId)) {
            return Optional.empty();
        }
        return sessionRepository.findByWhatsappMessageId(whatsappMessageId.trim());
    }

    private ResolvedSessionItem resolveItem(CreateDraftOrderItemRequest item) {
        if (item.quantity() == null || item.quantity() <= 0) {
            throw new BusinessException("Quantidade do item WhatsApp deve ser maior que zero.");
        }

        boolean hasVariantId = item.nuvemshopVariantId() != null;
        boolean hasMetaRetailerId = StringUtils.hasText(item.metaProductRetailerId());

        if (!hasVariantId && !hasMetaRetailerId) {
            throw new BusinessException("Cada item precisa ter nuvemshopVariantId ou metaProductRetailerId");
        }
        if (hasVariantId && hasMetaRetailerId) {
            throw new BusinessException("Informe apenas nuvemshopVariantId ou metaProductRetailerId em cada item");
        }

        ProductMapping mapping = hasMetaRetailerId
                ? productMappingService.findActiveByMetaProductRetailerId(item.metaProductRetailerId().trim())
                : productMappingService.findActiveByNuvemshopVariantId(item.nuvemshopVariantId());

        return new ResolvedSessionItem(mapping, item.quantity());
    }

    private BigDecimal subtotal(List<ResolvedSessionItem> items) {
        return items.stream()
                .map(item -> price(item.mapping()).multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String stockIssueMessage(List<ResolvedSessionItem> items) {
        return items.stream()
                .filter(item -> item.mapping().getStock() != null && item.quantity() > item.mapping().getStock())
                .map(item -> item.mapping().getProductName()
                        + variantSuffix(item.mapping())
                        + ": solicitado " + item.quantity()
                        + ", disponivel " + item.mapping().getStock())
                .findFirst()
                .orElse(null);
    }

    private String variantSuffix(ProductMapping mapping) {
        return StringUtils.hasText(mapping.getVariantName()) ? " - " + mapping.getVariantName() : "";
    }

    private BigDecimal price(ProductMapping mapping) {
        return mapping.getPrice() == null ? BigDecimal.ZERO : mapping.getPrice();
    }

    private String trimOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String normalizeSpaces(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private NameParts splitName(String fullName) {
        String normalized = normalizeSpaces(fullName);
        int firstSpace = normalized.indexOf(' ');
        if (firstSpace < 0) {
            return new NameParts(normalized, DEFAULT_LASTNAME);
        }
        return new NameParts(normalized.substring(0, firstSpace), normalized.substring(firstSpace + 1));
    }

    private String formatAddress(WhatsAppCheckoutSession session) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (StringUtils.hasText(session.getAddressStreet())) {
            parts.add(session.getAddressStreet());
        }
        if (StringUtils.hasText(session.getAddressNeighborhood())) {
            parts.add(session.getAddressNeighborhood());
        }
        if (StringUtils.hasText(session.getAddressCity()) || StringUtils.hasText(session.getAddressState())) {
            parts.add(trimToNull((trimOrDefault(session.getAddressCity(), "") + "/" + trimOrDefault(session.getAddressState(), "")).replaceAll("^/|/$", "")));
        }
        return parts.isEmpty() ? "CEP " + session.getPostalCode() : String.join(", ", parts);
    }

    private String normalizeComplement(String complement) {
        String normalized = normalizeSpaces(complement);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String lower = normalized.toLowerCase();
        if ("sem".equals(lower)
                || "nao".equals(lower)
                || "não".equals(lower)
                || "sem complemento".equals(lower)
                || "nao tem".equals(lower)
                || "não tem".equals(lower)
                || "n/a".equals(lower)) {
            return null;
        }
        return normalized;
    }

    private boolean isCancellationRequest(String text) {
        String normalized = normalizeSpaces(text).toLowerCase();
        return "cancelar".equals(normalized)
                || "cancelar pedido".equals(normalized)
                || "cancela".equals(normalized)
                || "cancelamento".equals(normalized)
                || "desistir".equals(normalized);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private record ResolvedSessionItem(ProductMapping mapping, Integer quantity) {
    }

    private record NameParts(String firstName, String lastName) {
    }

    public record CartSessionResult(
            boolean existing,
            WhatsAppCheckoutSessionStatus status,
            String customerPhone,
            String customerName,
            BigDecimal subtotal,
            BigDecimal minimumOrderTotal,
            BigDecimal missingAmount,
            String stockIssueMessage
    ) {

        private static CartSessionResult from(WhatsAppCheckoutSession session, boolean existing) {
            BigDecimal missingAmount = session.getMinimumOrderTotal().subtract(session.getSubtotal());
            if (missingAmount.compareTo(BigDecimal.ZERO) < 0) {
                missingAmount = BigDecimal.ZERO;
            }
            String stockIssueMessage = session.getItems()
                    .stream()
                    .filter(item -> item.getProductMapping() != null
                            && item.getProductMapping().getStock() != null
                            && item.getQuantity() > item.getProductMapping().getStock())
                    .map(item -> item.getProductName()
                            + (StringUtils.hasText(item.getVariantName()) ? " - " + item.getVariantName() : "")
                            + ": solicitado " + item.getQuantity()
                            + ", disponivel " + item.getProductMapping().getStock())
                    .findFirst()
                    .orElse(null);

            return new CartSessionResult(
                    existing,
                    session.getStatus(),
                    session.getCustomerPhone(),
                    session.getCustomerName(),
                    session.getSubtotal(),
                    session.getMinimumOrderTotal(),
                    missingAmount,
                    stockIssueMessage
            );
        }
    }

    public record TextSessionResult(
            boolean handled,
            String customerPhone,
            String customerName,
            String replyMessage,
            CreateDraftOrderResponse createdOrder
    ) {

        private static TextSessionResult notHandled() {
            return new TextSessionResult(false, null, null, null, null);
        }

        private static TextSessionResult reply(WhatsAppCheckoutSession session, String replyMessage) {
            return new TextSessionResult(
                    true,
                    session.getCustomerPhone(),
                    session.getCustomerName(),
                    replyMessage,
                    null
            );
        }

        private static TextSessionResult createdOrder(WhatsAppCheckoutSession session, CreateDraftOrderResponse createdOrder) {
            return new TextSessionResult(
                    true,
                    session.getCustomerPhone(),
                    session.getCustomerName(),
                    null,
                    createdOrder
            );
        }
    }
}
