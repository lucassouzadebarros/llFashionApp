package br.com.llfashion.whatsappcheckout.client;

import br.com.llfashion.whatsappcheckout.config.WhatsAppProperties;
import br.com.llfashion.whatsappcheckout.dto.request.WhatsAppButtonMessageRequest;
import br.com.llfashion.whatsappcheckout.dto.request.WhatsAppFlowMessageRequest;
import br.com.llfashion.whatsappcheckout.dto.request.WhatsAppTextMessageRequest;
import br.com.llfashion.whatsappcheckout.dto.response.WhatsAppMessageResponse;
import br.com.llfashion.whatsappcheckout.exception.WhatsAppApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

@Component
public class WhatsAppCloudApiClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudApiClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);

    private final WebClient webClient;
    private final WhatsAppProperties properties;
    private final ObjectMapper objectMapper;

    public WhatsAppCloudApiClient(WebClient.Builder webClientBuilder, WhatsAppProperties properties, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl(trimTrailingSlash(properties.apiBaseUrl())).build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public WhatsAppMessageResponse sendTextMessage(String phoneNumberId, String accessToken, String to, String body) {
        return sendMessage(phoneNumberId, accessToken, to, WhatsAppTextMessageRequest.of(to, body), "mensagem WhatsApp");
    }

    public WhatsAppMessageResponse sendButtonMessage(
            String phoneNumberId,
            String accessToken,
            String to,
            String body,
            List<WhatsAppButtonMessageRequest.ButtonOption> buttons
    ) {
        return sendMessage(
                phoneNumberId,
                accessToken,
                to,
                WhatsAppButtonMessageRequest.of(to, body, buttons),
                "menu interativo WhatsApp"
        );
    }

    public WhatsAppMessageResponse sendFlowMessage(
            String phoneNumberId,
            String accessToken,
            String to,
            String body,
            String flowId,
            String flowToken,
            String cta,
            String mode,
            java.util.Map<String, Object> initialData
    ) {
        return sendMessage(
                phoneNumberId,
                accessToken,
                to,
                WhatsAppFlowMessageRequest.of(to, body, flowId, flowToken, cta, mode, initialData),
                "WhatsApp Flow"
        );
    }

    private WhatsAppMessageResponse sendMessage(String phoneNumberId, String accessToken, String to, Object request, String label) {
        String path = "/" + phoneNumberId + "/messages";
        String finalUrl = trimTrailingSlash(properties.apiBaseUrl()) + path;
        log.info("Enviando {}. url={}, to={}, authorization={}", label, finalUrl, maskPhone(to), maskedBearer(accessToken));

        try {
            ResponseEntity<String> entity = webClient.post()
                    .uri(path)
                    .headers(headers -> addHeaders(headers, accessToken))
                    .bodyValue(request)
                    .exchangeToMono(response -> response.toEntity(String.class))
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            return handleResponse(finalUrl, entity);
        } catch (WebClientRequestException exception) {
            throw new WhatsAppApiException(
                    503,
                    "Falha de conexão ao enviar mensagem pelo WhatsApp.",
                    null,
                    preview(exception.getMessage()),
                    exception
            );
        } catch (RuntimeException exception) {
            if (isTimeout(exception)) {
                throw new WhatsAppApiException(
                        504,
                        "Timeout ao enviar mensagem pelo WhatsApp.",
                        null,
                        "A Meta não respondeu em até " + REQUEST_TIMEOUT.toSeconds() + " segundos.",
                        exception
                );
            }
            throw exception;
        }
    }

    private WhatsAppMessageResponse handleResponse(String finalUrl, ResponseEntity<String> entity) {
        if (entity == null) {
            throw new WhatsAppApiException(502, "Resposta vazia da API do WhatsApp.", null, "");
        }

        HttpStatusCode statusCode = entity.getStatusCode();
        MediaType contentType = entity.getHeaders().getContentType();
        String body = entity.getBody();
        String bodyPreview = preview(body);
        String contentTypeValue = contentType == null ? null : contentType.toString();

        log.info("WhatsApp message request url: {}", finalUrl);
        log.info("WhatsApp message response status: {}", statusCode);
        log.info("WhatsApp message response content-type: {}", contentType);
        log.info("WhatsApp message response body preview: {}", bodyPreview);

        if (statusCode.isError()) {
            throw new WhatsAppApiException(
                    statusCode.value(),
                    "Erro ao enviar mensagem pelo WhatsApp.",
                    contentTypeValue,
                    bodyPreview
            );
        }

        if (!hasText(body)) {
            return null;
        }

        if (contentType == null || !contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            throw new WhatsAppApiException(
                    statusCode.value(),
                    "WhatsApp retornou resposta não JSON ao enviar mensagem. Content-Type: " + contentTypeValue,
                    contentTypeValue,
                    bodyPreview
            );
        }

        try {
            return objectMapper.readValue(body, WhatsAppMessageResponse.class);
        } catch (Exception exception) {
            throw new WhatsAppApiException(
                    statusCode.value(),
                    "Erro ao converter resposta de envio do WhatsApp.",
                    contentTypeValue,
                    bodyPreview,
                    exception
            );
        }
    }

    private void addHeaders(HttpHeaders headers, String accessToken) {
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
    }

    private String trimTrailingSlash(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String preview(String body) {
        if (body == null) {
            return "";
        }
        return body.substring(0, Math.min(body.length(), 1000));
    }

    private boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String maskedBearer(String accessToken) {
        if (!hasText(accessToken)) {
            return "Bearer ***";
        }
        String token = accessToken.trim();
        return "Bearer " + token.substring(0, Math.min(token.length(), 6)) + "...";
    }

    private String maskPhone(String phone) {
        if (!hasText(phone)) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "***" + digits.substring(digits.length() - 4);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
