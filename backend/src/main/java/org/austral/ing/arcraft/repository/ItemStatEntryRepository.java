package org.austral.ing.arcraft.repository;

import org.austral.ing.arcraft.entity.ItemStatEntry;
import org.austral.ing.arcraft.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemStatEntryRepository extends JpaRepository<ItemStatEntry, UUID> {
    List<ItemStatEntry> findByPlayer(Player player);
    List<ItemStatEntry> findByPlayerId(UUID playerId);
    Optional<ItemStatEntry> findByPlayerAndItemType(Player player, String itemType);
}
