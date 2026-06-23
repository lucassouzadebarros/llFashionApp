package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.entity.ProductMapping;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WhatsAppProductSearchService {

    public static final String CATEGORY_ALL = "ALL";
    public static final String CATEGORY_NOVELTIES = "NOVIDADES";
    public static final String CATEGORY_PROMOTIONS = "PROMOCOES";

    private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");
    private static final int MAX_PRODUCTS = 30;

    private final ProductMappingService productMappingService;

    public WhatsAppProductSearchService(ProductMappingService productMappingService) {
        this.productMappingService = productMappingService;
    }

    public List<Map<String, Object>> mainMenuOptions() {
        return List.of(
                option("BUY_CATEGORY", "Comprar por categoria", "Montar carrinho no atacado"),
                option("VIEW_NEW", "Ver novidades", "Peças disponíveis em estoque"),
                option("VIEW_PROMOS", "Ver promoções", "Ofertas e oportunidades"),
                option("VIEW_CART", "Ver carrinho", "Revisar itens e pedido mínimo"),
                option("HUMAN", "Falar com atendente", "Chamar atendimento humano")
        );
    }

    public List<Map<String, Object>> categoryOptions() {
        return List.of(
                option(CATEGORY_ALL, "Todos os produtos", "Ver peças disponíveis"),
                option("CROPPEDS", "Croppeds", "Tops e croppeds"),
                option("SAIAS", "Saias", "Saias femininas"),
                option("SHORTS", "Shorts", "Shorts femininos"),
                option("VESTIDOS", "Vestidos", "Vestidos"),
                option("BLUSAS", "Blusas", "Blusas femininas"),
                option("CONJUNTOS", "Conjuntos", "Conjuntos"),
                option("FITNESS", "Moda fitness", "Peças fitness"),
                option(CATEGORY_PROMOTIONS, "Promoções", "Produtos promocionais"),
                option(CATEGORY_NOVELTIES, "Novidades", "Produtos recentes")
        );
    }

    public List<Map<String, Object>> productOptions(String categoryId) {
        Map<Long, ProductGroup> groups = new LinkedHashMap<>();

        for (ProductMapping mapping : productMappingService.findAvailableActiveMappings()) {
            if (!matchesCategory(mapping, categoryId)) {
                continue;
            }

            groups.computeIfAbsent(
                    mapping.getNuvemshopProductId(),
                    ignored -> new ProductGroup(mapping.getNuvemshopProductId(), mapping.getProductName())
            ).add(mapping);
        }

        return groups.values()
                .stream()
                .sorted(Comparator.comparing(ProductGroup::name, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_PRODUCTS)
                .map(ProductGroup::toOption)
                .toList();
    }

    public String titleForCategory(String categoryId) {
        return switch (normalize(categoryId)) {
            case CATEGORY_NOVELTIES -> "Novidades";
            case CATEGORY_PROMOTIONS -> "Promoções";
            case "CROPPEDS" -> "Croppeds";
            case "SAIAS" -> "Saias";
            case "SHORTS" -> "Shorts";
            case "VESTIDOS" -> "Vestidos";
            case "BLUSAS" -> "Blusas";
            case "CONJUNTOS" -> "Conjuntos";
            case "FITNESS" -> "Moda fitness";
            default -> "Produtos disponíveis";
        };
    }

    private boolean matchesCategory(ProductMapping mapping, String categoryId) {
        String category = normalize(categoryId);
        if (!StringUtils.hasText(category) || CATEGORY_ALL.equals(category)) {
            return true;
        }

        String text = normalize(mapping.getProductName() + " " + mapping.getVariantName() + " " + mapping.getSku());
        return switch (category) {
            case CATEGORY_NOVELTIES -> containsAny(text, "NOVIDADE", "NEW", "LANCAMENTO", "LANCAMENTOS");
            case CATEGORY_PROMOTIONS -> containsAny(text, "PROMO", "PROMOCAO", "SALE", "OFF", "LIQUIDA");
            case "CROPPEDS" -> containsAny(text, "CROPPED", "TOP");
            case "SAIAS" -> containsAny(text, "SAIA");
            case "SHORTS" -> containsAny(text, "SHORT", "SHORTS");
            case "VESTIDOS" -> containsAny(text, "VESTIDO");
            case "BLUSAS" -> containsAny(text, "BLUSA", "CAMISA", "BODY");
            case "CONJUNTOS" -> containsAny(text, "CONJUNTO", "KIT");
            case "FITNESS" -> containsAny(text, "FITNESS", "SUPLEX", "ACADEMIA", "LEGGING");
            default -> true;
        };
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> option(String id, String title, String description) {
        return Map.of(
                "id", id,
                "title", title,
                "description", description
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(BRAZIL).format(value == null ? BigDecimal.ZERO : value);
    }

    private record ProductGroup(Long id, String name, java.util.List<ProductMapping> variants) {

        private ProductGroup(Long id, String name) {
            this(id, name, new java.util.ArrayList<>());
        }

        private void add(ProductMapping mapping) {
            variants.add(mapping);
        }

        private Map<String, Object> toOption() {
            BigDecimal minPrice = variants.stream()
                    .map(ProductMapping::getPrice)
                    .filter(java.util.Objects::nonNull)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            int stock = variants.stream()
                    .map(ProductMapping::getStock)
                    .filter(java.util.Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();

            return Map.of(
                    "id", String.valueOf(id),
                    "title", name,
                    "description", "A partir de " + money(minPrice) + " | Estoque total: " + stock
            );
        }
    }
}
