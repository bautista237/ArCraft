package org.austral.ing.arcraft.repository;

import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.entity.PlayerPurchase;
import org.austral.ing.arcraft.entity.StoreItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlayerPurchaseRepository extends JpaRepository<PlayerPurchase, UUID> {
    List<PlayerPurchase> findByPlayerOrderByPurchasedAtDesc(Player player);
    boolean existsByPlayerAndItem(Player player, StoreItem item);
    long countByItem(StoreItem item);

    // All purchases of items in a given category, oldest first (so latest wins when building a map).
    List<PlayerPurchase> findByItem_CategoryOrderByPurchasedAtAsc(StoreItem.Category category);
}
