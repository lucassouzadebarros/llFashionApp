package br.com.llfashion.whatsappcheckout.repository;

import br.com.llfashion.whatsappcheckout.entity.StorefrontCart;
import br.com.llfashion.whatsappcheckout.enums.StorefrontCartStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StorefrontCartRepository extends JpaRepository<StorefrontCart, UUID> {

    Optional<StorefrontCart> findByCartToken(String cartToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cart from StorefrontCart cart left join fetch cart.items where cart.cartToken = :cartToken")
    Optional<StorefrontCart> findByCartTokenForUpdate(@Param("cartToken") String cartToken);

    Optional<StorefrontCart> findFirstByPhoneNumberAndStatusInOrderByUpdatedAtDesc(
            String phoneNumber,
            Collection<StorefrontCartStatus> statuses
    );
}
