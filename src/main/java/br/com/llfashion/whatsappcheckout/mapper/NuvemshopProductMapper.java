package br.com.llfashion.whatsappcheckout.mapper;

import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopProductResponse;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopVariantResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class NuvemshopProductMapper {

    public String resolveProductName(NuvemshopProductResponse product) {
        String name = resolveLocalizedText(product.name());
        if (StringUtils.hasText(name)) {
            return name;
        }
        return "Produto " + product.id();
    }

    public String resolveVariantName(NuvemshopVariantResponse variant) {
        JsonNode values = variant.values();
        if (values == null || values.isNull()) {
            return null;
        }

        if (values.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode value : values) {
                String text = resolveLocalizedText(value);
                if (StringUtils.hasText(text)) {
                    parts.add(text);
                }
            }
            return parts.isEmpty() ? null : String.join(" / ", parts);
        }

        return resolveLocalizedText(values);
    }

    public String resolveProductImageUrl(NuvemshopProductResponse product) {
        if (product == null || product.images() == null || product.images().isEmpty()) {
            return null;
        }

        return product.images()
                .stream()
                .filter(image -> image != null && StringUtils.hasText(image.src()))
                .sorted(Comparator.comparing(image -> image.position() == null ? Integer.MAX_VALUE : image.position()))
                .map(image -> image.src().trim())
                .findFirst()
                .orElse(null);
    }

    public String resolveVariantImageUrl(NuvemshopProductResponse product, NuvemshopVariantResponse variant) {
        if (product == null || product.images() == null || product.images().isEmpty()) {
            return null;
        }
        if (variant != null && variant.imageId() != null) {
            String variantImage = product.images()
                    .stream()
                    .filter(image -> image != null && variant.imageId().equals(image.id()) && StringUtils.hasText(image.src()))
                    .map(image -> image.src().trim())
                    .findFirst()
                    .orElse(null);
            if (StringUtils.hasText(variantImage)) {
                return variantImage;
            }
        }
        return resolveProductImageUrl(product);
    }

    private String resolveLocalizedText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return blankToNull(node.asText());
        }
        if (node.isObject()) {
            String portuguese = resolveLocalizedText(node.get("pt"));
            if (StringUtils.hasText(portuguese)) {
                return portuguese;
            }

            for (String preferredField : List.of("value", "label", "text", "name")) {
                String text = resolveLocalizedText(node.get(preferredField));
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }

            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String text = resolveLocalizedText(field.getValue());
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
