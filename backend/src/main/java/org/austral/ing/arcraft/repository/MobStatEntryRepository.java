package org.austral.ing.arcraft.repository;

import org.austral.ing.arcraft.entity.MobStatEntry;
import org.austral.ing.arcraft.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MobStatEntryRepository extends JpaRepository<MobStatEntry, UUID> {
    List<MobStatEntry> findByPlayer(Player player);
    List<MobStatEntry> findByPlayerId(UUID playerId);
    Optional<MobStatEntry> findByPlayerAndMobType(Player player, String mobType);
}
