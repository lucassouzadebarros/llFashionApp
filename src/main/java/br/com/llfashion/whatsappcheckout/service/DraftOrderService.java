package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.client.NuvemshopApiClient;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopDraftOrderProductRequest;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopDraftOrderRequest;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopDraftOrderResponse;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopDraftOrderShippingAddressRequest;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopDraftOrderShippingRequest;
import br.com.llfashion.whatsappcheckout.dto.request.CreateDraftOrderItemRequest;
import br.com.llfashion.whatsappcheckout.dto.request.CreateDraftOrderRequest;
import br.com.llfashion.whatsappcheckout.dto.response.CreateDraftOrderResponse;
import br.com.llfashion.whatsappcheckout.dto.response.DraftOrderSummaryResponse;
import br.com.llfashion.whatsappcheckout.dto.response.LocalOrderResponse;
import br.com.llfashion.whatsappcheckout.entity.NuvemshopInstallation;
import br.com.llfashion.whatsappcheckout.entity.ProductMapping;
import br.com.llfashion.whatsappcheckout.entity.WhatsappOrder;
import br.com.llfashion.whatsappcheckout.entity.WhatsappOrderItem;
import br.com.llfashion.whatsappcheckout.enums.OrderStatus;
import br.com.llfashion.whatsappcheckout.enums.WebhookSource;
import br.com.llfashion.whatsappcheckout.exception.BusinessException;
import br.com.llfashion.whatsappcheckout.exception.EntityNotFoundException;
import br.com.llfashion.whatsappcheckout.mapper.WhatsappOrderMapper;
import br.com.llfashion.whatsappcheckout.repository.WhatsappOrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DraftOrderService {

    private static final String DEFAULT_LASTNAME = "Cliente";
    private static final String DEFAULT_EMAIL_DOMAIN = "llfashionmoda.com.br";
    private static final String SALE_CHANNEL = "WhatsApp";
    private static final String STATUS_TOKEN_PREFIX = "ord_";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String NOTE = "Pedido criado automaticamente pela integração WhatsApp Checkout";

    private final NuvemshopInstallationService installationService;
    private final ProductMappingService productMappingService;
    private final NuvemshopApiClient apiClient;
    private final WhatsappOrderRepository orderRepository;
    private final WhatsappOrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    public DraftOrderService(
            NuvemshopInstallationService installationService,
            ProductMappingService productMappingService,
            NuvemshopApiClient apiClient,
            WhatsappOrderRepository orderRepository,
            WhatsappOrderMapper orderMapper,
            ObjectMapper objectMapper
    ) {
        this.installationService = installationService;
        this.productMappingService = productMappingService;
        this.apiClient = apiClient;
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CreateDraftOrderResponse createDraftOrder(CreateDraftOrderRequest request) {
        return createDraftOrder(request, null);
    }

    @Transactional
    public CreateDraftOrderResponse createDraftOrderFromWhatsAppWebhook(CreateDraftOrderRequest request, String whatsappMessageId) {
        return createDraftOrder(request, whatsappMessageId);
    }

    @Transactional(readOnly = true)
    public boolean hasOrderForWhatsAppMessage(String whatsappMessageId) {
        return StringUtils.hasText(whatsappMessageId)
                && orderRepository.findByWhatsappMessageId(whatsappMessageId.trim()).isPresent();
    }

    private CreateDraftOrderResponse createDraftOrder(CreateDraftOrderRequest request, String whatsappMessageId) {
        if (StringUtils.hasText(whatsappMessageId)) {
            WhatsappOrder existingOrder = orderRepository.findByWhatsappMessageId(whatsappMessageId.trim()).orElse(null);
            if (existingOrder != null) {
                return toCreateDraftOrderResponse(
                        existingOrder,
                        "Pedido já processado anteriormente para este carrinho do WhatsApp."
                );
            }
        }

        NuvemshopInstallation installation = installationService.getActiveInstallation();
        List<ResolvedOrderItem> resolvedItems = request.items()
                .stream()
                .map(this::resolveOrderItem)
                .toList();

        String customerLastname = StringUtils.hasText(request.customerLastname())
                ? request.customerLastname().trim()
                : DEFAULT_LASTNAME;
        String customerEmail = normalizeEmail(request.customerEmail(), request.customerPhone());

        NuvemshopDraftOrderRequest draftOrderRequest = new NuvemshopDraftOrderRequest(
                request.customerName().trim(),
                customerLastname,
                customerEmail,
                request.customerPhone().trim(),
                onlyDigitsOrNull(request.cpfCnpj()),
                "unpaid",
                SALE_CHANNEL,
                NOTE,
                resolvedItems.stream()
                        .map(item -> new NuvemshopDraftOrderProductRequest(item.mapping().getNuvemshopVariantId(), item.quantity()))
                        .toList(),
                buildShipping(request)
        );

        NuvemshopDraftOrderResponse nuvemshopResponse = apiClient.criarDraftOrder(
                installation.getStoreId(),
                installation.getAccessToken(),
                draftOrderRequest
        );

        if (nuvemshopResponse == null) {
            throw new BusinessException("A Nuvemshop não retornou dados do draft order criado");
        }

        String checkoutUrl = firstText(nuvemshopResponse.checkoutUrl(), nuvemshopResponse.abandonedCheckoutUrl());
        WhatsappOrder localOrder = saveLocalOrder(request, customerLastname, customerEmail, whatsappMessageId, resolvedItems, nuvemshopResponse, checkoutUrl);

        return toCreateDraftOrderResponse(localOrder, "Pedido criado com sucesso. Envie o checkoutUrl para o cliente finalizar o pagamento.");
    }

    @Transactional(readOnly = true)
    public DraftOrderSummaryResponse getDraftOrder(Long draftOrderId) {
        NuvemshopInstallation installation = installationService.getActiveInstallation();
        NuvemshopDraftOrderResponse response = apiClient.buscarDraftOrder(
                installation.getStoreId(),
                installation.getAccessToken(),
                draftOrderId
        );

        if (response == null) {
            throw new EntityNotFoundException("Draft Order não encontrado na Nuvemshop: " + draftOrderId);
        }

        return new DraftOrderSummaryResponse(
                response.id(),
                response.status(),
                response.paymentStatus(),
                firstText(response.checkoutUrl(), response.abandonedCheckoutUrl()),
                response.total()
        );
    }

    @Transactional(readOnly = true)
    public LocalOrderResponse getLocalOrder(UUID localOrderId) {
        WhatsappOrder order = orderRepository.findById(localOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Pedido local não encontrado: " + localOrderId));
        return orderMapper.toResponse(order);
    }

    private ResolvedOrderItem resolveOrderItem(CreateDraftOrderItemRequest item) {
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

        if (mapping.getStock() != null && item.quantity() > mapping.getStock()) {
            throw new BusinessException("Estoque insuficiente para "
                    + mapping.getProductName()
                    + (StringUtils.hasText(mapping.getVariantName()) ? " - " + mapping.getVariantName() : "")
                    + ". Disponível: " + mapping.getStock()
                    + ", solicitado: " + item.quantity() + ".");
        }

        return new ResolvedOrderItem(mapping, item.quantity());
    }

    private WhatsappOrder saveLocalOrder(
            CreateDraftOrderRequest request,
            String customerLastname,
            String customerEmail,
            String whatsappMessageId,
            List<ResolvedOrderItem> resolvedItems,
            NuvemshopDraftOrderResponse nuvemshopResponse,
            String checkoutUrl
    ) {
        WhatsappOrder order = WhatsappOrder.builder()
                .customerName(request.customerName().trim())
                .customerLastname(customerLastname)
                .customerEmail(customerEmail)
                .customerPhone(onlyDigitsOrNull(request.customerPhone()))
                .customerDocument(onlyDigitsOrNull(request.cpfCnpj()))
                .shippingPostalCode(onlyDigitsOrNull(request.shippingPostalCode()))
                .shippingStreet(trimToNull(request.shippingStreet()))
                .shippingNumber(trimToNull(request.shippingNumber()))
                .shippingComplement(trimToNull(request.shippingComplement()))
                .shippingNeighborhood(trimToNull(request.shippingNeighborhood()))
                .shippingCity(trimToNull(request.shippingCity()))
                .shippingState(trimToNull(request.shippingState()))
                .whatsappMessageId(trimToNull(whatsappMessageId))
                .source(WebhookSource.WHATSAPP)
                .status(OrderStatus.AGUARDANDO_PAGAMENTO)
                .statusPublicToken(generateStatusPublicToken())
                .paymentStatus(firstText(nuvemshopResponse.paymentStatus(), "unpaid"))
                .shippingStatus(firstText(nuvemshopResponse.shippingStatus(), "pending"))
                .nuvemshopDraftOrderId(nuvemshopResponse.id())
                .checkoutUrl(checkoutUrl)
                .abandonedCheckoutUrl(trimToNull(nuvemshopResponse.abandonedCheckoutUrl()))
                .pixCopyPaste(trimToNull(nuvemshopResponse.pixCopyPaste()))
                .pixQrCodeUrl(trimToNull(nuvemshopResponse.pixQrCodeUrl()))
                .total(nuvemshopResponse.total())
                .rawNuvemshopResponse(writeRawResponse(nuvemshopResponse))
                .build();

        for (ResolvedOrderItem resolvedItem : resolvedItems) {
            ProductMapping mapping = resolvedItem.mapping();
            order.addItem(WhatsappOrderItem.builder()
                    .productMapping(mapping)
                    .nuvemshopProductId(mapping.getNuvemshopProductId())
                    .nuvemshopVariantId(mapping.getNuvemshopVariantId())
                    .productName(mapping.getProductName())
                    .variantName(mapping.getVariantName())
                    .imageUrl(mapping.getImageUrl())
                    .quantity(resolvedItem.quantity())
                    .unitPrice(mapping.getPrice())
                    .build());
        }

        return orderRepository.save(order);
    }

    private CreateDraftOrderResponse toCreateDraftOrderResponse(WhatsappOrder order, String message) {
        return new CreateDraftOrderResponse(
                order.getId(),
                order.getNuvemshopDraftOrderId(),
                order.getStatus(),
                order.getCheckoutUrl(),
                order.getAbandonedCheckoutUrl(),
                order.getStatusPublicToken(),
                order.getTotal(),
                message
        );
    }

    private String generateStatusPublicToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder token = new StringBuilder(STATUS_TOKEN_PREFIX);
        for (byte value : bytes) {
            token.append(String.format(Locale.ROOT, "%02x", value));
        }
        return token.toString();
    }

    private String normalizeEmail(String email, String phone) {
        if (StringUtils.hasText(email)) {
            return email.trim();
        }

        String phoneDigits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (!StringUtils.hasText(phoneDigits)) {
            phoneDigits = UUID.randomUUID().toString();
        }
        return "cliente+" + phoneDigits + "@" + DEFAULT_EMAIL_DOMAIN;
    }

    private String writeRawResponse(NuvemshopDraftOrderResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            return "{\"serializationError\":\"" + exception.getOriginalMessage() + "\"}";
        }
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : trimToNull(second);
    }

    private List<NuvemshopDraftOrderShippingRequest> buildShipping(CreateDraftOrderRequest request) {
        if (!StringUtils.hasText(request.shippingPostalCode())
                && !StringUtils.hasText(request.shippingStreet())
                && !StringUtils.hasText(request.shippingNumber())) {
            return null;
        }

        NuvemshopDraftOrderShippingAddressRequest address = new NuvemshopDraftOrderShippingAddressRequest(
                trimToNull(request.shippingStreet()),
                trimToNull(request.shippingNumber()),
                trimToNull(request.shippingComplement()),
                trimToNull(request.shippingNeighborhood()),
                trimToNull(request.shippingCity()),
                trimToNull(request.shippingState()),
                onlyDigitsOrNull(request.shippingPostalCode())
        );
        return List.of(new NuvemshopDraftOrderShippingRequest("0.00", address));
    }

    private String onlyDigitsOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return StringUtils.hasText(digits) ? digits : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record ResolvedOrderItem(ProductMapping mapping, Integer quantity) {
    }
}
