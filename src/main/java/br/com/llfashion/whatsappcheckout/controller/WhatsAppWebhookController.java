package br.com.llfashion.whatsappcheckout.controller;

import br.com.llfashion.whatsappcheckout.dto.response.WhatsAppWebhookResponse;
import br.com.llfashion.whatsappcheckout.service.WhatsAppWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/whatsapp")
public class WhatsAppWebhookController {

    private final WhatsAppWebhookService whatsAppWebhookService;

    public WhatsAppWebhookController(WhatsAppWebhookService whatsAppWebhookService) {
        this.whatsAppWebhookService = whatsAppWebhookService;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge
    ) {
        return whatsAppWebhookService.verifyWebhook(mode, verifyToken, challenge);
    }

    @PostMapping
    public WhatsAppWebhookResponse receiveWebhook(@RequestBody JsonNode payload) {
        return whatsAppWebhookService.receiveWebhook(payload);
    }
}
