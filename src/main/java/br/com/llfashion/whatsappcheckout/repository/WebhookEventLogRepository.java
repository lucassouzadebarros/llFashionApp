package br.com.llfashion.whatsappcheckout.repository;

import br.com.llfashion.whatsappcheckout.entity.WebhookEventLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventLogRepository extends JpaRepository<WebhookEventLog, UUID> {
}
