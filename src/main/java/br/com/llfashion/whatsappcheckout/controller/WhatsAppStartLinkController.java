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
        boolean sent = whatsAppPaymentMessageService.sendText(
                request.phone(),
                buildMessage(request.customerName(), storefrontUrl),
                null
        );
        return new WhatsAppStartLinkResponse(
                request.phone(),
                storefrontUrl,
                sent,
                sent ? "Link de compra enviado pelo WhatsApp." : "Link gerado, mas a mensagem nao foi enviada pelo WhatsApp."
        );
    }

    private String buildMessage(String customerName, String storefrontUrl) {
        String name = customerName == null || customerName.isBlank() ? "" : ", " + customerName.trim();
        return "Bem-vinda a LLFashion Moda" + name + "!\n\n"
                + "Trabalhamos com moda feminina no atacado.\n"
                + "Pedido minimo no atacado: R$ 200,00.\n\n"
                + "Para montar seu pedido com fotos, tamanhos e estoque atualizado, acesse:\n"
                + storefrontUrl + "\n\n"
                + "Se preferir, responda atendente para falar com uma pessoa.";
    }
}
