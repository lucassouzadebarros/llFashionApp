package br.com.llfashion.whatsappcheckout.controller;

import br.com.llfashion.whatsappcheckout.dto.request.CreateDraftOrderItemRequest;
import br.com.llfashion.whatsappcheckout.dto.request.CreateDraftOrderRequest;
import br.com.llfashion.whatsappcheckout.dto.request.WhatsAppCartMockRequest;
import br.com.llfashion.whatsappcheckout.dto.response.CreateDraftOrderResponse;
import br.com.llfashion.whatsappcheckout.service.DraftOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mock/whatsapp")
public class WhatsAppOrderMockController {

    private final DraftOrderService draftOrderService;

    public WhatsAppOrderMockController(DraftOrderService draftOrderService) {
        this.draftOrderService = draftOrderService;
    }

    @PostMapping("/cart")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateDraftOrderResponse createFromWhatsAppCart(@Valid @RequestBody WhatsAppCartMockRequest request) {
        CreateDraftOrderRequest draftOrderRequest = new CreateDraftOrderRequest(
                request.customerName(),
                "Cliente",
                "",
                request.customerPhone(),
                request.items()
                        .stream()
                        .map(item -> new CreateDraftOrderItemRequest(null, item.productRetailerId(), item.quantity()))
                        .toList()
        );

        return draftOrderService.createDraftOrder(draftOrderRequest);
    }
}
