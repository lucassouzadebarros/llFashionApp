package br.com.llfashion.whatsappcheckout.entity;

import br.com.llfashion.whatsappcheckout.enums.WhatsAppFlowSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
@Table(name = "whatsapp_flow_session")
public class WhatsAppFlowSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flow_token", nullable = false, unique = true)
    private String flowToken;

    @Column(name = "customer_phone", length = 50, nullable = false)
    private String customerPhone;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "whatsapp_message_id")
    private String whatsappMessageId;

    @Column(name = "product_retailer_id")
    private String productRetailerId;

    @Column(name = "nuvemshop_product_id")
    private Long nuvemshopProductId;

    @Column(name = "nuvemshop_variant_id")
    private Long nuvemshopVariantId;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "subtotal", precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 100, nullable = false)
    private WhatsAppFlowSessionStatus status;

    @Column(name = "local_order_id")
    private UUID localOrderId;

    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
