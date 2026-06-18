package br.com.llfashion.whatsappcheckout.client;

import br.com.llfashion.whatsappcheckout.config.NuvemshopProperties;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopDraftOrderRequest;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopDraftOrderResponse;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopProductResponse;
import br.com.llfashion.whatsappcheckout.exception.NuvemshopApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class NuvemshopApiClient {

    private static final Logger log = LoggerFactory.getLogger(NuvemshopApiClient.class);
    private static final TypeReference<List<NuvemshopProductResponse>> PRODUCT_LIST_TYPE = new TypeReference<>() {
    };

    private final WebClient webClient;
    private final NuvemshopProperties properties;
    private final ObjectMapper objectMapper;

    public NuvemshopApiClient(WebClient.Builder webClientBuilder, NuvemshopProperties properties, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl(properties.apiBaseUrl()).build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<NuvemshopProductResponse> buscarProdutos(Long storeId, String accessToken, Integer page, Integer perPage) {
        String finalUrl = buildProductsUrl(storeId, page, perPage);
        log.info("Buscando produtos na Nuvemshop. url={}, authentication={}", finalUrl, maskedBearer(accessToken));

        try {
            ResponseEntity<String> entity = webClient.get()
                    .uri(finalUrl)
                    .headers(headers -> addDefaultHeaders(headers, accessToken))
                    .exchangeToMono(response -> response.toEntity(String.class))
                    .block();

            return handleProductsResponse(finalUrl, entity);
        } catch (WebClientRequestException exception) {
            throw new NuvemshopApiException(
                    503,
                    "Falha de conexao ao buscar produtos na Nuvemshop.",
                    null,
                    preview(exception.getMessage()),
                    exception
            );
        }
    }

    public NuvemshopProductResponse buscarProduto(Long storeId, String accessToken, Long productId) {
        String finalUrl = buildProductUrl(storeId, productId);
        log.info("Buscando produto na Nuvemshop. url={}, authentication={}", finalUrl, maskedBearer(accessToken));

        try {
            ResponseEntity<String> entity = webClient.get()
                    .uri(finalUrl)
                    .headers(headers -> addDefaultHeaders(headers, accessToken))
                    .exchangeToMono(response -> response.toEntity(String.class))
                    .block();

            return handleProductResponse(finalUrl, entity);
        } catch (WebClientRequestException exception) {
            throw new NuvemshopApiException(
                    503,
                    "Falha de conexao ao buscar produto na Nuvemshop.",
                    null,
                    preview(exception.getMessage()),
                    exception
            );
        }
    }

    public NuvemshopDraftOrderResponse criarDraftOrder(Long storeId, String accessToken, NuvemshopDraftOrderRequest request) {
        String finalUrl = buildDraftOrdersUrl(storeId);
        log.info("Criando Draft Order na Nuvemshop. url={}, authentication={}", finalUrl, maskedBearer(accessToken));

        try {
            ResponseEntity<String> entity = webClient.post()
                    .uri(finalUrl)
                    .headers(headers -> addJsonHeaders(headers, accessToken))
                    .bodyValue(request)
                    .exchangeToMono(response -> response.toEntity(String.class))
                    .block();

            return handleDraftOrderResponse(finalUrl, entity, "criar Draft Order na Nuvemshop");
        } catch (WebClientRequestException exception) {
            throw new NuvemshopApiException(
                    503,
                    "Falha de conexao ao criar Draft Order na Nuvemshop.",
                    null,
                    preview(exception.getMessage()),
                    exception
            );
        }
    }

    public NuvemshopDraftOrderResponse buscarDraftOrder(Long storeId, String accessToken, Long draftOrderId) {
        String finalUrl = buildDraftOrderUrl(storeId, draftOrderId);
        log.info("Buscando Draft Order na Nuvemshop. url={}, authentication={}", finalUrl, maskedBearer(accessToken));

        try {
            ResponseEntity<String> entity = webClient.get()
                    .uri(finalUrl)
                    .headers(headers -> addDefaultHeaders(headers, accessToken))
                    .exchangeToMono(response -> response.toEntity(String.class))
                    .block();

            return handleDraftOrderResponse(finalUrl, entity, "buscar Draft Order na Nuvemshop");
        } catch (WebClientRequestException exception) {
            throw new NuvemshopApiException(
                    503,
                    "Falha de conexao ao buscar Draft Order na Nuvemshop.",
                    null,
                    preview(exception.getMessage()),
                    exception
            );
        }
    }

    public NuvemshopDraftOrderResponse buscarOrder(Long storeId, String accessToken, Long orderId) {
        String finalUrl = buildOrderUrl(storeId, orderId);
        log.info("Buscando venda na Nuvemshop. url={}, authentication={}", finalUrl, maskedBearer(accessToken));

        try {
            ResponseEntity<String> entity = webClient.get()
                    .uri(finalUrl)
                    .headers(headers -> addDefaultHeaders(headers, accessToken))
                    .exchangeToMono(response -> response.toEntity(String.class))
                    .block();

            return handleDraftOrderResponse(finalUrl, entity, "buscar venda na Nuvemshop");
        } catch (WebClientRequestException exception) {
            throw new NuvemshopApiException(
                    503,
                    "Falha de conexao ao buscar venda na Nuvemshop.",
                    null,
                    preview(exception.getMessage()),
                    exception
            );
        }
    }

    private void addJsonHeaders(HttpHeaders headers, String accessToken) {
        addDefaultHeaders(headers, accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
    }

    private void addDefaultHeaders(HttpHeaders headers, String accessToken) {
        headers.set("Authentication", "bearer " + accessToken);
        headers.set(HttpHeaders.USER_AGENT, properties.userAgent());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    }

    private Mono<NuvemshopApiException> toException(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(responseBody -> new NuvemshopApiException(
                        response.statusCode().value(),
                        "Resposta inválida da API externa",
                        responseBody
                ));
    }

    private List<NuvemshopProductResponse> handleProductsResponse(String finalUrl, ResponseEntity<String> entity) {
        if (entity == null) {
            throw new NuvemshopApiException(
                    502,
                    "Resposta vazia ao buscar produtos na Nuvemshop",
                    ""
            );
        }

        HttpStatusCode statusCode = entity.getStatusCode();
        MediaType contentType = entity.getHeaders().getContentType();
        String body = entity.getBody();
        String bodyPreview = preview(body);
        String contentTypeValue = contentType == null ? null : contentType.toString();

        log.info("Nuvemshop products request url: {}", finalUrl);
        log.info("Nuvemshop products response status: {}", statusCode);
        log.info("Nuvemshop products response content-type: {}", contentType);
        log.info("Nuvemshop products response body preview: {}", bodyPreview);

        if (statusCode.isError()) {
            throw new NuvemshopApiException(
                    statusCode.value(),
                    "Erro ao buscar produtos na Nuvemshop",
                    contentTypeValue,
                    bodyPreview
            );
        }

        if (!hasText(body) || "[]".equals(body.trim())) {
            return List.of();
        }

        if (contentType == null || !contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            throw new NuvemshopApiException(
                    statusCode.value(),
                    "A Nuvemshop retornou uma resposta nao JSON ao buscar produtos. Content-Type: " + contentTypeValue,
                    contentTypeValue,
                    bodyPreview
            );
        }

        try {
            return Optional.ofNullable(objectMapper.readValue(body, PRODUCT_LIST_TYPE)).orElse(List.of());
        } catch (Exception exception) {
            throw new NuvemshopApiException(
                    statusCode.value(),
                    "Erro ao converter resposta de produtos da Nuvemshop",
                    contentTypeValue,
                    bodyPreview,
                    exception
            );
        }
    }

    private NuvemshopProductResponse handleProductResponse(String finalUrl, ResponseEntity<String> entity) {
        if (entity == null) {
            throw new NuvemshopApiException(
                    502,
                    "Resposta vazia ao buscar produto na Nuvemshop",
                    ""
            );
        }

        HttpStatusCode statusCode = entity.getStatusCode();
        MediaType contentType = entity.getHeaders().getContentType();
        String body = entity.getBody();
        String bodyPreview = preview(body);
        String contentTypeValue = contentType == null ? null : contentType.toString();

        log.info("Nuvemshop product request url: {}", finalUrl);
        log.info("Nuvemshop product response status: {}", statusCode);
        log.info("Nuvemshop product response content-type: {}", contentType);
        log.info("Nuvemshop product response body preview: {}", bodyPreview);

        if (statusCode.isError()) {
            throw new NuvemshopApiException(
                    statusCode.value(),
                    "Erro ao buscar produto na Nuvemshop",
                    contentTypeValue,
                    bodyPreview
            );
        }

        if (!hasText(body)) {
            throw new NuvemshopApiException(
                    statusCode.value(),
                    "A Nuvemshop retornou body vazio ao buscar produto",
                    contentTypeValue,
                    ""
            );
        }

        if (!isJsonResponse(contentType, body)) {
            throw new NuvemshopApiException(
                    statusCode.value(),
                    "A Nuvemshop retornou uma resposta nao JSON ao buscar produto. Content-Type: " + contentTypeValue,
                    contentTypeValue,
                    bodyPreview
            );
        }

        try {
            return objectMapper.readValue(body, NuvemshopProductResponse.class);
        } catch (Exception exception) {
            throw new NuvemshopApiException(
                    statusCode.value(),
                    "Erro ao converter resposta de produto da Nuvemshop",
                    contentTypeValue,
                    bodyPreview,
                    exception
            );
        }
    }

    private NuvemshopDraftOrderResponse handleDraftOrderResponse(String finalUrl, ResponseEntity<String> entity, String operation) {
        if (entity == null) {
            throw new NuvemshopApiException(
                    502,
                    "Resposta vazia ao " + operation,
                    ""
            );
        }

        HttpStatusCode statusCode = entity.getStatusCode();
        MediaType contentType = entity.getHeaders().getContentType();
        String body = entity.getBody();
        String bodyPreview = preview(body);
        String contentTypeValue = contentType == null ? null : contentType.toString();

        log.info("Nuvemshop draft order request url: {}", finalUrl);
        log.info("Nuvemshop draft order response status: {}", statusCode);
        log.info("Nuvemshop draft order response content-type: {}", contentType);
        log.info("Nuvemshop draft order response body preview: {}", bodyPreview);

        boolean lookupOperation = operation.startsWith("buscar");

        if (statusCode.isError()) {
            throw new NuvemshopApiException(
                    statusCode.value(),
                    "Erro ao " + operation,
                    contentTypeValue,
                    bodyPreview
            );
        }

        if (!hasText(body) || "[]".equals(body.trim()) || "null".equalsIgnoreCase(body.trim())) {
            if (lookupOperation) {
                return null;
            }
            throw new NuvemshopApiException(
                    statusCode.value(),
                    "A Nuvemshop retornou body vazio ao " + operation,
                    contentTypeValue,
                    ""
            );
        }

        if (!isJsonResponse(contentType, body)) {
            throw new NuvemshopApiException(
                    statusCode.value(),
                    "A Nuvemshop retornou uma resposta nao JSON ao " + operation + ". Content-Type: " + contentTypeValue,
                    contentTypeValue,
                    bodyPreview
            );
        }

        try {
            return objectMapper.readValue(body, NuvemshopDraftOrderResponse.class);
        } catch (Exception exception) {
            throw new NuvemshopApiException(
                    statusCode.value(),
                    "Erro ao converter resposta de Draft Order da Nuvemshop",
                    contentTypeValue,
                    bodyPreview,
                    exception
            );
        }
    }

    private String buildProductsUrl(Long storeId, Integer page, Integer perPage) {
        return trimTrailingSlash(properties.apiBaseUrl())
                + "/" + storeId
                + "/products?page=" + page
                + "&per_page=" + perPage;
    }

    private String buildProductUrl(Long storeId, Long productId) {
        return trimTrailingSlash(properties.apiBaseUrl())
                + "/" + storeId
                + "/products/" + productId;
    }

    private String buildDraftOrdersUrl(Long storeId) {
        return trimTrailingSlash(properties.apiBaseUrl())
                + "/" + storeId
                + "/draft_orders";
    }

    private String buildDraftOrderUrl(Long storeId, Long draftOrderId) {
        return buildDraftOrdersUrl(storeId) + "/" + draftOrderId;
    }

    private String buildOrderUrl(Long storeId, Long orderId) {
        return trimTrailingSlash(properties.apiBaseUrl())
                + "/" + storeId
                + "/orders/"
                + orderId;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
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

    private String maskedBearer(String accessToken) {
        if (!hasText(accessToken)) {
            return "bearer ***";
        }
        String token = accessToken.trim();
        return "bearer " + token.substring(0, Math.min(token.length(), 6)) + "...";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isJsonResponse(MediaType contentType, String body) {
        boolean jsonContentType = contentType != null && contentType.isCompatibleWith(MediaType.APPLICATION_JSON);
        if (jsonContentType) {
            return true;
        }

        String trimmedBody = body == null ? "" : body.trim();
        boolean jsonBody = trimmedBody.startsWith("{") || trimmedBody.startsWith("[");
        if (jsonBody) {
            log.warn("Nuvemshop retornou Content-Type {} com body JSON. A resposta sera convertida mesmo assim.", contentType);
        }
        return jsonBody;
    }
}
