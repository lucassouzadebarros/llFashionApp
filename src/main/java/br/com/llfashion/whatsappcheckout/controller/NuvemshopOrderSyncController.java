package br.com.llfashion.whatsappcheckout.controller;

import br.com.llfashion.whatsappcheckout.dto.response.NuvemshopOrderImportResponse;
import br.com.llfashion.whatsappcheckout.service.NuvemshopSiteOrderSyncService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/nuvemshop/orders")
public class NuvemshopOrderSyncController {

    private final NuvemshopSiteOrderSyncService orderSyncService;

    public NuvemshopOrderSyncController(NuvemshopSiteOrderSyncService orderSyncService) {
        this.orderSyncService = orderSyncService;
    }

    @PostMapping("/import")
    public NuvemshopOrderImportResponse importOrders(@RequestParam(defaultValue = "180") int days) {
        return orderSyncService.importCreatedOrders(days);
    }
}
