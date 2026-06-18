package br.com.llfashion.whatsappcheckout.dto.response;

public record LatestOrderStatusResponse(
        boolean found,
        String message,
        OrderStatusResponse order,
        boolean multiple,
        java.util.List<OrderStatusSummaryResponse> orders
) {
}
