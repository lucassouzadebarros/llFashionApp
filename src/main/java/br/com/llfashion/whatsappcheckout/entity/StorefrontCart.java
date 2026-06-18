package br.com.llfashion.whatsappcheckout.entity;

import br.com.llfashion.whatsappcheckout.enums.StorefrontCartStatus;
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
import jakarta.persistence.Version;
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
@Table(name = "storefront_cart")
public class StorefrontCart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cart_token", nullable = false, unique = true, length = 80)
    private String cartToken;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_lastname")
    private String customerLastname;

    @Column(name = "customer_document", length = 30)
    private String customerDocument;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_phone", length = 50)
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
    private StorefrontCartStatus status;

    @Column(name = "subtotal", precision = 10, scale = 2, nullable = false)
    private BigDecimal subtotal;

    @Column(name = "shipping_price", precision = 10, scale = 2)
    private BigDecimal shippingPrice;

    @Column(name = "total", precision = 10, scale = 2, nullable = false)
    private BigDecimal total;

    @Column(name = "minimum_order_value", precision = 10, scale = 2, nullable = false)
    private BigDecimal minimumOrderValue;

    @Column(name = "selected_shipping_code")
    private String selectedShippingCode;

    @Column(name = "selected_shipping_name")
    private String selectedShippingName;

    @Column(name = "selected_shipping_eta")
    private String selectedShippingEta;

    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;

    @Column(name = "local_order_id")
    private UUID localOrderId;

    @Column(name = "nuvemshop_draft_order_id")
    private Long nuvemshopDraftOrderId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StorefrontCartItem> items = new ArrayList<>();

    public void addItem(StorefrontCartItem item) {
        items.add(item);
        item.setCart(this);
    }

    public void removeItem(StorefrontCartItem item) {
        items.remove(item);
        item.setCart(null);
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
