package br.com.llfashion.whatsappcheckout.entity;

import br.com.llfashion.whatsappcheckout.enums.WhatsAppCheckoutSessionStatus;
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
@Table(name = "whatsapp_checkout_session")
public class WhatsAppCheckoutSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "whatsapp_message_id")
    private String whatsappMessageId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_lastname", nullable = false)
    private String customerLastname;

    @Column(name = "customer_document", length = 30)
    private String customerDocument;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_phone", length = 50, nullable = false)
    private String customerPhone;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "address_street", length = 500)
    private String addressStreet;

    @Column(name = "address_number", length = 50)
    private String addressNumber;

    @Column(name = "address_complement")
    private String addressComplement;

    @Column(name = "address_neighborhood")
    private String addressNeighborhood;

    @Column(name = "address_city")
    private String addressCity;

    @Column(name = "address_state", length = 100)
    private String addressState;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 100, nullable = false)
    private WhatsAppCheckoutSessionStatus status;

    @Column(name = "subtotal", precision = 10, scale = 2, nullable = false)
    private BigDecimal subtotal;

    @Column(name = "minimum_order_total", precision = 10, scale = 2, nullable = false)
    private BigDecimal minimumOrderTotal;

    @Column(name = "local_order_id")
    private UUID localOrderId;

    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WhatsAppCheckoutSessionItem> items = new ArrayList<>();

    public void addItem(WhatsAppCheckoutSessionItem item) {
        items.add(item);
        item.setSession(this);
    }

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
