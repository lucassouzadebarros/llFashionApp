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
@Table(name = "whatsapp_order_item")
public class WhatsappOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private WhatsappOrder order;

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

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
