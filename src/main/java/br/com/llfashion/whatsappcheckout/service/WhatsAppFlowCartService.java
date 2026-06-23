package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.config.CheckoutProperties;
import br.com.llfashion.whatsappcheckout.dto.request.CreateDraftOrderItemRequest;
import br.com.llfashion.whatsappcheckout.dto.request.CreateDraftOrderRequest;
import br.com.llfashion.whatsappcheckout.dto.response.CreateDraftOrderResponse;
import br.com.llfashion.whatsappcheckout.entity.ProductMapping;
import br.com.llfashion.whatsappcheckout.entity.WhatsAppCheckoutSession;
import br.com.llfashion.whatsappcheckout.entity.WhatsAppCheckoutSessionItem;
import br.com.llfashion.whatsappcheckout.enums.WhatsAppCheckoutSessionStatus;
import br.com.llfashion.whatsappcheckout.exception.BusinessException;
import br.com.llfashion.whatsappcheckout.repository.WhatsAppCheckoutSessionRepository;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WhatsAppFlowCartService {

    private static final String DEFAULT_LASTNAME = "Cliente";
    private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");

    private final CheckoutProperties checkoutProperties;
    private final ProductMappingService productMappingService;
    private final WhatsAppCheckoutSessionRepository cartRepository;
    private final DraftOrderService draftOrderService;

    public WhatsAppFlowCartService(
            CheckoutProperties checkoutProperties,
            ProductMappingService productMappingService,
            WhatsAppCheckoutSessionRepository cartRepository,
            DraftOrderService draftOrderService
    ) {
        this.checkoutProperties = checkoutProperties;
        this.productMappingService = productMappingService;
        this.cartRepository = cartRepository;
        this.draftOrderService = draftOrderService;
    }

    @Transactional(readOnly = true)
    public int availableStock(String customerPhone, ProductMapping mapping) {
        int stock = mapping.getStock() == null ? 0 : mapping.getStock();
        int reserved = openCart(customerPhone)
                .map(cart -> cart.getItems()
                        .stream()
                        .filter(item -> mapping.getNuvemshopVariantId().equals(item.getNuvemshopVariantId()))
                        .mapToInt(WhatsAppCheckoutSessionItem::getQuantity)
                        .sum())
                .orElse(0);
        return Math.max(0, stock - reserved);
    }

    public BigDecimal minimumOrderTotal() {
        return checkoutProperties.resolvedMinimumOrderTotal();
    }

    @Transactional
    public CartSummary startCartFromWhatsAppOrder(
            String customerPhone,
            String customerName,
            String whatsappMessageId,
            List<CartItemInput> items
    ) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("Carrinho do WhatsApp não possui itens para abrir o Flow.");
        }

        if (StringUtils.hasText(whatsappMessageId)) {
            java.util.Optional<WhatsAppCheckoutSession> existingByMessage = cartRepository.findByWhatsappMessageId(whatsappMessageId.trim());
            if (existingByMessage.isPresent()) {
                return toSummary(existingByMessage.get());
            }
        }

        String phone = onlyDigits(customerPhone);
        openCart(phone).ifPresent(existing -> {
            existing.setStatus(WhatsAppCheckoutSessionStatus.CANCELLED);
            cartRepository.save(existing);
        });

        WhatsAppCheckoutSession cart = createOpenCart(phone, customerName, whatsappMessageId);
        List<String> skippedItems = new ArrayList<>();

        for (CartItemInput item : items) {
            ProductMapping mapping = resolveMapping(item.productRetailerId());
            int stock = mapping.getStock() == null ? 0 : mapping.getStock();
            int requestedQuantity = item.quantity() == null ? 1 : item.quantity();
            int allowedQuantity = Math.min(Math.max(requestedQuantity, 1), stock);

            if (allowedQuantity <= 0) {
                skippedItems.add(mapping.getProductName()
                        + (StringUtils.hasText(mapping.getVariantName()) ? " - " + mapping.getVariantName() : ""));
                continue;
            }

            cart.addItem(toCartItem(mapping, allowedQuantity));
        }

        if (cart.getItems().isEmpty()) {
            throw new BusinessException("Nenhum item do carrinho possui estoque disponível para continuar.");
        }

        cart.setSubtotal(subtotal(cart));
        cart.setMinimumOrderTotal(checkoutProperties.resolvedMinimumOrderTotal());
        CartSummary summary = toSummary(cartRepository.save(cart));
        if (!skippedItems.isEmpty()) {
            return summary.withNotice("Removi itens sem estoque: " + String.join(", ", skippedItems) + ".");
        }
        return summary;
    }

    @Transactional
    public CartSummary addSelectedItem(
            String customerPhone,
            String customerName,
            String whatsappMessageId,
            ProductMapping mapping,
            Integer quantity
    ) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException("Quantidade do item no carrinho deve ser maior que zero.");
        }

        WhatsAppCheckoutSession cart = openCart(customerPhone)
                .orElseGet(() -> createOpenCart(customerPhone, customerName, whatsappMessageId));

        int currentQuantity = cart.getItems()
                .stream()
                .filter(item -> mapping.getNuvemshopVariantId().equals(item.getNuvemshopVariantId()))
                .mapToInt(WhatsAppCheckoutSessionItem::getQuantity)
                .sum();
        int requestedTotal = currentQuantity + quantity;
        int stock = mapping.getStock() == null ? 0 : mapping.getStock();
        if (requestedTotal > stock) {
            throw new BusinessException("Estoque insuficiente para adicionar ao carrinho. Disponível agora: "
                    + Math.max(0, stock - currentQuantity) + " unidade(s).");
        }

        WhatsAppCheckoutSessionItem existingItem = cart.getItems()
                .stream()
                .filter(item -> mapping.getNuvemshopVariantId().equals(item.getNuvemshopVariantId()))
                .findFirst()
                .orElse(null);

        if (existingItem == null) {
            cart.addItem(toCartItem(mapping, quantity));
        } else {
            existingItem.setQuantity(requestedTotal);
        }

        cart.setStatus(WhatsAppCheckoutSessionStatus.CART_OPEN);
        cart.setSubtotal(subtotal(cart));
        cart.setMinimumOrderTotal(checkoutProperties.resolvedMinimumOrderTotal());
        cart.setCustomerName(trimOrDefault(customerName, cart.getCustomerName()));
        return toSummary(cartRepository.save(cart));
    }

    @Transactional(readOnly = true)
    public CartSummary currentSummary(String customerPhone) {
        return openCart(customerPhone)
                .map(this::toSummary)
                .orElseGet(() -> new CartSummary(0, BigDecimal.ZERO, checkoutProperties.resolvedMinimumOrderTotal(), "Carrinho vazio.", false, null));
    }

    @Transactional
    public CartSummary cancelOpenCart(String customerPhone) {
        java.util.Optional<WhatsAppCheckoutSession> openCart = openCart(customerPhone);
        if (openCart.isEmpty()) {
            return new CartSummary(
                    0,
                    BigDecimal.ZERO,
                    checkoutProperties.resolvedMinimumOrderTotal(),
                    "Pedido cancelado. Nenhum pedido foi criado.",
                    false,
                    null
            );
        }

        WhatsAppCheckoutSession cart = openCart.get();
        cart.setStatus(WhatsAppCheckoutSessionStatus.CANCELLED);
        return toSummary(cartRepository.save(cart))
                .withNotice("Pedido cancelado. Seu carrinho foi encerrado e nenhum pedido foi criado.");
    }

    @Transactional(readOnly = true)
    public List<CartItemOption> cartItemOptions(String customerPhone) {
        return openCart(customerPhone)
                .map(cart -> cart.getItems()
                        .stream()
                        .map(this::toCartItemOption)
                        .toList())
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public CartItemOption cartItemOption(String customerPhone, Long nuvemshopVariantId) {
        return openCart(customerPhone)
                .flatMap(cart -> cart.getItems()
                        .stream()
                        .filter(item -> nuvemshopVariantId.equals(item.getNuvemshopVariantId()))
                        .findFirst()
                        .map(this::toCartItemOption))
                .orElseThrow(() -> new BusinessException("Item do carrinho não encontrado para ajustar quantidade."));
    }

    @Transactional
    public CartSummary updateItemQuantity(String customerPhone, Long nuvemshopVariantId, Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new BusinessException("Quantidade do item no carrinho não pode ser negativa.");
        }

        WhatsAppCheckoutSession cart = openCart(customerPhone)
                .orElseThrow(() -> new BusinessException("Carrinho do WhatsApp não encontrado para ajustar quantidade."));

        WhatsAppCheckoutSessionItem item = cart.getItems()
                .stream()
                .filter(cartItem -> nuvemshopVariantId.equals(cartItem.getNuvemshopVariantId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Item do carrinho não encontrado para ajustar quantidade."));

        if (quantity == 0) {
            cart.getItems().remove(item);
        } else {
            int stock = itemStock(item);
            if (quantity > stock) {
                throw new BusinessException("Quantidade maior que o estoque disponível. Disponível: " + stock + " unidade(s).");
            }
            item.setQuantity(quantity);
        }

        cart.setSubtotal(subtotal(cart));
        cart.setMinimumOrderTotal(checkoutProperties.resolvedMinimumOrderTotal());
        cart.setStatus(WhatsAppCheckoutSessionStatus.CART_OPEN);
        return toSummary(cartRepository.save(cart));
    }

    @Transactional
    public CreateDraftOrderResponse createOrderFromOpenCart(String customerPhone, CartCustomerData customerData) {
        WhatsAppCheckoutSession cart = openCart(customerPhone)
                .orElseThrow(() -> new BusinessException("Carrinho do WhatsApp não encontrado para finalizar."));

        if (cart.getItems().isEmpty()) {
            throw new BusinessException("Carrinho do WhatsApp está vazio.");
        }

        validateMinimum(cart);
        validateFinalStock(cart);

        CreateDraftOrderRequest request = new CreateDraftOrderRequest(
                customerData.firstName(),
                customerData.lastName(),
                customerData.email(),
                cart.getCustomerPhone(),
                cart.getItems()
                        .stream()
                        .map(item -> new CreateDraftOrderItemRequest(item.getNuvemshopVariantId(), null, item.getQuantity()))
                        .toList(),
                customerData.document(),
                customerData.postalCode(),
                customerData.street(),
                customerData.number(),
                customerData.complement(),
                customerData.neighborhood(),
                customerData.city(),
                customerData.state()
        );

        CreateDraftOrderResponse order = draftOrderService.createDraftOrderFromWhatsAppWebhook(
                request,
                "flow-cart-" + cart.getId()
        );

        cart.setCustomerName(customerData.firstName());
        cart.setCustomerLastname(customerData.lastName());
        cart.setCustomerDocument(customerData.document());
        cart.setCustomerEmail(customerData.email());
        cart.setPostalCode(customerData.postalCode());
        cart.setAddressStreet(customerData.street());
        cart.setAddressNumber(customerData.number());
        cart.setAddressComplement(customerData.complement());
        cart.setAddressNeighborhood(customerData.neighborhood());
        cart.setAddressCity(customerData.city());
        cart.setAddressState(customerData.state());
        cart.setStatus(WhatsAppCheckoutSessionStatus.DRAFT_ORDER_CREATED);
        cart.setLocalOrderId(order.localOrderId());
        cart.setCheckoutUrl(order.checkoutUrl());
        cartRepository.save(cart);

        return order;
    }

    private WhatsAppCheckoutSession createOpenCart(String customerPhone, String customerName, String whatsappMessageId) {
        return WhatsAppCheckoutSession.builder()
                .whatsappMessageId(trimToNull(whatsappMessageId))
                .customerName(trimOrDefault(customerName, "Cliente WhatsApp"))
                .customerLastname(DEFAULT_LASTNAME)
                .customerPhone(onlyDigits(customerPhone))
                .status(WhatsAppCheckoutSessionStatus.CART_OPEN)
                .subtotal(BigDecimal.ZERO)
                .minimumOrderTotal(checkoutProperties.resolvedMinimumOrderTotal())
                .build();
    }

    private java.util.Optional<WhatsAppCheckoutSession> openCart(String customerPhone) {
        return cartRepository.findFirstByCustomerPhoneAndStatusOrderByUpdatedAtDesc(
                onlyDigits(customerPhone),
                WhatsAppCheckoutSessionStatus.CART_OPEN
        );
    }

    private WhatsAppCheckoutSessionItem toCartItem(ProductMapping mapping, Integer quantity) {
        return WhatsAppCheckoutSessionItem.builder()
                .productMapping(mapping)
                .nuvemshopProductId(mapping.getNuvemshopProductId())
                .nuvemshopVariantId(mapping.getNuvemshopVariantId())
                .productName(mapping.getProductName())
                .variantName(mapping.getVariantName())
                .quantity(quantity)
                .unitPrice(mapping.getPrice())
                .build();
    }

    private CartItemOption toCartItemOption(WhatsAppCheckoutSessionItem item) {
        int stock = itemStock(item);
        BigDecimal unitPrice = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
        String title = item.getProductName()
                + (StringUtils.hasText(item.getVariantName()) ? " - " + item.getVariantName() : "");
        return new CartItemOption(
                String.valueOf(item.getNuvemshopVariantId()),
                title,
                "Qtd atual: " + item.getQuantity() + " | Estoque: " + stock + " | " + money(subtotal),
                item.getNuvemshopVariantId(),
                item.getQuantity(),
                stock,
                unitPrice
        );
    }

    private int itemStock(WhatsAppCheckoutSessionItem item) {
        if (item.getProductMapping() != null && item.getProductMapping().getStock() != null) {
            return item.getProductMapping().getStock();
        }
        return 0;
    }

    private ProductMapping resolveMapping(String productRetailerId) {
        if (!StringUtils.hasText(productRetailerId)) {
            throw new BusinessException("Item do carrinho WhatsApp sem product_retailer_id.");
        }
        String value = productRetailerId.trim();
        if (isNumeric(value)) {
            return productMappingService.findActiveByNuvemshopVariantId(Long.valueOf(value));
        }
        return productMappingService.findActiveByMetaProductRetailerId(value);
    }

    private void validateMinimum(WhatsAppCheckoutSession cart) {
        BigDecimal minimum = checkoutProperties.resolvedMinimumOrderTotal();
        BigDecimal subtotal = subtotal(cart);
        if (subtotal.compareTo(minimum) < 0) {
            throw new BusinessException("Pedido mínimo não atingido. Subtotal: "
                    + money(subtotal) + ". Mínimo: " + money(minimum) + ".");
        }
    }

    private void validateFinalStock(WhatsAppCheckoutSession cart) {
        for (WhatsAppCheckoutSessionItem item : cart.getItems()) {
            ProductMapping mapping = item.getProductMapping();
            int stock = mapping == null || mapping.getStock() == null ? 0 : mapping.getStock();
            if (item.getQuantity() > stock) {
                throw new BusinessException("Estoque mudou. Disponível agora para "
                        + item.getProductName()
                        + (StringUtils.hasText(item.getVariantName()) ? " - " + item.getVariantName() : "")
                        + ": " + stock + " unidade(s).");
            }
        }
    }

    private CartSummary toSummary(WhatsAppCheckoutSession cart) {
        BigDecimal subtotal = subtotal(cart);
        BigDecimal minimum = checkoutProperties.resolvedMinimumOrderTotal();
        boolean canCheckout = subtotal.compareTo(minimum) >= 0;
        return new CartSummary(cart.getItems().size(), subtotal, minimum, summaryText(cart, subtotal, minimum), canCheckout, null);
    }

    private String summaryText(WhatsAppCheckoutSession cart, BigDecimal subtotal, BigDecimal minimum) {
        List<String> items = cart.getItems()
                .stream()
                .map(item -> item.getQuantity() + "x " + item.getProductName()
                        + (StringUtils.hasText(item.getVariantName()) ? " - " + item.getVariantName() : ""))
                .toList();
        String base = items.isEmpty() ? "Carrinho vazio." : String.join("\n", items);
        String total = "\n\nSubtotal: " + money(subtotal);
        if (subtotal.compareTo(minimum) >= 0) {
            return base + total;
        }
        return base + total + "\nPedido mínimo: " + money(minimum) + "\nFalta: " + money(minimum.subtract(subtotal));
    }

    private BigDecimal subtotal(WhatsAppCheckoutSession cart) {
        return cart.getItems()
                .stream()
                .map(item -> (item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice())
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(BRAZIL).format(value == null ? BigDecimal.ZERO : value);
    }

    private String trimOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
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

    public record CartSummary(
            int itemCount,
            BigDecimal subtotal,
            BigDecimal minimumOrderTotal,
            String summaryText,
            boolean canCheckout,
            String notice
    ) {
        public BigDecimal missingAmount() {
            BigDecimal missing = minimumOrderTotal.subtract(subtotal);
            return missing.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : missing;
        }

        public CartSummary withNotice(String notice) {
            return new CartSummary(itemCount, subtotal, minimumOrderTotal, summaryText, canCheckout, notice);
        }
    }

    public record CartItemInput(String productRetailerId, Integer quantity) {
    }

    public record CartItemOption(
            String id,
            String title,
            String description,
            Long nuvemshopVariantId,
            Integer quantity,
            Integer stock,
            BigDecimal unitPrice
    ) {
    }

    public record CartCustomerData(
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
