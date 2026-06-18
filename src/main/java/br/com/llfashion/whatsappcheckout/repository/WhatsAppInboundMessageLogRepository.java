package br.com.llfashion.whatsappcheckout.repository;

import br.com.llfashion.whatsappcheckout.entity.WhatsAppInboundMessageLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsAppInboundMessageLogRepository extends JpaRepository<WhatsAppInboundMessageLog, UUID> {
}
