package br.com.llfashion.whatsappcheckout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "storefront_cart_item")
public class StorefrontCartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private StorefrontCart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_mapping_id")
    private ProductMapping productMapping;

    @Column(name = "nuvemshop_product_id", nullable = false)
    private Long nuvemshopProductId;

    @Column(name = "nuvemshop_variant_id", nullable = false)
    private Long nuvemshopVariantId;

    @Column(name = "product_name", length = 500, nullable = false)
    private String productName;

    @Column(name = "variant_name", length = 500)
    private String variantName;

    @Column(name = "size", length = 100)
    private String size;

    @Column(name = "color", length = 100)
    private String color;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "total_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal totalPrice;

    @Column(name = "stock_at_selection")
    private Integer stockAtSelection;

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
