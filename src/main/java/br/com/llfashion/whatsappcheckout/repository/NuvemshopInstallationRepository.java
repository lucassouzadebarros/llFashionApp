package br.com.llfashion.whatsappcheckout.repository;

import br.com.llfashion.whatsappcheckout.entity.NuvemshopInstallation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NuvemshopInstallationRepository extends JpaRepository<NuvemshopInstallation, UUID> {

    Optional<NuvemshopInstallation> findByStoreId(Long storeId);

    Optional<NuvemshopInstallation> findTopByActiveTrueOrderByUpdatedAtDesc();
}
