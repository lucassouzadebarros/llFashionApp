package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.client.NuvemshopApiClient;
import br.com.llfashion.whatsappcheckout.config.CheckoutProperties;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopProductResponse;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopVariantResponse;
import br.com.llfashion.whatsappcheckout.dto.request.CreateDraftOrderItemRequest;
import br.com.llfashion.whatsappcheckout.dto.request.CreateDraftOrderRequest;
import br.com.llfashion.whatsappcheckout.dto.request.StorefrontAddressRequest;
import br.com.llfashion.whatsappcheckout.dto.request.StorefrontCustomerRequest;
import br.com.llfashion.whatsappcheckout.dto.response.CreateDraftOrderResponse;
import br.com.llfashion.whatsappcheckout.dto.response.StorefrontCartItemResponse;
import br.com.llfashion.whatsappcheckout.dto.response.StorefrontCartResponse;
import br.com.llfashion.whatsappcheckout.dto.response.StorefrontCheckoutResponse;
import br.com.llfashion.whatsappcheckout.dto.response.StorefrontSessionResponse;
import br.com.llfashion.whatsappcheckout.dto.response.StorefrontShippingOptionResponse;
import br.com.llfashion.whatsappcheckout.entity.NuvemshopInstallation;
import br.com.llfashion.whatsappcheckout.entity.ProductMapping;
import br.com.llfashion.whatsappcheckout.entity.StorefrontCart;
import br.com.llfashion.whatsappcheckout.entity.StorefrontCartItem;
import br.com.llfashion.whatsappcheckout.enums.StorefrontCartStatus;
import br.com.llfashion.whatsappcheckout.exception.BusinessException;
import br.com.llfashion.whatsappcheckout.exception.EntityNotFoundException;
import br.com.llfashion.whatsappcheckout.exception.NuvemshopApiException;
import br.com.llfashion.whatsappcheckout.mapper.NuvemshopProductMapper;
import br.com.llfashion.whatsappcheckout.repository.StorefrontCartRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class StorefrontCartService {

    private static final String DEFAULT_LASTNAME = "Cliente";
    private static final Set<StorefrontCartStatus> REUSABLE_STATUSES = Set.of(
            StorefrontCartStatus.CART_OPEN,
            StorefrontCartStatus.MINIMUM_NOT_REACHED,
            StorefrontCartStatus.MINIMUM_REACHED,
            StorefrontCartStatus.CUSTOMER_DATA_PENDING,
            StorefrontCartStatus.ADDRESS_PENDING,
            StorefrontCartStatus.SHIPPING_PENDING,
            StorefrontCartStatus.SHIPPING_SELECTED,
            StorefrontCartStatus.WAITING_CONFIRMATION
    );

    private final CheckoutProperties checkoutProperties;
    private final ProductMappingService productMappingService;
    private final DraftOrderService draftOrderService;
    private final OrderTrackingService orderTrackingService;
    private final NuvemshopInstallationService installationService;
    private final NuvemshopApiClient nuvemshopApiClient;
    private final StorefrontCatalogService catalogService;
    private final StorefrontCartRepository cartRepository;
    private final NuvemshopProductMapper productMapper;

    public StorefrontCartService(
            CheckoutProperties checkoutProperties,
            ProductMappingService productMappingService,
            DraftOrderService draftOrderService,
            OrderTrackingService orderTrackingService,
            NuvemshopInstallationService installationService,
            NuvemshopApiClient nuvemshopApiClient,
            StorefrontCatalogService catalogService,
            StorefrontCartRepository cartRepository,
            NuvemshopProductMapper productMapper
    ) {
        this.checkoutProperties = checkoutProperties;
        this.productMappingService = productMappingService;
        this.draftOrderService = draftOrderService;
        this.orderTrackingService = orderTrackingService;
        this.installationService = installationService;
        this.nuvemshopApiClient = nuvemshopApiClient;
        this.catalogService = catalogService;
        this.cartRepository = cartRepository;
        this.productMapper = productMapper;
    }

    @Transactional
    public StorefrontSessionResponse startSession(String phone) {
        String normalizedPhone = onlyDigits(phone);
        if (StringUtils.hasText(normalizedPhone)) {
            StorefrontCart reusableCart = cartRepository
                    .findFirstByPhoneNumberAndStatusInOrderByUpdatedAtDesc(normalizedPhone, REUSABLE_STATUSES)
                    .filter(cart -> cart.getExpiresAt().isAfter(LocalDateTime.now()))
                    .orElse(null);
            if (reusableCart != null) {
                recalculateAndSave(reusableCart);
                return new StorefrontSessionResponse(reusableCart.getCartToken(), toResponse(reusableCart));
            }
        }

        StorefrontCart cart = StorefrontCart.builder()
                .cartToken(UUID.randomUUID().toString().replace("-", ""))
                .phoneNumber(trimToNull(normalizedPhone))
                .customerPhone(trimToNull(normalizedPhone))
                .status(StorefrontCartStatus.CART_OPEN)
                .subtotal(BigDecimal.ZERO)
                .shippingPrice(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .minimumOrderValue(checkoutProperties.resolvedMinimumOrderTotal())
                .expiresAt(LocalDateTime.now().plus(checkoutProperties.resolvedCartExpiration()))
                .build();

        cartRepository.save(cart);
        return new StorefrontSessionResponse(cart.getCartToken(), toResponse(cart));
    }

    @Transactional
    public StorefrontCartResponse getCart(String cartToken) {
        return toResponse(findActiveCart(cartToken));
    }

    @Transactional
    public StorefrontSessionResponse recoverSessionFromToken(String cartToken) {
        if (!StringUtils.hasText(cartToken)) {
            return startSession(null);
        }

        StorefrontCart expiredOrExistingCart = cartRepository.findByCartToken(cartToken.trim()).orElse(null);
        if (expiredOrExistingCart == null) {
            return startSession(null);
        }

        if (expiredOrExistingCart.getExpiresAt().isBefore(LocalDateTime.now())) {
            expiredOrExistingCart.setStatus(StorefrontCartStatus.EXPIRED);
            cartRepository.save(expiredOrExistingCart);
        }

        return startSession(firstText(expiredOrExistingCart.getPhoneNumber(), expiredOrExistingCart.getCustomerPhone()));
    }

    @Transactional
    public StorefrontCartResponse addItem(String cartToken, Long nuvemshopVariantId, Integer quantity) {
        StorefrontCart cart = findActiveCart(cartToken);
        ProductMapping mapping = productMappingService.findActiveByNuvemshopVariantId(nuvemshopVariantId);
        mapping = refreshMappingFromNuvemshop(mapping);
        validateStock(mapping, quantity);
        Long refreshedVariantId = mapping.getNuvemshopVariantId();

        StorefrontCartItem existingItem = cart.getItems().stream()
                .filter(item -> item.getNuvemshopVariantId().equals(refreshedVariantId))
                .findFirst()
                .orElse(null);

        if (existingItem != null) {
            int newQuantity = existingItem.getQuantity() + quantity;
            validateStock(mapping, newQuantity);
            existingItem.setProductMapping(mapping);
            existingItem.setQuantity(newQuantity);
            existingItem.setStockAtSelection(mapping.getStock());
            existingItem.setUnitPrice(price(mapping));
            existingItem.setTotalPrice(price(mapping).multiply(BigDecimal.valueOf(newQuantity)));
        } else {
            StorefrontCartItem item = StorefrontCartItem.builder()
                    .productMapping(mapping)
                    .nuvemshopProductId(mapping.getNuvemshopProductId())
                    .nuvemshopVariantId(mapping.getNuvemshopVariantId())
                    .productName(mapping.getProductName())
                    .variantName(mapping.getVariantName())
                    .size(catalogService.toVariantResponse(mapping).size())
                    .color(catalogService.toVariantResponse(mapping).color())
                    .model(catalogService.toVariantResponse(mapping).model())
                    .imageUrl(mapping.getImageUrl())
                    .quantity(quantity)
                    .unitPrice(price(mapping))
                    .totalPrice(price(mapping).multiply(BigDecimal.valueOf(quantity)))
                    .stockAtSelection(mapping.getStock())
                    .build();
            cart.addItem(item);
        }

        recalculateAndSave(cart);
        return toResponse(cart);
    }

    @Transactional
    public StorefrontCartResponse updateItem(String cartToken, UUID itemId, Integer quantity) {
        StorefrontCart cart = findActiveCart(cartToken);
        StorefrontCartItem item = findItem(cart, itemId);
        ProductMapping mapping = item.getProductMapping() == null
                ? productMappingService.findActiveByNuvemshopVariantId(item.getNuvemshopVariantId())
                : item.getProductMapping();
        mapping = refreshMappingFromNuvemshop(mapping);
        validateStock(mapping, quantity);

        item.setProductMapping(mapping);
        item.setQuantity(quantity);
        item.setUnitPrice(price(mapping));
        item.setTotalPrice(price(mapping).multiply(BigDecimal.valueOf(quantity)));
        item.setStockAtSelection(mapping.getStock());
        recalculateAndSave(cart);
        return toResponse(cart);
    }

    @Transactional
    public StorefrontCartResponse removeItem(String cartToken, UUID itemId) {
        StorefrontCart cart = findActiveCart(cartToken);
        StorefrontCartItem item = findItem(cart, itemId);
        cart.removeItem(item);
        recalculateAndSave(cart);
        return toResponse(cart);
    }

    @Transactional
    public StorefrontCartResponse saveCustomer(String cartToken, StorefrontCustomerRequest request) {
        StorefrontCart cart = findActiveCart(cartToken);
        NameParts nameParts = splitName(request.fullName());
        cart.setCustomerName(nameParts.firstName());
        cart.setCustomerLastname(nameParts.lastName());
        cart.setCustomerDocument(onlyDigits(request.cpfCnpj()));
        cart.setCustomerEmail(request.email().trim().toLowerCase());
        cart.setCustomerPhone(onlyDigits(request.phone()));
        cart.setPhoneNumber(onlyDigits(request.phone()));
        cart.setStatus(StorefrontCartStatus.ADDRESS_PENDING);
        cartRepository.save(cart);
        return toResponse(cart);
    }

    @Transactional
    public StorefrontCartResponse saveAddress(String cartToken, StorefrontAddressRequest request) {
        StorefrontCart cart = findActiveCart(cartToken);
        cart.setPostalCode(onlyDigits(request.postalCode()));
        cart.setAddressStreet(request.street().trim());
        cart.setAddressNumber(request.number().trim());
        cart.setAddressComplement(trimToNull(request.complement()));
        cart.setAddressNeighborhood(request.neighborhood().trim());
        cart.setAddressCity(request.city().trim());
        cart.setAddressState(request.state().trim().toUpperCase());
        clearSelectedShipping(cart);
        cart.setStatus(StorefrontCartStatus.WAITING_CONFIRMATION);
        recalculateAndSave(cart);
        return toResponse(cart);
    }

    @Transactional(readOnly = true)
    public List<StorefrontShippingOptionResponse> shippingOptions(String cartToken) {
        StorefrontCart cart = findActiveCart(cartToken);
        if (!StringUtils.hasText(cart.getPostalCode())) {
            throw new BusinessException("Informe o endereço antes de consultar o frete.");
        }

        return List.of(
                new StorefrontShippingOptionResponse("PAC", "PAC", "Entrega econômica", "7 a 10 dias úteis", new BigDecimal("18.90"), false),
                new StorefrontShippingOptionResponse("SEDEX", "Sedex", "Entrega mais rápida", "2 a 4 dias úteis", new BigDecimal("27.90"), false),
                new StorefrontShippingOptionResponse("TRANSPORTADORA", "Transportadora", "Prazo confirmado no atendimento", "3 a 6 dias úteis", new BigDecimal("39.90"), false),
                new StorefrontShippingOptionResponse("RETIRADA", "Retirada na loja", "Retire sem custo de frete", "Combinar retirada", BigDecimal.ZERO, false)
        );
    }

    @Transactional
    public StorefrontCartResponse selectShipping(String cartToken, String shippingCode) {
        StorefrontCart cart = findActiveCart(cartToken);
        StorefrontShippingOptionResponse option = shippingOptions(cartToken).stream()
                .filter(candidate -> candidate.code().equalsIgnoreCase(shippingCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Forma de envio não encontrada: " + shippingCode));

        cart.setSelectedShippingCode(option.code());
        cart.setSelectedShippingName(option.name());
        cart.setSelectedShippingEta(option.eta());
        cart.setShippingPrice(option.price());
        cart.setStatus(StorefrontCartStatus.SHIPPING_SELECTED);
        recalculateAndSave(cart);
        return toResponse(cart);
    }

    @Transactional
    public StorefrontCheckoutResponse createPaymentLink(String cartToken) {
        StorefrontCart cart = findActiveCartForCheckout(cartToken);
        if (StringUtils.hasText(cart.getCheckoutUrl()) && cart.getLocalOrderId() != null) {
            String statusToken = orderTrackingService.findStatusTokenByLocalOrderId(cart.getLocalOrderId()).orElse(null);
            return new StorefrontCheckoutResponse(
                    cart.getCartToken(),
                    cart.getLocalOrderId(),
                    cart.getNuvemshopDraftOrderId(),
                    cart.getCheckoutUrl(),
                    null,
                    null,
                    statusToken,
                    orderTrackingService.statusUrl(statusToken),
                    cart.getTotal(),
                    "Link de pagamento já gerado para este carrinho."
            );
        }
        clearSelectedShipping(cart);
        if (cart.getStatus() == StorefrontCartStatus.SHIPPING_PENDING
                || cart.getStatus() == StorefrontCartStatus.SHIPPING_SELECTED) {
            cart.setStatus(StorefrontCartStatus.WAITING_CONFIRMATION);
        }
        validateReadyForCheckout(cart);
        validateCurrentStock(cart);

        CreateDraftOrderResponse order;
        try {
            order = draftOrderService.createDraftOrder(toDraftOrderRequest(cart));
        } catch (NuvemshopApiException exception) {
            if (isStockError(exception)) {
                throw new BusinessException("O estoque mudou na Nuvemshop antes de finalizar o pedido. Atualize o carrinho e ajuste a quantidade dos itens indisponíveis.", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            throw exception;
        }
        cart.setStatus(StorefrontCartStatus.PAYMENT_LINK_GENERATED);
        cart.setCheckoutUrl(order.checkoutUrl());
        cart.setLocalOrderId(order.localOrderId());
        cart.setNuvemshopDraftOrderId(order.nuvemshopDraftOrderId());
        cartRepository.save(cart);

        return new StorefrontCheckoutResponse(
                cart.getCartToken(),
                order.localOrderId(),
                order.nuvemshopDraftOrderId(),
                order.checkoutUrl(),
                null,
                null,
                order.statusPublicToken(),
                orderTrackingService.statusUrl(order.statusPublicToken()),
                order.total() == null ? cart.getTotal() : order.total(),
                "Link de pagamento gerado com sucesso."
        );
    }

    public String storefrontUrlForPhone(String phone) {
        StorefrontSessionResponse session = startSession(phone);
        String baseUrl = checkoutProperties.resolvedFrontendBaseUrl();
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "token=" + session.cartToken();
    }

    private StorefrontCart findActiveCart(String cartToken) {
        if (!StringUtils.hasText(cartToken)) {
            throw new BusinessException("cartToken é obrigatório.");
        }
        StorefrontCart cart = cartRepository.findByCartToken(cartToken.trim())
                .orElseThrow(() -> new EntityNotFoundException("Carrinho não encontrado: " + cartToken));
        if (cart.getExpiresAt().isBefore(LocalDateTime.now())) {
            cart.setStatus(StorefrontCartStatus.EXPIRED);
            cartRepository.save(cart);
            throw new BusinessException("Carrinho expirado. Inicie um novo pedido.", HttpStatus.GONE);
        }
        return cart;
    }

    private StorefrontCart findActiveCartForCheckout(String cartToken) {
        if (!StringUtils.hasText(cartToken)) {
            throw new BusinessException("cartToken é obrigatório.");
        }
        StorefrontCart cart = cartRepository.findByCartTokenForUpdate(cartToken.trim())
                .orElseThrow(() -> new EntityNotFoundException("Carrinho não encontrado: " + cartToken));
        if (cart.getExpiresAt().isBefore(LocalDateTime.now())) {
            cart.setStatus(StorefrontCartStatus.EXPIRED);
            cartRepository.save(cart);
            throw new BusinessException("Carrinho expirado. Inicie um novo pedido.", HttpStatus.GONE);
        }
        return cart;
    }

    private StorefrontCartItem findItem(StorefrontCart cart, UUID itemId) {
        return cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Item do carrinho não encontrado: " + itemId));
    }

    private void validateReadyForCheckout(StorefrontCart cart) {
        if (cart.getItems().isEmpty()) {
            throw new BusinessException("Carrinho vazio.");
        }
        if (cart.getSubtotal().compareTo(cart.getMinimumOrderValue()) < 0) {
            throw new BusinessException("Pedido mínimo de " + cart.getMinimumOrderValue() + " ainda não foi atingido.");
        }
        if (!StringUtils.hasText(cart.getCustomerName())
                || !StringUtils.hasText(cart.getCustomerDocument())
                || !StringUtils.hasText(cart.getCustomerEmail())
                || !StringUtils.hasText(cart.getCustomerPhone())) {
            throw new BusinessException("Preencha os dados da cliente antes de gerar o pagamento.");
        }
        if (!StringUtils.hasText(cart.getPostalCode())
                || !StringUtils.hasText(cart.getAddressStreet())
                || !StringUtils.hasText(cart.getAddressNumber())) {
            throw new BusinessException("Preencha o endereço de entrega antes de gerar o pagamento.");
        }
    }

    private void clearSelectedShipping(StorefrontCart cart) {
        cart.setSelectedShippingCode(null);
        cart.setSelectedShippingName(null);
        cart.setSelectedShippingEta(null);
        cart.setShippingPrice(BigDecimal.ZERO);
    }

    private void validateCurrentStock(StorefrontCart cart) {
        refreshCurrentStockFromNuvemshop(cart);
        for (StorefrontCartItem item : cart.getItems()) {
            ProductMapping mapping = productMappingService.findActiveByNuvemshopVariantId(item.getNuvemshopVariantId());
            validateStock(mapping, item.getQuantity());
        }
    }

    private ProductMapping refreshMappingFromNuvemshop(ProductMapping mapping) {
        NuvemshopInstallation installation = installationService.getActiveInstallation();
        NuvemshopProductResponse product = nuvemshopApiClient.buscarProduto(
                installation.getStoreId(),
                installation.getAccessToken(),
                mapping.getNuvemshopProductId()
        );
        NuvemshopVariantResponse variant = product.variants() == null
                ? null
                : product.variants().stream()
                        .filter(candidate -> mapping.getNuvemshopVariantId().equals(candidate.id()))
                        .findFirst()
                        .orElse(null);

        return productMappingService.updateStockPriceAndImage(
                mapping.getNuvemshopVariantId(),
                variant == null ? 0 : variant.stock(),
                effectivePrice(variant, mapping.getPrice()),
                hasPromotionalPrice(variant),
                productMapper.resolveVariantImageUrl(product, variant),
                productMapper.resolveProductImageUrl(product)
        );
    }

    private void refreshCurrentStockFromNuvemshop(StorefrontCart cart) {
        NuvemshopInstallation installation = installationService.getActiveInstallation();
        Map<Long, NuvemshopProductResponse> productsById = new HashMap<>();

        for (StorefrontCartItem item : cart.getItems()) {
            NuvemshopProductResponse product = productsById.computeIfAbsent(
                    item.getNuvemshopProductId(),
                    productId -> nuvemshopApiClient.buscarProduto(
                            installation.getStoreId(),
                            installation.getAccessToken(),
                            productId
                    )
            );

            NuvemshopVariantResponse variant = product.variants() == null
                    ? null
                    : product.variants().stream()
                            .filter(candidate -> item.getNuvemshopVariantId().equals(candidate.id()))
                            .findFirst()
                            .orElse(null);

            ProductMapping updatedMapping = productMappingService.updateStockPriceAndImage(
                    item.getNuvemshopVariantId(),
                    variant == null ? 0 : variant.stock(),
                    effectivePrice(variant, item.getUnitPrice()),
                    hasPromotionalPrice(variant),
                    productMapper.resolveVariantImageUrl(product, variant),
                    productMapper.resolveProductImageUrl(product)
            );
            item.setProductMapping(updatedMapping);
            item.setImageUrl(updatedMapping.getImageUrl());
            item.setStockAtSelection(updatedMapping.getStock());
            item.setUnitPrice(price(updatedMapping));
            item.setTotalPrice(price(updatedMapping).multiply(BigDecimal.valueOf(item.getQuantity())));
            validateStock(updatedMapping, item.getQuantity());
        }

        recalculateAndSave(cart);
    }

    private boolean isStockError(NuvemshopApiException exception) {
        String body = exception.getResponseBody();
        return exception.getStatusCode() == 422
                && body != null
                && (body.contains("does not have enough stock")
                || body.contains("Product variants not available")
                || body.contains("variant_errors"));
    }

    private void validateStock(ProductMapping mapping, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException("Quantidade deve ser maior que zero.");
        }
        int stock = mapping.getStock() == null ? 0 : mapping.getStock();
        if (stock <= 0) {
            throw new BusinessException("Produto sem estoque: " + mapping.getProductName());
        }
        if (quantity > stock) {
            throw new BusinessException("Estoque insuficiente para " + mapping.getProductName()
                    + variantSuffix(mapping) + ". Disponível: " + stock + ", solicitado: " + quantity + ".");
        }
    }

    private CreateDraftOrderRequest toDraftOrderRequest(StorefrontCart cart) {
        List<CreateDraftOrderItemRequest> items = cart.getItems().stream()
                .map(item -> new CreateDraftOrderItemRequest(item.getNuvemshopVariantId(), null, item.getQuantity()))
                .toList();
        return new CreateDraftOrderRequest(
                cart.getCustomerName(),
                StringUtils.hasText(cart.getCustomerLastname()) ? cart.getCustomerLastname() : DEFAULT_LASTNAME,
                cart.getCustomerEmail(),
                cart.getCustomerPhone(),
                items,
                cart.getCustomerDocument(),
                cart.getPostalCode(),
                cart.getAddressStreet(),
                cart.getAddressNumber(),
                cart.getAddressComplement(),
                cart.getAddressNeighborhood(),
                cart.getAddressCity(),
                cart.getAddressState()
        );
    }

    private void recalculateAndSave(StorefrontCart cart) {
        BigDecimal subtotal = cart.getItems().stream()
                .map(item -> {
                    ProductMapping mapping = item.getProductMapping();
                    BigDecimal unitPrice = mapping == null ? item.getUnitPrice() : price(mapping);
                    BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
                    item.setUnitPrice(unitPrice);
                    item.setTotalPrice(totalPrice);
                    return totalPrice;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        cart.setSubtotal(subtotal);
        BigDecimal shipping = cart.getShippingPrice() == null ? BigDecimal.ZERO : cart.getShippingPrice();
        cart.setTotal(subtotal.add(shipping));
        if (subtotal.compareTo(cart.getMinimumOrderValue()) < 0) {
            cart.setStatus(StorefrontCartStatus.MINIMUM_NOT_REACHED);
        } else if (cart.getStatus() == StorefrontCartStatus.CART_OPEN
                || cart.getStatus() == StorefrontCartStatus.MINIMUM_NOT_REACHED) {
            cart.setStatus(StorefrontCartStatus.MINIMUM_REACHED);
        }
        cartRepository.save(cart);
    }

    private StorefrontCartResponse toResponse(StorefrontCart cart) {
        BigDecimal missingAmount = cart.getMinimumOrderValue().subtract(cart.getSubtotal());
        if (missingAmount.compareTo(BigDecimal.ZERO) < 0) {
            missingAmount = BigDecimal.ZERO;
        }
        int progress = BigDecimal.ZERO.compareTo(cart.getMinimumOrderValue()) == 0
                ? 100
                : cart.getSubtotal()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(cart.getMinimumOrderValue(), 0, RoundingMode.DOWN)
                        .min(BigDecimal.valueOf(100))
                        .intValue();
        String statusPublicToken = cart.getLocalOrderId() == null
                ? null
                : orderTrackingService.findStatusTokenByLocalOrderId(cart.getLocalOrderId()).orElse(null);

        return new StorefrontCartResponse(
                cart.getCartToken(),
                cart.getStatus(),
                cart.getSubtotal(),
                cart.getShippingPrice(),
                cart.getTotal(),
                cart.getMinimumOrderValue(),
                missingAmount,
                progress,
                cart.getSubtotal().compareTo(cart.getMinimumOrderValue()) >= 0,
                cart.getSelectedShippingCode(),
                cart.getSelectedShippingName(),
                cart.getSelectedShippingEta(),
                cart.getCheckoutUrl(),
                statusPublicToken,
                orderTrackingService.statusUrl(statusPublicToken),
                fullName(cart),
                cart.getCustomerEmail(),
                cart.getCustomerPhone(),
                cart.getPostalCode(),
                cart.getAddressStreet(),
                cart.getAddressNumber(),
                cart.getAddressComplement(),
                cart.getAddressNeighborhood(),
                cart.getAddressCity(),
                cart.getAddressState(),
                cart.getExpiresAt(),
                cart.getItems().stream().map(this::toItemResponse).toList()
        );
    }

    private StorefrontCartItemResponse toItemResponse(StorefrontCartItem item) {
        ProductMapping mapping = item.getProductMapping();
        Integer currentStock = mapping == null ? item.getStockAtSelection() : mapping.getStock();
        return new StorefrontCartItemResponse(
                item.getId(),
                mapping == null ? null : mapping.getId(),
                item.getNuvemshopProductId(),
                item.getNuvemshopVariantId(),
                item.getProductName(),
                item.getVariantName(),
                item.getSize(),
                item.getColor(),
                item.getModel(),
                StringUtils.hasText(item.getImageUrl()) ? item.getImageUrl() : (mapping == null ? null : mapping.getImageUrl()),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                currentStock
        );
    }

    private BigDecimal price(ProductMapping mapping) {
        return mapping.getPrice() == null ? BigDecimal.ZERO : mapping.getPrice();
    }

    private BigDecimal effectivePrice(NuvemshopVariantResponse variant, BigDecimal fallbackPrice) {
        if (hasPromotionalPrice(variant)) {
            return variant.promotionalPrice();
        }
        if (variant != null && variant.price() != null) {
            return variant.price();
        }
        return fallbackPrice;
    }

    private boolean hasPromotionalPrice(NuvemshopVariantResponse variant) {
        if (variant == null || variant.promotionalPrice() == null) {
            return false;
        }
        BigDecimal promotionalPrice = variant.promotionalPrice();
        if (promotionalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return variant.price() == null || promotionalPrice.compareTo(variant.price()) < 0;
    }

    private String variantSuffix(ProductMapping mapping) {
        return StringUtils.hasText(mapping.getVariantName()) ? " - " + mapping.getVariantName() : "";
    }

    private NameParts splitName(String fullName) {
        String normalized = normalizeSpaces(fullName);
        int firstSpace = normalized.indexOf(' ');
        if (firstSpace < 0) {
            return new NameParts(normalized, DEFAULT_LASTNAME);
        }
        return new NameParts(normalized.substring(0, firstSpace), normalized.substring(firstSpace + 1));
    }

    private String fullName(StorefrontCart cart) {
        return normalizeSpaces((cart.getCustomerName() == null ? "" : cart.getCustomerName())
                + " "
                + (cart.getCustomerLastname() == null ? "" : cart.getCustomerLastname()));
    }

    private String normalizeSpaces(String value) {
        return StringUtils.hasText(value) ? value.trim().replaceAll("\\s+", " ") : "";
    }

    private String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : trimToNull(second);
    }

    private record NameParts(String firstName, String lastName) {
    }
}
