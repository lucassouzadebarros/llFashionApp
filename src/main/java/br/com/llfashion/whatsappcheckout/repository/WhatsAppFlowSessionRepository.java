package br.com.llfashion.whatsappcheckout.repository;

import br.com.llfashion.whatsappcheckout.entity.WhatsAppFlowSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsAppFlowSessionRepository extends JpaRepository<WhatsAppFlowSession, UUID> {

    Optional<WhatsAppFlowSession> findByFlowToken(String flowToken);

    Optional<WhatsAppFlowSession> findByWhatsappMessageId(String whatsappMessageId);
}
