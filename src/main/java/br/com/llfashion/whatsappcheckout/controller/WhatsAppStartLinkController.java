package br.com.llfashion.whatsappcheckout.controller;

import br.com.llfashion.whatsappcheckout.dto.request.WhatsAppStartLinkRequest;
import br.com.llfashion.whatsappcheckout.dto.response.WhatsAppStartLinkResponse;
import br.com.llfashion.whatsappcheckout.service.StorefrontCartService;
import br.com.llfashion.whatsappcheckout.service.WhatsAppPaymentMessageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppStartLinkController {

    private final StorefrontCartService storefrontCartService;
    private final WhatsAppPaymentMessageService whatsAppPaymentMessageService;

    public WhatsAppStartLinkController(StorefrontCartService storefrontCartService, WhatsAppPaymentMessageService whatsAppPaymentMessageService) {
        this.storefrontCartService = storefrontCartService;
        this.whatsAppPaymentMessageService = whatsAppPaymentMessageService;
    }

    @PostMapping("/send-start-link")
    public WhatsAppStartLinkResponse sendStartLink(@Valid @RequestBody WhatsAppStartLinkRequest request) {
        String storefrontUrl = storefrontCartService.storefrontUrlForPhone(request.phone());
        boolean sent = whatsAppPaymentMessageService.sendShoppingCta(
                request.phone(),
                request.customerName(),
                storefrontUrl,
                null
        );
        return new WhatsAppStartLinkResponse(
                request.phone(),
                storefrontUrl,
                sent,
                sent ? "Botao de compra enviado pelo WhatsApp." : "Link gerado, mas a mensagem nao foi enviada pelo WhatsApp."
        );
    }
}
