package br.com.llfashion.whatsappcheckout.repository;

import br.com.llfashion.whatsappcheckout.entity.WhatsAppCheckoutSession;
import br.com.llfashion.whatsappcheckout.enums.WhatsAppCheckoutSessionStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsAppCheckoutSessionRepository extends JpaRepository<WhatsAppCheckoutSession, UUID> {

    Optional<WhatsAppCheckoutSession> findByWhatsappMessageId(String whatsappMessageId);

    Optional<WhatsAppCheckoutSession> findFirstByCustomerPhoneAndStatusOrderByUpdatedAtDesc(
            String customerPhone,
            WhatsAppCheckoutSessionStatus status
    );

    Optional<WhatsAppCheckoutSession> findFirstByCustomerPhoneAndStatusInOrderByUpdatedAtDesc(
            String customerPhone,
            Collection<WhatsAppCheckoutSessionStatus> statuses
    );
}
