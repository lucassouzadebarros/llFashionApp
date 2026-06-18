package br.com.llfashion.whatsappcheckout.client;

import br.com.llfashion.whatsappcheckout.config.NuvemshopProperties;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopTokenRequest;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopTokenResponse;
import br.com.llfashion.whatsappcheckout.exception.NuvemshopApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class NuvemshopOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(NuvemshopOAuthClient.class);

    private final WebClient webClient;
    private final NuvemshopProperties properties;
    private final ObjectMapper objectMapper;

    public NuvemshopOAuthClient(
            WebClient.Builder webClientBuilder,
            NuvemshopProperties properties,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClientBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public NuvemshopTokenResponse trocarCodePorToken(NuvemshopTokenRequest request) {
        return webClient.post()
                .uri(properties.tokenUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchangeToMono(response -> response.toEntity(String.class)
                        .map(this::handleTokenResponse))
                .block();
    }

    private NuvemshopTokenResponse handleTokenResponse(ResponseEntity<String> entity) {
        HttpStatusCode statusCode = entity.getStatusCode();
        MediaType contentType = entity.getHeaders().getContentType();
        String body = entity.getBody();
        String bodyPreview = preview(body);
        String contentTypeValue = contentType == null ? null : contentType.toString();

        log.info("Nuvemshop token response status: {}", statusCode);
        log.info("Nuvemshop token response headers: {}", entity.getHeaders());
        log.info("Nuvemshop token response content-type: {}", contentType);
        log.info("Nuvemshop token response body preview: {}", bodyPreview);

        if (statusCode.isError()) {
            throw new NuvemshopApiException(
                    statusCode.value(),
                    "Falha ao trocar code por access_token na Nuvemshop",
                    contentTypeValue,
                    bodyPreview
            );
        }

        boolean jsonContentType = contentType != null && contentType.isCompatibleWith(MediaType.APPLICATION_JSON);
        boolean jsonBody = looksLikeJson(body);

        if (!jsonContentType && !jsonBody) {
            throw new NuvemshopApiException(
                    statusCode.value(),
                    "A Nuvemshop retornou uma resposta nao JSON ao trocar o code pelo access_token. Content-Type: " + contentTypeValue + ". Verifique token-url, client_secret, code expirado/usado e URL de callback.",
                    contentTypeValue,
                    bodyPreview
            );
        }

        if (!jsonContentType) {
            log.warn("Nuvemshop retornou Content-Type {} no token OAuth, mas o body parece JSON. A resposta sera convertida mesmo assim.", contentType);
        }

        try {
            return objectMapper.readValue(body, NuvemshopTokenResponse.class);
        } catch (Exception exception) {
            throw new NuvemshopApiException(
                    statusCode.value(),
                    "Erro ao converter resposta da Nuvemshop para NuvemshopTokenResponse",
                    contentTypeValue,
                    bodyPreview,
                    exception
            );
        }
    }

    private String preview(String body) {
        if (body == null) {
            return "";
        }
        String sanitizedBody = sanitizeSensitiveData(body);
        return sanitizedBody.substring(0, Math.min(sanitizedBody.length(), 500));
    }

    private boolean looksLikeJson(String body) {
        if (body == null) {
            return false;
        }
        String trimmedBody = body.trim();
        return trimmedBody.startsWith("{") || trimmedBody.startsWith("[");
    }

    private String sanitizeSensitiveData(String value) {
        return value
                .replaceAll("(?i)(\"access_token\"\\s*:\\s*\")[^\"]+\"", "$1***\"")
                .replaceAll("(?i)(\"client_secret\"\\s*:\\s*\")[^\"]+\"", "$1***\"");
    }
}
