package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.client.WhatsAppCloudApiClient;
import br.com.llfashion.whatsappcheckout.config.WhatsAppProperties;
import br.com.llfashion.whatsappcheckout.dto.request.WhatsAppButtonMessageRequest;
import br.com.llfashion.whatsappcheckout.dto.response.CreateDraftOrderResponse;
import br.com.llfashion.whatsappcheckout.dto.response.OrderStatusListResponse;
import br.com.llfashion.whatsappcheckout.dto.response.OrderStatusResponse;
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
    private final StorefrontCartService storefrontCartService;
    private final OrderTrackingService orderTrackingService;

    public WhatsAppPaymentMessageService(
            WhatsAppProperties properties,
            WhatsAppCloudApiClient whatsAppCloudApiClient,
            StorefrontCartService storefrontCartService,
            OrderTrackingService orderTrackingService
    ) {
        this.properties = properties;
        this.whatsAppCloudApiClient = whatsAppCloudApiClient;
        this.storefrontCartService = storefrontCartService;
        this.orderTrackingService = orderTrackingService;
    }

    public boolean sendPaymentLink(String to, String customerName, CreateDraftOrderResponse order, String webhookPhoneNumberId) {
        if (order == null || !StringUtils.hasText(order.checkoutUrl())) {
            log.warn("Mensagem de pagamento WhatsApp nao enviada: checkoutUrl vazio.");
            return false;
        }

        return sendText(to, buildPaymentMessage(customerName, order), webhookPhoneNumberId);
    }

    public boolean sendInitialMenu(String to, String customerName, String webhookPhoneNumberId) {
        String phoneNumberId = resolvePhoneNumberIdForInteractive(webhookPhoneNumberId, "Menu WhatsApp");
        if (!StringUtils.hasText(phoneNumberId)) {
            return false;
        }

        String storefrontUrl = storefrontUrlForPhone(to);
        if (!StringUtils.hasText(storefrontUrl)) {
            return false;
        }

        String body = buildShoppingCtaBody(customerName);
        try {
            sendShoppingCtaRequest(phoneNumberId, to, body, storefrontUrl);
        } catch (WhatsAppApiException exception) {
            if (isTransientFailure(exception)) {
                log.warn("Falha temporaria ao enviar botao principal do menu. Tentando novamente. status={}, bodyPreview={}",
                        exception.getStatusCode(),
                        exception.getResponseBody());
                sleepBeforeRetry();
                try {
                    sendShoppingCtaRequest(phoneNumberId, to, body, storefrontUrl);
                } catch (WhatsAppApiException retryException) {
                    exception = retryException;
                }
            }
            log.warn("Falha ao enviar botao principal do menu pelo WhatsApp. status={}, contentType={}, bodyPreview={}",
                    exception.getStatusCode(),
                    exception.getContentType(),
                    exception.getResponseBody());
            return false;
        }

        try {
            sendSupportOptionsRequest(phoneNumberId, to);
        } catch (WhatsAppApiException exception) {
            log.warn("Botao de compra enviado, mas opcoes auxiliares falharam. status={}, contentType={}, bodyPreview={}",
                    exception.getStatusCode(),
                    exception.getContentType(),
                    exception.getResponseBody());
        }
        return true;
    }

    public boolean sendShoppingCta(String to, String customerName, String webhookPhoneNumberId) {
        String storefrontUrl = storefrontUrlForPhone(to);
        return sendShoppingCta(to, customerName, storefrontUrl, webhookPhoneNumberId);
    }

    public boolean sendShoppingCta(String to, String customerName, String storefrontUrl, String webhookPhoneNumberId) {
        if (!StringUtils.hasText(storefrontUrl)) {
            log.warn("Botao de compra WhatsApp nao enviado: storefrontUrl vazio.");
            return false;
        }

        String phoneNumberId = resolvePhoneNumberIdForInteractive(webhookPhoneNumberId, "Botao de compra WhatsApp");
        if (!StringUtils.hasText(phoneNumberId)) {
            return false;
        }

        String body = buildShoppingCtaBody(customerName);
        try {
            sendShoppingCtaRequest(phoneNumberId, to, body, storefrontUrl);
            return true;
        } catch (WhatsAppApiException exception) {
            if (isTransientFailure(exception)) {
                log.warn("Falha temporaria ao enviar botao de compra. Tentando novamente. status={}, bodyPreview={}",
                        exception.getStatusCode(),
                        exception.getResponseBody());
                sleepBeforeRetry();
                try {
                    sendShoppingCtaRequest(phoneNumberId, to, body, storefrontUrl);
                    return true;
                } catch (WhatsAppApiException retryException) {
                    exception = retryException;
                }
            }
            log.warn("Falha ao enviar botao de compra pelo WhatsApp. status={}, contentType={}, bodyPreview={}",
                    exception.getStatusCode(),
                    exception.getContentType(),
                    exception.getResponseBody());
            return false;
        }
    }

    public boolean sendOrderTrackingCta(String to, OrderStatusListResponse result, String webhookPhoneNumberId) {
        if (result == null || !result.found()) {
            String message = result == null
                    ? "N\u00E3o encontrei pedidos para este WhatsApp."
                    : result.message();
            return sendText(to, message + "\n\nSe o pedido foi feito por outro telefone, fale com uma atendente para localizar.", webhookPhoneNumberId);
        }

        String url = trackingUrl(result, to);
        String fallbackMessage = orderTrackingService.buildWhatsAppStatusMessage(result, to);
        if (!StringUtils.hasText(url)) {
            return sendText(to, fallbackMessage, webhookPhoneNumberId);
        }

        String phoneNumberId = resolvePhoneNumberIdForInteractive(webhookPhoneNumberId, "Botao de acompanhamento WhatsApp");
        if (!StringUtils.hasText(phoneNumberId)) {
            return sendText(to, fallbackMessage, webhookPhoneNumberId);
        }

        String body = trackingBody(result);
        String buttonText = result.multiple()
                ? "Ver meus pedidos"
                : "Acompanhar pedido";
        try {
            whatsAppCloudApiClient.sendCtaUrlMessage(
                    phoneNumberId,
                    properties.accessToken(),
                    onlyDigits(to),
                    body,
                    buttonText,
                    url
            );
            return true;
        } catch (WhatsAppApiException exception) {
            if (isTransientFailure(exception)) {
                log.warn("Falha temporaria ao enviar botao de acompanhamento. Tentando novamente. status={}, bodyPreview={}",
                        exception.getStatusCode(),
                        exception.getResponseBody());
                sleepBeforeRetry();
                try {
                    whatsAppCloudApiClient.sendCtaUrlMessage(
                            phoneNumberId,
                            properties.accessToken(),
                            onlyDigits(to),
                            body,
                            buttonText,
                            url
                    );
                    return true;
                } catch (WhatsAppApiException retryException) {
                    exception = retryException;
                }
            }
            log.warn("Falha ao enviar botao de acompanhamento pelo WhatsApp. status={}, contentType={}, bodyPreview={}",
                    exception.getStatusCode(),
                    exception.getContentType(),
                    exception.getResponseBody());
            return sendText(to, fallbackMessage, webhookPhoneNumberId);
        }
    }

    private void sendShoppingCtaRequest(String phoneNumberId, String to, String body, String storefrontUrl) {
        whatsAppCloudApiClient.sendCtaUrlMessage(
                phoneNumberId,
                properties.accessToken(),
                onlyDigits(to),
                body,
                "Comprar agora",
                storefrontUrl
        );
    }

    private void sendSupportOptionsRequest(String phoneNumberId, String to) {
        whatsAppCloudApiClient.sendButtonMessage(
                phoneNumberId,
                properties.accessToken(),
                onlyDigits(to),
                "Outras opções",
                List.of(
                        new WhatsAppButtonMessageRequest.ButtonOption(MENU_TRACK_ORDER, "📦 Acompanhar Pedido"),
                        new WhatsAppButtonMessageRequest.ButtonOption(MENU_HUMAN_ATTENDANT, "💬 Falar Atendente")
                )
        );
    }

    private String storefrontUrlForPhone(String to) {
        try {
            return storefrontCartService.storefrontUrlForPhone(to);
        } catch (RuntimeException exception) {
            log.warn("Nao foi possivel gerar link do storefront para menu WhatsApp. phone={}, erro={}",
                    maskPhone(to),
                    exception.getMessage());
            return null;
        }
    }

    private String trackingUrl(OrderStatusListResponse result, String to) {
        if (result.multiple()) {
            return orderTrackingService.statusListUrl(to);
        }
        OrderStatusResponse order = result.order();
        if (order == null) {
            return orderTrackingService.statusListUrl(to);
        }
        return orderTrackingService.statusUrl(order);
    }

    private String trackingBody(OrderStatusListResponse result) {
        if (result.multiple()) {
            return result.message()
                    + "\n\nToque no bot\u00E3o abaixo para escolher qual pedido acompanhar.";
        }

        OrderStatusResponse order = result.order();
        String status = order == null ? "Pedido encontrado" : order.statusTitle();
        String payment = order == null ? "" : "\nPagamento: " + order.paymentStatus();
        String shipping = order == null ? "" : "\nEnvio: " + order.shippingStatus();
        return "Encontrei seu pedido.\n\n"
                + "Status: " + status
                + payment
                + shipping
                + "\n\nToque no bot\u00E3o abaixo para acompanhar.";
    }

    public boolean sendText(String to, String message, String webhookPhoneNumberId) {
        if (!StringUtils.hasText(message)) {
            log.warn("Mensagem WhatsApp nao enviada: texto vazio.");
            return false;
        }
        if (!StringUtils.hasText(properties.accessToken())) {
            log.warn("Mensagem WhatsApp nao enviada: WHATSAPP_ACCESS_TOKEN nao configurado.");
            return false;
        }

        String phoneNumberId = resolvePhoneNumberId(webhookPhoneNumberId);
        if (!StringUtils.hasText(phoneNumberId)) {
            log.warn("Mensagem WhatsApp nao enviada: WHATSAPP_PHONE_NUMBER_ID nao configurado e payload sem phone_number_id.");
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

    private String resolvePhoneNumberIdForInteractive(String webhookPhoneNumberId, String label) {
        if (!StringUtils.hasText(properties.accessToken())) {
            log.warn("{} nao enviado: WHATSAPP_ACCESS_TOKEN nao configurado.", label);
            return null;
        }

        String phoneNumberId = resolvePhoneNumberId(webhookPhoneNumberId);
        if (!StringUtils.hasText(phoneNumberId)) {
            log.warn("{} nao enviado: WHATSAPP_PHONE_NUMBER_ID nao configurado e payload sem phone_number_id.", label);
            return null;
        }
        return phoneNumberId;
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

    private String buildShoppingCtaBody(String customerName) {
        String name = StringUtils.hasText(customerName) && !"Cliente WhatsApp".equalsIgnoreCase(customerName.trim())
                ? ", " + customerName.trim()
                : "";
        return "Bem-vinda a L&LFashion" + name + "!\n\n"
                + "Moda feminina no atacado.\n"
                + "Pedido mínimo: R$ 200,00.\n\n"
                + "Monte seu pedido com fotos, estoque atualizado e checkout seguro.";
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

    private String maskPhone(String phone) {
        String digits = onlyDigits(phone);
        if (digits.length() <= 4) {
            return "****";
        }
        return "***" + digits.substring(digits.length() - 4);
    }
}
