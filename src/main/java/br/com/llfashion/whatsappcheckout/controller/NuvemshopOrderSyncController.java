package br.com.llfashion.whatsappcheckout.controller;

import br.com.llfashion.whatsappcheckout.dto.response.NuvemshopOrderImportResponse;
import br.com.llfashion.whatsappcheckout.service.NuvemshopSiteOrderSyncService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/nuvemshop/orders")
public class NuvemshopOrderSyncController {

    private final NuvemshopSiteOrderSyncService orderSyncService;

    public NuvemshopOrderSyncController(NuvemshopSiteOrderSyncService orderSyncService) {
        this.orderSyncService = orderSyncService;
    }

    @RequestMapping(value = "/import", method = {RequestMethod.GET, RequestMethod.POST})
    public NuvemshopOrderImportResponse importOrders(@RequestParam(defaultValue = "180") int days) {
        return orderSyncService.importCreatedOrders(days);
    }
}
