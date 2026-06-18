package br.com.llfashion.whatsappcheckout.repository;

import br.com.llfashion.whatsappcheckout.entity.ProductMapping;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductMappingRepository extends JpaRepository<ProductMapping, UUID>, JpaSpecificationExecutor<ProductMapping> {

    Optional<ProductMapping> findByNuvemshopVariantId(Long nuvemshopVariantId);

    Optional<ProductMapping> findByNuvemshopVariantIdAndActiveTrue(Long nuvemshopVariantId);

    Optional<ProductMapping> findByMetaProductRetailerIdAndActiveTrue(String metaProductRetailerId);

    List<ProductMapping> findByNuvemshopProductIdAndActiveTrueOrderByVariantNameAsc(Long nuvemshopProductId);

    List<ProductMapping> findByActiveTrueOrderByProductNameAscVariantNameAsc();

    List<ProductMapping> findByActiveTrueAndStockGreaterThanOrderByProductNameAscVariantNameAsc(Integer stock);
}
