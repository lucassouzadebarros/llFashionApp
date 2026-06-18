package br.com.llfashion.whatsappcheckout.controller;

import br.com.llfashion.whatsappcheckout.dto.request.UpdateMetaRetailerIdRequest;
import br.com.llfashion.whatsappcheckout.dto.response.ProductMappingResponse;
import br.com.llfashion.whatsappcheckout.service.ProductMappingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products/mappings")
public class ProductMappingController {

    private final ProductMappingService productMappingService;

    public ProductMappingController(ProductMappingService productMappingService) {
        this.productMappingService = productMappingService;
    }

    @GetMapping
    public List<ProductMappingResponse> listMappings(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String metaProductRetailerId
    ) {
        return productMappingService.listMappings(active, productName, sku, metaProductRetailerId);
    }

    @PutMapping("/{id}/meta-retailer-id")
    public ProductMappingResponse updateMetaRetailerId(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMetaRetailerIdRequest request
    ) {
        return productMappingService.updateMetaRetailerId(id, request.metaProductRetailerId());
    }
}
