package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.client.NuvemshopOAuthClient;
import br.com.llfashion.whatsappcheckout.config.NuvemshopProperties;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopTokenRequest;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopTokenResponse;
import br.com.llfashion.whatsappcheckout.dto.response.InstallUrlResponse;
import br.com.llfashion.whatsappcheckout.entity.NuvemshopInstallation;
import br.com.llfashion.whatsappcheckout.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NuvemshopOAuthService {

    private static final Logger log = LoggerFactory.getLogger(NuvemshopOAuthService.class);
    private static final String AUTHORIZATION_CODE = "authorization_code";

    private final NuvemshopProperties properties;
    private final NuvemshopOAuthClient oauthClient;
    private final NuvemshopInstallationService installationService;

    public NuvemshopOAuthService(
            NuvemshopProperties properties,
            NuvemshopOAuthClient oauthClient,
            NuvemshopInstallationService installationService
    ) {
        this.properties = properties;
        this.oauthClient = oauthClient;
        this.installationService = installationService;
    }

    public InstallUrlResponse getInstallUrl() {
        String installUrl = properties.authorizeUrl().replace("{appId}", properties.appId());
        return new InstallUrlResponse(installUrl);
    }

    public String processCallback(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("Parametro code e obrigatorio", HttpStatus.BAD_REQUEST);
        }
        validateOAuthConfiguration();

        String normalizedCode = code.trim();
        log.info("Callback OAuth da Nuvemshop recebido. code recebido: {}", preview(normalizedCode));
        log.info("Nuvemshop OAuth tokenUrl usada: {}", properties.tokenUrl());
        log.info("Nuvemshop OAuth appId usado: {}", properties.appId());

        NuvemshopTokenRequest tokenRequest = new NuvemshopTokenRequest(
                properties.appId(),
                properties.clientSecret(),
                AUTHORIZATION_CODE,
                normalizedCode
        );
        NuvemshopTokenResponse tokenResponse = oauthClient.trocarCodePorToken(tokenRequest);
        NuvemshopInstallation installation = installationService.saveOrUpdateInstallation(tokenResponse);

        log.info("Integracao OAuth da Nuvemshop concluida para storeId: {}", installation.getStoreId());
        return "Integracao com a Nuvemshop realizada com sucesso. Store ID: " + installation.getStoreId();
    }

    private void validateOAuthConfiguration() {
        if (!StringUtils.hasText(properties.clientSecret())) {
            throw new BusinessException("NUVEMSHOP_CLIENT_SECRET nao configurado nas variaveis de ambiente.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (!StringUtils.hasText(properties.appId())) {
            throw new BusinessException("NUVEMSHOP_APP_ID nao configurado nas variaveis de ambiente.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (!StringUtils.hasText(properties.tokenUrl())) {
            throw new BusinessException("nuvemshop.token-url nao configurado.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String preview(String value) {
        if (value == null) {
            return "";
        }
        return value.substring(0, Math.min(value.length(), 6)) + "...";
    }
}
