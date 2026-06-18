package br.com.llfashion.whatsappcheckout.controller;

import br.com.llfashion.whatsappcheckout.dto.response.LatestOrderStatusResponse;
import br.com.llfashion.whatsappcheckout.dto.response.OrderStatusListResponse;
import br.com.llfashion.whatsappcheckout.dto.response.OrderStatusResponse;
import br.com.llfashion.whatsappcheckout.service.OrderTrackingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/status")
public class OrderStatusController {

    private final OrderTrackingService orderTrackingService;

    public OrderStatusController(OrderTrackingService orderTrackingService) {
        this.orderTrackingService = orderTrackingService;
    }

    @GetMapping("/{statusPublicToken}")
    public OrderStatusResponse getStatus(@PathVariable String statusPublicToken) {
        return orderTrackingService.getStatus(statusPublicToken);
    }

    @GetMapping("/access/{accessToken}")
    public OrderStatusListResponse getStatusByAccessToken(@PathVariable String accessToken) {
        return orderTrackingService.findOrdersByTemporaryAccessToken(accessToken);
    }

    @GetMapping("/latest")
    public LatestOrderStatusResponse latestByPhone(@RequestParam String phone) {
        return orderTrackingService.findLatestByPhone(phone);
    }

    @GetMapping("/customer")
    public OrderStatusListResponse byPhone(@RequestParam String phone) {
        return orderTrackingService.findOrdersByPhone(phone);
    }
}
