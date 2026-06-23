package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.config.WhatsAppProperties;
import br.com.llfashion.whatsappcheckout.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WhatsAppFlowCryptoService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppFlowCryptoService.class);
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final WhatsAppProperties properties;
    private final ObjectMapper objectMapper;

    public WhatsAppFlowCryptoService(WhatsAppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isEncryptedEnvelope(JsonNode payload) {
        return payload != null
                && payload.hasNonNull("encrypted_flow_data")
                && payload.hasNonNull("encrypted_aes_key")
                && payload.hasNonNull("initial_vector");
    }

    public DecryptedFlowPayload decrypt(JsonNode encryptedEnvelope) {
        try {
            byte[] aesKey = decryptAesKey(encryptedEnvelope.path("encrypted_aes_key").asText());
            byte[] iv = Base64.getDecoder().decode(encryptedEnvelope.path("initial_vector").asText());
            byte[] encryptedFlowData = Base64.getDecoder().decode(encryptedEnvelope.path("encrypted_flow_data").asText());

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] decrypted = cipher.doFinal(encryptedFlowData);
            JsonNode payload = objectMapper.readTree(new String(decrypted, StandardCharsets.UTF_8));
            return new DecryptedFlowPayload(payload, aesKey, iv);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Falha ao descriptografar chamada do WhatsApp Flow: {}", exception.getMessage(), exception);
            throw new BusinessException("Não foi possível descriptografar a chamada do WhatsApp Flow.", HttpStatus.BAD_REQUEST);
        }
    }

    public String encryptResponse(DecryptedFlowPayload decryptedPayload, Object response) {
        try {
            byte[] responseJson = objectMapper.writeValueAsBytes(response);
            byte[] flippedIv = flipIv(decryptedPayload.initialVector());

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(decryptedPayload.aesKey(), "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, flippedIv)
            );
            return Base64.getEncoder().encodeToString(cipher.doFinal(responseJson));
        } catch (Exception exception) {
            throw new BusinessException("Não foi possível criptografar a resposta do WhatsApp Flow.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private byte[] decryptAesKey(String encryptedAesKey) throws Exception {
        PrivateKey privateKey = loadPrivateKey();
        byte[] encryptedKey = Base64.getDecoder().decode(encryptedAesKey);

        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            OAEPParameterSpec oaepSha256 = new OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    PSource.PSpecified.DEFAULT
            );
            cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepSha256);
            return cipher.doFinal(encryptedKey);
        } catch (Exception exception) {
            log.debug("Falha ao abrir AES key com RSA OAEP SHA-256. Tentando fallback SHA-1. Motivo: {}", exception.getMessage());
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
            OAEPParameterSpec oaepSha1 = new OAEPParameterSpec(
                    "SHA-1",
                    "MGF1",
                    MGF1ParameterSpec.SHA1,
                    PSource.PSpecified.DEFAULT
            );
            cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepSha1);
            return cipher.doFinal(encryptedKey);
        }
    }

    private PrivateKey loadPrivateKey() throws Exception {
        WhatsAppProperties.Flows flows = properties.flows();
        String pem = flows == null ? null : flows.privateKey();

        if (!StringUtils.hasText(pem) && flows != null && StringUtils.hasText(flows.privateKeyPath())) {
            pem = Files.readString(Path.of(flows.privateKeyPath().trim()));
        }

        if (!StringUtils.hasText(pem)) {
            throw new BusinessException("WHATSAPP_FLOW_PRIVATE_KEY ou WHATSAPP_FLOW_PRIVATE_KEY_PATH não configurado.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String normalized = pem.replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            throw new BusinessException("Use uma chave privada PKCS#8 para WhatsApp Flows: -----BEGIN PRIVATE KEY-----.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private byte[] flipIv(byte[] iv) {
        byte[] flipped = new byte[iv.length];
        for (int index = 0; index < iv.length; index++) {
            flipped[index] = (byte) (iv[index] ^ 0xFF);
        }
        return flipped;
    }

    public record DecryptedFlowPayload(JsonNode payload, byte[] aesKey, byte[] initialVector) {
    }
}
