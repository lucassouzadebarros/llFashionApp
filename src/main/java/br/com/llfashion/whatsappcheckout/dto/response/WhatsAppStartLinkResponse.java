package br.com.llfashion.whatsappcheckout.dto.response;

public record WhatsAppStartLinkResponse(
        String phone,
        String storefrontUrl,
        Boolean sent,
        String message
) {
}
