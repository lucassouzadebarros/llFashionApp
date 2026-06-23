package br.com.llfashion.whatsappcheckout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "product_mapping")
public class ProductMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nuvemshop_product_id", nullable = false)
    private Long nuvemshopProductId;

    @Column(name = "nuvemshop_variant_id", nullable = false)
    private Long nuvemshopVariantId;

    @Column(name = "sku")
    private String sku;

    @Column(name = "meta_product_retailer_id")
    private String metaProductRetailerId;

    @Column(name = "product_name", length = 500, nullable = false)
    private String productName;

    @Column(name = "variant_name", length = 500)
    private String variantName;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "stock")
    private Integer stock;

    @Column(name = "promotional", nullable = false)
    private Boolean promotional;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (active == null) {
            active = true;
        }
        if (promotional == null) {
            promotional = false;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (promotional == null) {
            promotional = false;
        }
    }
}
