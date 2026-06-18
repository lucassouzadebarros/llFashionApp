package br.com.llfashion.whatsappcheckout.repository;

import br.com.llfashion.whatsappcheckout.entity.OrderStatusAccessToken;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OrderStatusAccessTokenRepository extends JpaRepository<OrderStatusAccessToken, UUID> {

    Optional<OrderStatusAccessToken> findByAccessTokenHashAndExpiresAtAfter(String accessTokenHash, LocalDateTime now);

    @Transactional
    @Modifying
    @Query("delete from OrderStatusAccessToken token where token.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
