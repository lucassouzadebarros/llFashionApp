package br.com.llfashion.whatsappcheckout.mapper;

import br.com.llfashion.whatsappcheckout.dto.response.LocalOrderItemResponse;
import br.com.llfashion.whatsappcheckout.dto.response.LocalOrderResponse;
import br.com.llfashion.whatsappcheckout.entity.ProductMapping;
import br.com.llfashion.whatsappcheckout.entity.WhatsappOrder;
import br.com.llfashion.whatsappcheckout.entity.WhatsappOrderItem;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WhatsappOrderMapper {

    public LocalOrderResponse toResponse(WhatsappOrder order) {
        List<LocalOrderItemResponse> items = order.getItems()
                .stream()
                .map(this::toItemResponse)
                .toList();

        return new LocalOrderResponse(
                order.getId(),
                order.getCustomerName(),
                order.getCustomerLastname(),
                order.getCustomerEmail(),
                order.getCustomerPhone(),
                order.getCustomerDocument(),
                order.getShippingPostalCode(),
                order.getShippingStreet(),
                order.getShippingNumber(),
                order.getShippingComplement(),
                order.getShippingNeighborhood(),
                order.getShippingCity(),
                order.getShippingState(),
                order.getSource(),
                order.getStatus(),
                order.getNuvemshopDraftOrderId(),
                order.getCheckoutUrl(),
                order.getAbandonedCheckoutUrl(),
                order.getTotal(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items
        );
    }

    private LocalOrderItemResponse toItemResponse(WhatsappOrderItem item) {
        return new LocalOrderItemResponse(
                item.getId(),
                productMappingId(item.getProductMapping()),
                item.getNuvemshopProductId(),
                item.getNuvemshopVariantId(),
                item.getProductName(),
                item.getVariantName(),
                item.getQuantity(),
                item.getUnitPrice()
        );
    }

    private UUID productMappingId(ProductMapping productMapping) {
        return productMapping == null ? null : productMapping.getId();
    }
}
