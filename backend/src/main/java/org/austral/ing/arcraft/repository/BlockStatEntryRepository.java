package org.austral.ing.arcraft.repository;

import org.austral.ing.arcraft.entity.BlockStatEntry;
import org.austral.ing.arcraft.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlockStatEntryRepository extends JpaRepository<BlockStatEntry, UUID> {
    List<BlockStatEntry> findByPlayer(Player player);
    List<BlockStatEntry> findByPlayerId(UUID playerId);
    Optional<BlockStatEntry> findByPlayerAndBlockType(Player player, String blockType);
}
