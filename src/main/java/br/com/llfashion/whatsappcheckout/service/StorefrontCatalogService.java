package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.dto.response.StorefrontCategoryResponse;
import br.com.llfashion.whatsappcheckout.dto.response.StorefrontProductDetailResponse;
import br.com.llfashion.whatsappcheckout.dto.response.StorefrontProductSummaryResponse;
import br.com.llfashion.whatsappcheckout.dto.response.StorefrontVariantResponse;
import br.com.llfashion.whatsappcheckout.entity.ProductMapping;
import br.com.llfashion.whatsappcheckout.exception.EntityNotFoundException;
import br.com.llfashion.whatsappcheckout.repository.ProductMappingRepository;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class StorefrontCatalogService {

    private static final String FALLBACK_IMAGE_URL = "https://placehold.co/900x1200/f3faf6/047857.png?text=L%26LFashion";

    private final ProductMappingRepository productMappingRepository;

    public StorefrontCatalogService(ProductMappingRepository productMappingRepository) {
        this.productMappingRepository = productMappingRepository;
    }

    @Transactional(readOnly = true)
    public List<StorefrontCategoryResponse> listCategories() {
        List<ProductGroup> groups = availableProductGroups();
        return categories().stream()
                .map(category -> new StorefrontCategoryResponse(
                        category.id(),
                        category.name(),
                        category.description(),
                        category.imageUrl(groups),
                        countProducts(category, groups)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StorefrontProductSummaryResponse> listProducts(String categoryId, Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 || size > 60 ? 24 : size;
        Category category = categoryById(categoryId);

        List<StorefrontProductSummaryResponse> products = availableProductGroups().stream()
                .filter(group -> category.matches(group))
                .map(this::toProductSummary)
                .sorted(Comparator.comparing(StorefrontProductSummaryResponse::productName))
                .toList();

        int from = Math.min(safePage * safeSize, products.size());
        int to = Math.min(from + safeSize, products.size());
        return products.subList(from, to);
    }

    @Transactional(readOnly = true)
    public StorefrontProductDetailResponse getProduct(Long productId) {
        List<ProductMapping> variants = productMappingRepository
                .findByNuvemshopProductIdAndActiveTrueOrderByVariantNameAsc(productId)
                .stream()
                .filter(this::isAvailable)
                .toList();

        if (variants.isEmpty()) {
            throw new EntityNotFoundException("Produto não encontrado ou sem estoque: " + productId);
        }

        ProductGroup group = new ProductGroup(productId, variants);
        return new StorefrontProductDetailResponse(
                productId,
                group.name(),
                group.imageUrl(),
                group.startingPrice(),
                group.totalStock(),
                categoryForProduct(group).id(),
                variants.stream().map(this::toVariantResponse).toList()
        );
    }

    private List<ProductGroup> availableProductGroups() {
        Map<Long, List<ProductMapping>> grouped = productMappingRepository
                .findByActiveTrueAndStockGreaterThanOrderByProductNameAscVariantNameAsc(0)
                .stream()
                .filter(this::isAvailable)
                .collect(java.util.stream.Collectors.groupingBy(
                        ProductMapping::getNuvemshopProductId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));

        return grouped.entrySet().stream()
                .map(entry -> new ProductGroup(entry.getKey(), entry.getValue()))
                .filter(group -> group.totalStock() > 0)
                .toList();
    }

    private StorefrontProductSummaryResponse toProductSummary(ProductGroup group) {
        return new StorefrontProductSummaryResponse(
                group.productId(),
                group.name(),
                group.imageUrl(),
                group.startingPrice(),
                group.totalStock(),
                group.variants().size(),
                group.totalStock() > 0
        );
    }

    public StorefrontVariantResponse toVariantResponse(ProductMapping mapping) {
        VariantParts parts = parseVariant(mapping.getVariantName());
        return new StorefrontVariantResponse(
                mapping.getId(),
                mapping.getNuvemshopProductId(),
                mapping.getNuvemshopVariantId(),
                mapping.getSku(),
                mapping.getVariantName(),
                parts.color(),
                parts.size(),
                parts.model(),
                imageUrl(mapping),
                price(mapping),
                stock(mapping),
                stock(mapping) > 0
        );
    }

    private List<Category> categories() {
        return List.of(
                new Category("todos", "Todos os produtos", "Veja todas as peças disponíveis", List.of()),
                new Category("croppeds", "Croppeds", "Tops e croppeds", List.of("cropped", "top")),
                new Category("saias", "Saias", "Saias femininas", List.of("saia")),
                new Category("shorts", "Shorts", "Shorts femininos", List.of("short")),
                new Category("vestidos", "Vestidos", "Vestidos femininos", List.of("vestido")),
                new Category("blusas", "Blusas", "Blusas femininas", List.of("blusa", "camisa", "body")),
                new Category("conjuntos", "Conjuntos", "Conjuntos e looks", List.of("conjunto", "kit")),
                new Category("promocoes", "Promoções", "Peças com oportunidade", List.of("promo", "off", "liquida"))
        );
    }

    private Category categoryById(String categoryId) {
        if (!StringUtils.hasText(categoryId)) {
            return categories().get(0);
        }
        return categories().stream()
                .filter(category -> category.id().equalsIgnoreCase(categoryId.trim()))
                .findFirst()
                .orElse(categories().get(0));
    }

    private Category categoryForProduct(ProductGroup group) {
        return categories().stream()
                .filter(category -> !"todos".equals(category.id()))
                .filter(category -> category.matches(group))
                .findFirst()
                .orElse(categories().get(0));
    }

    private int countProducts(Category category, List<ProductGroup> groups) {
        return (int) groups.stream().filter(category::matches).count();
    }

    private boolean isAvailable(ProductMapping mapping) {
        return Boolean.TRUE.equals(mapping.getActive()) && stock(mapping) > 0;
    }

    private BigDecimal price(ProductMapping mapping) {
        return mapping.getPrice() == null ? BigDecimal.ZERO : mapping.getPrice();
    }

    private int stock(ProductMapping mapping) {
        return mapping.getStock() == null ? 0 : mapping.getStock();
    }

    private String imageUrl(ProductMapping mapping) {
        return StringUtils.hasText(mapping.getImageUrl()) ? mapping.getImageUrl().trim() : FALLBACK_IMAGE_URL;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private VariantParts parseVariant(String variantName) {
        if (!StringUtils.hasText(variantName)) {
            return new VariantParts(null, "Único", null);
        }
        List<String> parts = java.util.Arrays.stream(variantName.split("/"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        String color = parts.isEmpty() ? null : parts.get(0);
        String size = parts.size() >= 2 ? parts.get(1) : "Único";
        String model = parts.size() >= 3 ? parts.get(2) : null;
        return new VariantParts(color, size, model);
    }

    private record VariantParts(String color, String size, String model) {
    }

    private record Category(String id, String name, String description, List<String> keywords) {

        boolean matches(ProductGroup group) {
            if ("todos".equals(id)) {
                return true;
            }
            String searchable = group.searchableName();
            return keywords.stream().anyMatch(searchable::contains);
        }

        String imageUrl(List<ProductGroup> groups) {
            return groups.stream()
                    .filter(this::matches)
                    .map(ProductGroup::imageUrl)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(FALLBACK_IMAGE_URL);
        }
    }

    private final class ProductGroup {

        private final Long productId;
        private final List<ProductMapping> variants;

        private ProductGroup(Long productId, List<ProductMapping> variants) {
            this.productId = productId;
            this.variants = variants;
        }

        Long productId() {
            return productId;
        }

        List<ProductMapping> variants() {
            return variants;
        }

        String name() {
            return variants.stream()
                    .map(ProductMapping::getProductName)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse("Produto L&LFashion");
        }

        String searchableName() {
            return normalize(name());
        }

        String imageUrl() {
            return variants.stream()
                    .map(StorefrontCatalogService.this::imageUrl)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(FALLBACK_IMAGE_URL);
        }

        BigDecimal startingPrice() {
            return variants.stream()
                    .map(StorefrontCatalogService.this::price)
                    .min(Comparator.naturalOrder())
                    .orElse(BigDecimal.ZERO);
        }

        Integer totalStock() {
            return variants.stream()
                    .map(StorefrontCatalogService.this::stock)
                    .reduce(0, Integer::sum);
        }
    }
}
