package br.com.llfashion.whatsappcheckout.controller;

import br.com.llfashion.whatsappcheckout.client.ViaCepClient;
import br.com.llfashion.whatsappcheckout.dto.request.StorefrontAddCartItemRequest;
import br.com.llfashion.whatsappcheckout.dto.request.StorefrontAddressRequest;
import br.com.llfashion.whatsappcheckout.dto.request.StorefrontCustomerRequest;
import br.com.llfashion.whatsappcheckout.dto.request.StorefrontSelectShippingRequest;
import br.com.llfashion.whatsappcheckout.dto.request.StorefrontUpdateCartItemRequest;
import br.com.llfashion.whatsappcheckout.dto.response.StorefrontCartResponse;
import br.com.llfashion.whatsappcheckout.dto.response.StorefrontCategoryResponse;
import br.com.llfashion.whatsappcheckout.dto.response.StorefrontCheckoutResponse;
import br.com.llfashion.whatsappcheckout.dto.response.StorefrontProductDetailResponse;
import br.com.llfashion.whatsappcheckout.dto.response.StorefrontProductSummaryResponse;
import br.com.llfashion.whatsappcheckout.dto.response.StorefrontSessionResponse;
import br.com.llfashion.whatsappcheckout.dto.response.StorefrontShippingOptionResponse;
import br.com.llfashion.whatsappcheckout.dto.response.ViaCepAddressResponse;
import br.com.llfashion.whatsappcheckout.exception.EntityNotFoundException;
import br.com.llfashion.whatsappcheckout.service.StorefrontCartService;
import br.com.llfashion.whatsappcheckout.service.StorefrontCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/storefront")
public class StorefrontController {

    private final StorefrontCatalogService catalogService;
    private final StorefrontCartService cartService;
    private final ViaCepClient viaCepClient;

    public StorefrontController(StorefrontCatalogService catalogService, StorefrontCartService cartService, ViaCepClient viaCepClient) {
        this.catalogService = catalogService;
        this.cartService = cartService;
        this.viaCepClient = viaCepClient;
    }

    @GetMapping("/session/start")
    public StorefrontSessionResponse startSession(@RequestParam(required = false) String phone) {
        return cartService.startSession(phone);
    }

    @GetMapping("/session/recover/{cartToken}")
    public StorefrontSessionResponse recoverSession(@PathVariable String cartToken) {
        return cartService.recoverSessionFromToken(cartToken);
    }

    @GetMapping("/categories")
    public List<StorefrontCategoryResponse> listCategories() {
        return catalogService.listCategories();
    }

    @GetMapping("/products")
    public List<StorefrontProductSummaryResponse> listProducts(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return catalogService.listProducts(categoryId, page, size);
    }

    @GetMapping("/products/{productId}")
    public StorefrontProductDetailResponse getProduct(@PathVariable Long productId) {
        return catalogService.getProduct(productId);
    }

    @GetMapping("/products/{productId}/variants")
    public StorefrontProductDetailResponse getProductVariants(@PathVariable Long productId) {
        return catalogService.getProduct(productId);
    }

    @GetMapping("/cart/{cartToken}")
    public StorefrontCartResponse getCart(@PathVariable String cartToken) {
        return cartService.getCart(cartToken);
    }

    @PostMapping("/cart/{cartToken}/items")
    public StorefrontCartResponse addItem(
            @PathVariable String cartToken,
            @Valid @RequestBody StorefrontAddCartItemRequest request
    ) {
        return cartService.addItem(cartToken, request.nuvemshopVariantId(), request.quantity());
    }

    @PutMapping("/cart/{cartToken}/items/{itemId}")
    public StorefrontCartResponse updateItem(
            @PathVariable String cartToken,
            @PathVariable UUID itemId,
            @Valid @RequestBody StorefrontUpdateCartItemRequest request
    ) {
        return cartService.updateItem(cartToken, itemId, request.quantity());
    }

    @DeleteMapping("/cart/{cartToken}/items/{itemId}")
    public StorefrontCartResponse removeItem(@PathVariable String cartToken, @PathVariable UUID itemId) {
        return cartService.removeItem(cartToken, itemId);
    }

    @PostMapping("/checkout/{cartToken}/customer")
    public StorefrontCartResponse saveCustomer(
            @PathVariable String cartToken,
            @Valid @RequestBody StorefrontCustomerRequest request
    ) {
        return cartService.saveCustomer(cartToken, request);
    }

    @PostMapping("/checkout/{cartToken}/address")
    public StorefrontCartResponse saveAddress(
            @PathVariable String cartToken,
            @Valid @RequestBody StorefrontAddressRequest request
    ) {
        return cartService.saveAddress(cartToken, request);
    }

    @GetMapping("/address/cep/{cep}")
    public ViaCepAddressResponse findAddress(@PathVariable String cep) {
        return viaCepClient.buscarEndereco(cep)
                .orElseThrow(() -> new EntityNotFoundException("CEP não encontrado: " + cep));
    }

    @PostMapping("/checkout/{cartToken}/shipping-options")
    public List<StorefrontShippingOptionResponse> shippingOptions(@PathVariable String cartToken) {
        return cartService.shippingOptions(cartToken);
    }

    @PostMapping("/checkout/{cartToken}/select-shipping")
    public StorefrontCartResponse selectShipping(
            @PathVariable String cartToken,
            @Valid @RequestBody StorefrontSelectShippingRequest request
    ) {
        return cartService.selectShipping(cartToken, request.shippingCode());
    }

    @PostMapping("/checkout/{cartToken}/create-payment-link")
    public StorefrontCheckoutResponse createPaymentLink(@PathVariable String cartToken) {
        return cartService.createPaymentLink(cartToken);
    }
}
