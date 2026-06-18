package br.com.llfashion.whatsappcheckout.controller;

import br.com.llfashion.whatsappcheckout.dto.response.ProductSyncResponse;
import br.com.llfashion.whatsappcheckout.service.ProductSyncService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/nuvemshop/products")
public class ProductSyncController {

    private final ProductSyncService productSyncService;

    public ProductSyncController(ProductSyncService productSyncService) {
        this.productSyncService = productSyncService;
    }

    @PostMapping("/sync")
    public ProductSyncResponse syncProducts() {
        return productSyncService.syncProducts();
    }
}
