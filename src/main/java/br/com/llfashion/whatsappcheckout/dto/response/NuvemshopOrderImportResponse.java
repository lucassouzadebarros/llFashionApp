package br.com.llfashion.whatsappcheckout.dto.response;

public record NuvemshopOrderImportResponse(
        int totalOrdersRead,
        int totalOrdersImportedOrUpdated,
        int totalOrdersSkipped,
        String message
) {
}
