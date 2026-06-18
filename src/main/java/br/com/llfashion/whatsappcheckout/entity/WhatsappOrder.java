package br.com.llfashion.whatsappcheckout.entity;

import br.com.llfashion.whatsappcheckout.enums.OrderStatus;
import br.com.llfashion.whatsappcheckout.enums.WebhookSource;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "whatsapp_order")
public class WhatsappOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_lastname", nullable = false)
    private String customerLastname;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "customer_phone", length = 50, nullable = false)
    private String customerPhone;

    @Column(name = "customer_document", length = 30)
    private String customerDocument;

    @Column(name = "shipping_postal_code", length = 20)
    private String shippingPostalCode;

    @Column(name = "shipping_street", length = 500)
    private String shippingStreet;

    @Column(name = "shipping_number", length = 50)
    private String shippingNumber;

    @Column(name = "shipping_complement")
    private String shippingComplement;

    @Column(name = "shipping_neighborhood")
    private String shippingNeighborhood;

    @Column(name = "shipping_city")
    private String shippingCity;

    @Column(name = "shipping_state", length = 100)
    private String shippingState;

    @Column(name = "whatsapp_message_id")
    private String whatsappMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 100, nullable = false)
    private WebhookSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 100, nullable = false)
    private OrderStatus status;

    @Column(name = "nuvemshop_draft_order_id")
    private Long nuvemshopDraftOrderId;

    @Column(name = "nuvemshop_order_number", length = 100)
    private String nuvemshopOrderNumber;

    @Column(name = "status_public_token", nullable = false, unique = true, length = 120)
    private String statusPublicToken;

    @Column(name = "payment_status", length = 100)
    private String paymentStatus;

    @Column(name = "shipping_status", length = 100)
    private String shippingStatus;

    @Column(name = "shipping_tracking_number")
    private String shippingTrackingNumber;

    @Column(name = "shipping_tracking_url", columnDefinition = "TEXT")
    private String shippingTrackingUrl;

    @Column(name = "shipping_method")
    private String shippingMethod;

    @Column(name = "shipping_min_days")
    private Integer shippingMinDays;

    @Column(name = "shipping_max_days")
    private Integer shippingMaxDays;

    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;

    @Column(name = "abandoned_checkout_url", columnDefinition = "TEXT")
    private String abandonedCheckoutUrl;

    @Column(name = "pix_copy_paste", columnDefinition = "TEXT")
    private String pixCopyPaste;

    @Column(name = "pix_qr_code_url", columnDefinition = "TEXT")
    private String pixQrCodeUrl;

    @Column(name = "total", precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "raw_nuvemshop_response", columnDefinition = "TEXT")
    private String rawNuvemshopResponse;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WhatsappOrderItem> items = new ArrayList<>();

    public void addItem(WhatsappOrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
