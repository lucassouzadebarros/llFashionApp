package br.com.llfashion.whatsappcheckout.dto.response;

import java.util.List;

public record OrderStatusListResponse(
        boolean found,
        boolean multiple,
        String message,
        int totalOrders,
        OrderStatusResponse order,
        List<OrderStatusSummaryResponse> orders
) {
}
