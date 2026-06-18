package br.com.llfashion.whatsappcheckout.dto.nuvemshop;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NuvemshopOrderResponse(
        Long id,
        String number,
        String status,
        @JsonProperty("payment_status")
        String paymentStatus,
        @JsonProperty("shipping_status")
        String shippingStatus,
        @JsonProperty("shipping_option")
        String shippingOption,
        @JsonProperty("shipping_method")
        String shippingMethod,
        @JsonProperty("shipping_tracking_number")
        String shippingTrackingNumber,
        @JsonProperty("tracking_number")
        String trackingNumber,
        @JsonProperty("shipping_tracking_url")
        String shippingTrackingUrl,
        @JsonProperty("tracking_url")
        String trackingUrl,
        @JsonProperty("cancelled_at")
        String cancelledAt,
        @JsonProperty("cancel_reason")
        String cancelReason,
        @JsonProperty("checkout_url")
        String checkoutUrl,
        @JsonProperty("abandoned_checkout_url")
        String abandonedCheckoutUrl,
        @JsonProperty("created_at")
        String createdAt,
        @JsonProperty("updated_at")
        String updatedAt,
        @JsonAlias({"contact_name", "customer_name", "name"})
        String contactName,
        @JsonAlias({"contact_email", "customer_email", "email"})
        String contactEmail,
        @JsonAlias({"contact_phone", "customer_phone", "phone"})
        String contactPhone,
        @JsonAlias({"contact_identification", "customer_identification", "identification"})
        String contactIdentification,
        BigDecimal total,
        JsonNode customer,
        @JsonProperty("shipping_address")
        JsonNode shippingAddress,
        List<NuvemshopOrderProductResponse> products
) {
}
