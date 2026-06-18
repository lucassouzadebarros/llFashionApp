package br.com.llfashion.whatsappcheckout.dto.nuvemshop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NuvemshopDraftOrderResponse(
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
        @JsonProperty("pix_copy_paste")
        String pixCopyPaste,
        @JsonProperty("pix_qr_code_url")
        String pixQrCodeUrl,
        BigDecimal total,
        List<NuvemshopDraftOrderProductResponse> products
) {
}
