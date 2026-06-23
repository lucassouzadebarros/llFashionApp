package br.com.llfashion.whatsappcheckout.mapper;

import br.com.llfashion.whatsappcheckout.dto.response.ProductMappingResponse;
import br.com.llfashion.whatsappcheckout.entity.ProductMapping;
import org.springframework.stereotype.Component;

@Component
public class ProductMappingMapper {

    public ProductMappingResponse toResponse(ProductMapping productMapping) {
        return new ProductMappingResponse(
                productMapping.getId(),
                productMapping.getNuvemshopProductId(),
                productMapping.getNuvemshopVariantId(),
                productMapping.getSku(),
                productMapping.getMetaProductRetailerId(),
                productMapping.getProductName(),
                productMapping.getVariantName(),
                productMapping.getImageUrl(),
                productMapping.getProductImageUrl(),
                productMapping.getPrice(),
                productMapping.getStock(),
                productMapping.getPromotional(),
                productMapping.getActive()
        );
    }
}
