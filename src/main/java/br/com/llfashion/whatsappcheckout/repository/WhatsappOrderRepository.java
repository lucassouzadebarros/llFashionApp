package br.com.llfashion.whatsappcheckout.repository;

import br.com.llfashion.whatsappcheckout.entity.WhatsappOrder;
import br.com.llfashion.whatsappcheckout.enums.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WhatsappOrderRepository extends JpaRepository<WhatsappOrder, UUID> {

    Optional<WhatsappOrder> findByNuvemshopDraftOrderId(Long nuvemshopDraftOrderId);

    Optional<WhatsappOrder> findByWhatsappMessageId(String whatsappMessageId);

    Optional<WhatsappOrder> findByStatusPublicToken(String statusPublicToken);

    List<WhatsappOrder> findTop3ByCustomerPhoneOrderByCreatedAtDesc(String customerPhone);

    List<WhatsappOrder> findByCustomerPhoneOrderByCreatedAtDesc(String customerPhone);

    @Query("""
            select o
            from WhatsappOrder o
            where o.nuvemshopDraftOrderId is not null
              and o.createdAt >= :createdAtFrom
              and o.status <> :ignoredStatus
            order by o.updatedAt asc
            """)
    List<WhatsappOrder> findTrackableOrdersSince(
            @Param("createdAtFrom") LocalDateTime createdAtFrom,
            @Param("ignoredStatus") OrderStatus ignoredStatus
    );
}
