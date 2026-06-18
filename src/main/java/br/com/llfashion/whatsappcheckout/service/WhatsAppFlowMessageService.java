package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.client.WhatsAppCloudApiClient;
import br.com.llfashion.whatsappcheckout.config.WhatsAppProperties;
import br.com.llfashion.whatsappcheckout.exception.WhatsAppApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WhatsAppFlowMessageService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppFlowMessageService.class);

    private final WhatsAppProperties properties;
    private final WhatsAppCloudApiClient whatsAppCloudApiClient;

    public WhatsAppFlowMessageService(WhatsAppProperties properties, WhatsAppCloudApiClient whatsAppCloudApiClient) {
        this.properties = properties;
        this.whatsAppCloudApiClient = whatsAppCloudApiClient;
    }

    public boolean canSendFlow() {
        WhatsAppProperties.Flows flows = properties.flows();
        return flows != null
                && flows.resolvedEnabled()
                && StringUtils.hasText(flows.flowId())
                && StringUtils.hasText(properties.accessToken());
    }

    public boolean sendProductPurchaseFlow(
            String to,
            String customerName,
            String productName,
            String flowToken,
            String webhookPhoneNumberId
    ) {
        if (!canSendFlow()) {
            log.warn("WhatsApp Flow nao enviado: WHATSAPP_FLOW_ID ou WHATSAPP_ACCESS_TOKEN nao configurado.");
            return false;
        }

        String phoneNumberId = resolvePhoneNumberId(webhookPhoneNumberId);
        if (!StringUtils.hasText(phoneNumberId)) {
            log.warn("WhatsApp Flow nao enviado: WHATSAPP_PHONE_NUMBER_ID nao configurado e payload sem phone_number_id.");
            return false;
        }

        WhatsAppProperties.Flows flows = properties.flows();
        String name = StringUtils.hasText(customerName) ? customerName.trim() : "Cliente";
        String body = "Ola, " + name + "! Vamos continuar sua compra"
                + (StringUtils.hasText(productName) ? " de " + productName : "")
                + ". Toque no botao para revisar os itens, ajustar quantidades e informar os dados de entrega.";

        try {
            whatsAppCloudApiClient.sendFlowMessage(
                    phoneNumberId,
                    properties.accessToken(),
                    onlyDigits(to),
                    body,
                    flows.flowId(),
                    flowToken,
                    StringUtils.hasText(flows.flowCta()) ? flows.flowCta() : "Comprar agora",
                    StringUtils.hasText(flows.mode()) ? flows.mode() : "published",
                    null
            );
            return true;
        } catch (WhatsAppApiException exception) {
            log.warn("Falha ao enviar WhatsApp Flow. status={}, contentType={}, bodyPreview={}",
                    exception.getStatusCode(),
                    exception.getContentType(),
                    exception.getResponseBody());
            return false;
        }
    }

    public boolean sendShoppingMenuFlow(
            String to,
            String customerName,
            String flowToken,
            String entryPoint,
            String webhookPhoneNumberId
    ) {
        if (!canSendFlow()) {
            log.warn("WhatsApp Flow nao enviado: WHATSAPP_FLOW_ID ou WHATSAPP_ACCESS_TOKEN nao configurado.");
            return false;
        }

        String phoneNumberId = resolvePhoneNumberId(webhookPhoneNumberId);
        if (!StringUtils.hasText(phoneNumberId)) {
            log.warn("WhatsApp Flow nao enviado: WHATSAPP_PHONE_NUMBER_ID nao configurado e payload sem phone_number_id.");
            return false;
        }

        String name = StringUtils.hasText(customerName) ? customerName.trim() : "Cliente";
        String body = "Bem-vinda a LLFashion Moda, " + name + "!\n\n"
                + "Trabalhamos com moda feminina no atacado.\n"
                + "Pedido minimo no atacado: R$ 200,00.\n\n"
                + "Toque no botao para comprar, ver novidades, promocoes ou revisar seu carrinho.";

        try {
            WhatsAppProperties.Flows flows = properties.flows();
            whatsAppCloudApiClient.sendFlowMessage(
                    phoneNumberId,
                    properties.accessToken(),
                    onlyDigits(to),
                    body,
                    flows.flowId(),
                    flowToken,
                    "Abrir menu",
                    StringUtils.hasText(flows.mode()) ? flows.mode() : "published",
                    java.util.Map.of("entry_point", StringUtils.hasText(entryPoint) ? entryPoint : "MENU")
            );
            return true;
        } catch (WhatsAppApiException exception) {
            log.warn("Falha ao enviar WhatsApp Flow. status={}, contentType={}, bodyPreview={}",
                    exception.getStatusCode(),
                    exception.getContentType(),
                    exception.getResponseBody());
            return false;
        }
    }

    private String resolvePhoneNumberId(String webhookPhoneNumberId) {
        if (StringUtils.hasText(properties.phoneNumberId())) {
            return properties.phoneNumberId().trim();
        }
        return StringUtils.hasText(webhookPhoneNumberId) ? webhookPhoneNumberId.trim() : null;
    }

    private String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
