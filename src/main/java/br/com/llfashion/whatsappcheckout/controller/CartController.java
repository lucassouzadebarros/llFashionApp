package br.com.llfashion.whatsappcheckout.controller;

import br.com.llfashion.whatsappcheckout.dto.request.StorefrontAddCartItemRequest;
import br.com.llfashion.whatsappcheckout.dto.request.StorefrontUpdateCartItemRequest;
import br.com.llfashion.whatsappcheckout.dto.response.StorefrontCartResponse;
import br.com.llfashion.whatsappcheckout.dto.response.StorefrontCheckoutResponse;
import br.com.llfashion.whatsappcheckout.dto.response.StorefrontSessionResponse;
import br.com.llfashion.whatsappcheckout.service.StorefrontCartService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/carts")
public class CartController {

    private final StorefrontCartService cartService;

    public CartController(StorefrontCartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/start-from-whatsapp")
    public StorefrontSessionResponse startFromWhatsApp(@RequestParam(required = false) String phone) {
        return cartService.startSession(phone);
    }

    @GetMapping("/{cartToken}")
    public StorefrontCartResponse getCart(@PathVariable String cartToken) {
        return cartService.getCart(cartToken);
    }

    @PostMapping("/{cartToken}/items")
    public StorefrontCartResponse addItem(
            @PathVariable String cartToken,
            @Valid @RequestBody StorefrontAddCartItemRequest request
    ) {
        return cartService.addItem(cartToken, request.nuvemshopVariantId(), request.quantity());
    }

    @PatchMapping("/{cartToken}/items/{itemId}")
    public StorefrontCartResponse updateItem(
            @PathVariable String cartToken,
            @PathVariable UUID itemId,
            @Valid @RequestBody StorefrontUpdateCartItemRequest request
    ) {
        return cartService.updateItem(cartToken, itemId, request.quantity());
    }

    @DeleteMapping("/{cartToken}/items/{itemId}")
    public StorefrontCartResponse removeItem(@PathVariable String cartToken, @PathVariable UUID itemId) {
        return cartService.removeItem(cartToken, itemId);
    }

    @PostMapping("/{cartToken}/checkout")
    public StorefrontCheckoutResponse checkout(@PathVariable String cartToken) {
        return cartService.createPaymentLink(cartToken);
    }
}
