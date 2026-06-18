package br.com.llfashion.whatsappcheckout.service;

import br.com.llfashion.whatsappcheckout.config.NuvemshopProperties;
import br.com.llfashion.whatsappcheckout.dto.nuvemshop.NuvemshopTokenResponse;
import br.com.llfashion.whatsappcheckout.entity.NuvemshopInstallation;
import br.com.llfashion.whatsappcheckout.exception.BusinessException;
import br.com.llfashion.whatsappcheckout.exception.EntityNotFoundException;
import br.com.llfashion.whatsappcheckout.repository.NuvemshopInstallationRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class NuvemshopInstallationService {

    private final NuvemshopInstallationRepository repository;
    private final NuvemshopProperties properties;

    public NuvemshopInstallationService(NuvemshopInstallationRepository repository, NuvemshopProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public NuvemshopInstallation getActiveInstallation() {
        return repository.findTopByActiveTrueOrderByUpdatedAtDesc()
                .orElseThrow(() -> new BusinessException("Nenhuma instalação ativa da Nuvemshop foi encontrada"));
    }

    @Transactional
    public NuvemshopInstallation saveOrUpdateInstallation(NuvemshopTokenResponse tokenResponse) {
        validateTokenResponse(tokenResponse);

        NuvemshopInstallation installation = repository.findByStoreId(tokenResponse.userId())
                .orElseGet(NuvemshopInstallation::new);

        installation.setAppId(properties.appId());
        installation.setStoreId(tokenResponse.userId());
        installation.setAccessToken(tokenResponse.accessToken());
        installation.setTokenType(tokenResponse.tokenType());
        installation.setScope(tokenResponse.scope());
        installation.setActive(true);

        return repository.save(installation);
    }

    @Transactional(readOnly = true)
    public Optional<NuvemshopInstallation> findByStoreId(Long storeId) {
        return repository.findByStoreId(storeId);
    }

    @Transactional
    public void deactivateInstallation(Long storeId) {
        NuvemshopInstallation installation = repository.findByStoreId(storeId)
                .orElseThrow(() -> new EntityNotFoundException("Instalação não encontrada para storeId: " + storeId));
        installation.setActive(false);
        repository.save(installation);
    }

    private void validateTokenResponse(NuvemshopTokenResponse tokenResponse) {
        if (tokenResponse == null || !StringUtils.hasText(tokenResponse.accessToken()) || tokenResponse.userId() == null) {
            throw new BusinessException("Resposta OAuth da Nuvemshop não retornou access_token ou user_id");
        }
    }
}
