package br.com.llfashion.whatsappcheckout.controller;

import br.com.llfashion.whatsappcheckout.dto.request.CreateDraftOrderRequest;
import br.com.llfashion.whatsappcheckout.dto.response.CreateDraftOrderResponse;
import br.com.llfashion.whatsappcheckout.dto.response.DraftOrderSummaryResponse;
import br.com.llfashion.whatsappcheckout.dto.response.LocalOrderResponse;
import br.com.llfashion.whatsappcheckout.service.DraftOrderService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class DraftOrderController {

    private final DraftOrderService draftOrderService;

    public DraftOrderController(DraftOrderService draftOrderService) {
        this.draftOrderService = draftOrderService;
    }

    @PostMapping("/draft")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateDraftOrderResponse createDraftOrder(@Valid @RequestBody CreateDraftOrderRequest request) {
        return draftOrderService.createDraftOrder(request);
    }

    @GetMapping("/draft/{draftOrderId}")
    public DraftOrderSummaryResponse getDraftOrder(@PathVariable Long draftOrderId) {
        return draftOrderService.getDraftOrder(draftOrderId);
    }

    @GetMapping("/{localOrderId}")
    public LocalOrderResponse getLocalOrder(@PathVariable UUID localOrderId) {
        return draftOrderService.getLocalOrder(localOrderId);
    }
}
