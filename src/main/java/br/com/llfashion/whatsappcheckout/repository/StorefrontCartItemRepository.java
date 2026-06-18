package br.com.llfashion.whatsappcheckout.repository;

import br.com.llfashion.whatsappcheckout.entity.StorefrontCartItem;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorefrontCartItemRepository extends JpaRepository<StorefrontCartItem, UUID> {
}
