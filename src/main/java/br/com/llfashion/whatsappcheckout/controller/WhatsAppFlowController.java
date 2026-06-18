package br.com.llfashion.whatsappcheckout.controller;

import br.com.llfashion.whatsappcheckout.service.WhatsAppFlowCryptoService;
import br.com.llfashion.whatsappcheckout.service.WhatsAppFlowService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/whatsapp/flows")
public class WhatsAppFlowController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppFlowController.class);

    private final WhatsAppFlowService whatsAppFlowService;
    private final WhatsAppFlowCryptoService cryptoService;

    public WhatsAppFlowController(WhatsAppFlowService whatsAppFlowService, WhatsAppFlowCryptoService cryptoService) {
        this.whatsAppFlowService = whatsAppFlowService;
        this.cryptoService = cryptoService;
    }

    @PostMapping(value = "/data-exchange", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dataExchange(@RequestBody JsonNode payload) {
        if (cryptoService.isEncryptedEnvelope(payload)) {
            WhatsAppFlowCryptoService.DecryptedFlowPayload decryptedPayload = cryptoService.decrypt(payload);
            logRequest(decryptedPayload.payload(), true);
            Map<String, Object> response = whatsAppFlowService.handleDataExchange(decryptedPayload.payload());
            logResponse(response);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(cryptoService.encryptResponse(decryptedPayload, response));
        }

        logRequest(payload, false);
        Map<String, Object> response = whatsAppFlowService.handleDataExchange(payload);
        logResponse(response);
        return ResponseEntity.ok(response);
    }

    private void logRequest(JsonNode payload, boolean encrypted) {
        JsonNode data = payload == null ? null : payload.path("data");
        String flowToken = firstText(payload, data, "flow_token", "flowToken");
        log.info(
                "WhatsApp Flow data-exchange recebido. encrypted={}, action={}, screen={}, flowToken={}, dataKeys={}, flowError={}",
                encrypted,
                text(payload, "action"),
                text(payload, "screen"),
                mask(flowToken),
                fieldNames(data),
                flowError(data)
        );
    }

    private void logResponse(Map<String, Object> response) {
        Object data = response.get("data");
        log.info(
                "WhatsApp Flow data-exchange resposta. screen={}, dataKeys={}",
                response.get("screen"),
                data instanceof Map<?, ?> map ? map.keySet() : "[]"
        );
    }

    private String fieldNames(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
            return "[]";
        }
        StringBuilder names = new StringBuilder("[");
        Iterator<String> iterator = node.fieldNames();
        while (iterator.hasNext()) {
            if (names.length() > 1) {
                names.append(", ");
            }
            names.append(iterator.next());
        }
        return names.append(']').toString();
    }

    private String flowError(JsonNode data) {
        String error = text(data, "error");
        String errorMessage = text(data, "error_message", "errorMessage");
        if ((error == null || error.isBlank()) && (errorMessage == null || errorMessage.isBlank())) {
            return "";
        }
        return preview((error == null ? "flow_client_error" : error)
                + (errorMessage == null ? "" : ": " + errorMessage));
    }

    private String firstText(JsonNode firstNode, JsonNode secondNode, String... names) {
        String first = text(firstNode, names);
        return first == null || first.isBlank() ? text(secondNode, names) : first;
    }

    private String text(JsonNode node, String... names) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        int visible = Math.min(8, trimmed.length());
        return trimmed.substring(0, visible) + "...";
    }

    private String preview(String value) {
        if (value == null) {
            return "";
        }
        return value.substring(0, Math.min(value.length(), 500));
    }
}
