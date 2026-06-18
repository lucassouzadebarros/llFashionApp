package br.com.llfashion.whatsappcheckout.dto.response;

public record ProductSyncResponse(
        int totalProductsRead,
        int totalVariantsSynced,
        String message
) {
}
