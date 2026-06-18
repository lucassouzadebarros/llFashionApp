package br.com.llfashion.whatsappcheckout.dto.response;

public record StorefrontCategoryResponse(
        String id,
        String name,
        String description,
        String imageUrl,
        Integer totalProducts
) {
}
