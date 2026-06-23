package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.dto.response.ProductMappingResponse;
import br.com.llfashion.whatsappcheckout.entity.ProductMapping;
import br.com.llfashion.whatsappcheckout.exception.BusinessException;
import br.com.llfashion.whatsappcheckout.exception.EntityNotFoundException;
import br.com.llfashion.whatsappcheckout.mapper.ProductMappingMapper;
import br.com.llfashion.whatsappcheckout.repository.ProductMappingRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProductMappingService {

    private final ProductMappingRepository repository;
    private final ProductMappingMapper mapper;

    public ProductMappingService(ProductMappingRepository repository, ProductMappingMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public ProductMapping saveOrUpdateFromNuvemshop(
            Long productId,
            Long variantId,
            String sku,
            String productName,
            String variantName,
            String imageUrl,
            BigDecimal price,
            Integer stock,
            Boolean promotional
    ) {
        ProductMapping mapping = repository.findByNuvemshopVariantId(variantId)
                .orElseGet(ProductMapping::new);

        mapping.setNuvemshopProductId(productId);
        mapping.setNuvemshopVariantId(variantId);
        mapping.setSku(trimToNull(sku));
        mapping.setProductName(productName);
        mapping.setVariantName(trimToNull(variantName));
        mapping.setImageUrl(trimToNull(imageUrl));
        mapping.setPrice(price);
        mapping.setStock(stock);
        mapping.setPromotional(Boolean.TRUE.equals(promotional));
        mapping.setActive(true);

        return repository.save(mapping);
    }

    @Transactional(readOnly = true)
    public List<ProductMappingResponse> listMappings(Boolean active, String productName, String sku, String metaProductRetailerId) {
        Specification<ProductMapping> specification = Specification.where(null);

        if (active != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        if (StringUtils.hasText(productName)) {
            String value = likeValue(productName);
            specification = specification.and((root, query, cb) -> cb.like(cb.lower(root.get("productName")), value));
        }
        if (StringUtils.hasText(sku)) {
            String value = likeValue(sku);
            specification = specification.and((root, query, cb) -> cb.like(cb.lower(root.get("sku")), value));
        }
        if (StringUtils.hasText(metaProductRetailerId)) {
            String value = likeValue(metaProductRetailerId);
            specification = specification.and((root, query, cb) -> cb.like(cb.lower(root.get("metaProductRetailerId")), value));
        }

        return repository.findAll(specification, Sort.by("productName").ascending().and(Sort.by("variantName").ascending()))
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public ProductMappingResponse updateMetaRetailerId(UUID id, String metaProductRetailerId) {
        ProductMapping mapping = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Mapeamento de produto não encontrado: " + id));

        mapping.setMetaProductRetailerId(trimToNull(metaProductRetailerId));
        return mapper.toResponse(repository.save(mapping));
    }

    @Transactional(readOnly = true)
    public ProductMapping findActiveByNuvemshopVariantId(Long nuvemshopVariantId) {
        return repository.findByNuvemshopVariantIdAndActiveTrue(nuvemshopVariantId)
                .orElseThrow(() -> new BusinessException("Produto não encontrado para o nuvemshopVariantId: " + nuvemshopVariantId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductMapping updateStockAndPrice(Long nuvemshopVariantId, Integer stock, BigDecimal price, Boolean promotional) {
        ProductMapping mapping = repository.findByNuvemshopVariantIdAndActiveTrue(nuvemshopVariantId)
                .orElseThrow(() -> new BusinessException("Produto não encontrado para o nuvemshopVariantId: " + nuvemshopVariantId));
        mapping.setStock(stock == null ? 0 : stock);
        if (price != null) {
            mapping.setPrice(price);
        }
        mapping.setPromotional(Boolean.TRUE.equals(promotional));
        return repository.save(mapping);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductMapping updateStockPriceAndImage(Long nuvemshopVariantId, Integer stock, BigDecimal price, Boolean promotional, String imageUrl) {
        ProductMapping mapping = updateStockAndPrice(nuvemshopVariantId, stock, price, promotional);
        if (StringUtils.hasText(imageUrl)) {
            mapping.setImageUrl(imageUrl.trim());
        }
        return repository.save(mapping);
    }

    @Transactional(readOnly = true)
    public ProductMapping findActiveByMetaProductRetailerId(String metaProductRetailerId) {
        return repository.findByMetaProductRetailerIdAndActiveTrue(metaProductRetailerId)
                .orElseThrow(() -> new BusinessException("Produto não encontrado para o metaProductRetailerId: " + metaProductRetailerId));
    }

    @Transactional(readOnly = true)
    public List<ProductMapping> findActiveVariantsByProductId(Long nuvemshopProductId) {
        return repository.findByNuvemshopProductIdAndActiveTrueOrderByVariantNameAsc(nuvemshopProductId);
    }

    @Transactional(readOnly = true)
    public List<ProductMapping> findAvailableActiveMappings() {
        return repository.findByActiveTrueAndStockGreaterThanOrderByProductNameAscVariantNameAsc(0);
    }

    private String likeValue(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
