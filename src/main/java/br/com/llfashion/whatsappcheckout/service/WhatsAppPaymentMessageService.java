package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.client.WhatsAppCloudApiClient;
import br.com.llfashion.whatsappcheckout.config.WhatsAppProperties;
import br.com.llfashion.whatsappcheckout.dto.request.WhatsAppButtonMessageRequest;
import br.com.llfashion.whatsappcheckout.dto.response.CreateDraftOrderResponse;
import br.com.llfashion.whatsappcheckout.exception.WhatsAppApiException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WhatsAppPaymentMessageService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppPaymentMessageService.class);
    private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");
    public static final String MENU_BUY_NOW = "LLF_BUY_NOW";
    public static final String MENU_TRACK_ORDER = "LLF_TRACK_ORDER";
    public static final String MENU_HUMAN_ATTENDANT = "LLF_HUMAN_ATTENDANT";

    private final WhatsAppProperties properties;
    private final WhatsAppCloudApiClient whatsAppCloudApiClient;

    public WhatsAppPaymentMessageService(WhatsAppProperties properties, WhatsAppCloudApiClient whatsAppCloudApiClient) {
        this.properties = properties;
        this.whatsAppCloudApiClient = whatsAppCloudApiClient;
    }

    public boolean sendPaymentLink(String to, String customerName, CreateDraftOrderResponse order, String webhookPhoneNumberId) {
        if (order == null || !StringUtils.hasText(order.checkoutUrl())) {
            log.warn("Mensagem de pagamento WhatsApp não enviada: checkoutUrl vazio.");
            return false;
        }

        return sendText(to, buildPaymentMessage(customerName, order), webhookPhoneNumberId);
    }

    public boolean sendInitialMenu(String to, String customerName, String webhookPhoneNumberId) {
        if (!StringUtils.hasText(properties.accessToken())) {
            log.warn("Menu WhatsApp não enviado: WHATSAPP_ACCESS_TOKEN não configurado.");
            return false;
        }

        String phoneNumberId = resolvePhoneNumberId(webhookPhoneNumberId);
        if (!StringUtils.hasText(phoneNumberId)) {
            log.warn("Menu WhatsApp não enviado: WHATSAPP_PHONE_NUMBER_ID não configurado e payload sem phone_number_id.");
            return false;
        }

        String name = StringUtils.hasText(customerName) && !"Cliente WhatsApp".equalsIgnoreCase(customerName.trim())
                ? ", " + customerName.trim()
                : "";
        String body = "Bem-vinda a L&LFashion" + name + "!\n\n"
                + "Trabalhamos com moda feminina no atacado.\n\n"
                + "Pedido mínimo: R$ 200,00.\n\n"
                + "Como deseja continuar?";

        try {
            sendInitialMenuRequest(phoneNumberId, to, body);
            return true;
        } catch (WhatsAppApiException exception) {
            if (isTransientFailure(exception)) {
            log.warn("Falha temporária ao enviar menu interativo. Tentando novamente. status={}, bodyPreview={}",
                        exception.getStatusCode(),
                        exception.getResponseBody());
                sleepBeforeRetry();
                try {
                    sendInitialMenuRequest(phoneNumberId, to, body);
                    return true;
                } catch (WhatsAppApiException retryException) {
                    exception = retryException;
                }
            }
            log.warn("Falha ao enviar menu interativo pelo WhatsApp. status={}, contentType={}, bodyPreview={}",
                    exception.getStatusCode(),
                    exception.getContentType(),
                    exception.getResponseBody());
            return false;
        }
    }

    private void sendInitialMenuRequest(String phoneNumberId, String to, String body) {
        whatsAppCloudApiClient.sendButtonMessage(
                phoneNumberId,
                properties.accessToken(),
                onlyDigits(to),
                body,
                List.of(
                        new WhatsAppButtonMessageRequest.ButtonOption(MENU_BUY_NOW, "Comprar Agora"),
                        new WhatsAppButtonMessageRequest.ButtonOption(MENU_TRACK_ORDER, "Acompanhar Pedido"),
                        new WhatsAppButtonMessageRequest.ButtonOption(MENU_HUMAN_ATTENDANT, "Falar com Atendente")
                )
        );
    }

    public boolean sendText(String to, String message, String webhookPhoneNumberId) {
        if (!StringUtils.hasText(message)) {
            log.warn("Mensagem WhatsApp não enviada: texto vazio.");
            return false;
        }
        if (!StringUtils.hasText(properties.accessToken())) {
            log.warn("Mensagem WhatsApp não enviada: WHATSAPP_ACCESS_TOKEN não configurado.");
            return false;
        }

        String phoneNumberId = resolvePhoneNumberId(webhookPhoneNumberId);
        if (!StringUtils.hasText(phoneNumberId)) {
            log.warn("Mensagem WhatsApp não enviada: WHATSAPP_PHONE_NUMBER_ID não configurado e payload sem phone_number_id.");
            return false;
        }

        try {
            whatsAppCloudApiClient.sendTextMessage(
                    phoneNumberId,
                    properties.accessToken(),
                    onlyDigits(to),
                    message
            );
            return true;
        } catch (WhatsAppApiException exception) {
            log.warn("Falha ao enviar mensagem pelo WhatsApp. status={}, contentType={}, bodyPreview={}",
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

    private boolean isTransientFailure(WhatsAppApiException exception) {
        int statusCode = exception.getStatusCode();
        return statusCode == 503 || statusCode == 504 || statusCode == 429;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(700);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildPaymentMessage(String customerName, CreateDraftOrderResponse order) {
        String name = StringUtils.hasText(customerName) ? customerName.trim() : "Cliente";
        String total = orderTotal(order);

        return "Olá, " + name + "! Seu pedido foi criado com sucesso.\n\n"
                + "Total: " + total + "\n"
                + "Link para pagamento:\n"
                + order.checkoutUrl() + "\n\n"
                + "Assim que o pagamento for finalizado, acompanharemos por aqui.";
    }

    private String orderTotal(CreateDraftOrderResponse order) {
        BigDecimal total = order.total();
        if (total == null) {
            return "consulte no checkout";
        }
        return NumberFormat.getCurrencyInstance(BRAZIL).format(total);
    }

    private String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
