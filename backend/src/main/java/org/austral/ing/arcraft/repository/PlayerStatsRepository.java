package org.austral.ing.arcraft.repository;

import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.entity.PlayerStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerStatsRepository extends JpaRepository<PlayerStats, UUID> {
    Optional<PlayerStats> findByPlayer(Player player);
    Optional<PlayerStats> findByPlayerId(UUID playerId);
    List<PlayerStats> findAllByOrderByKillsDesc();
    List<PlayerStats> findAllByOrderByDeathsDesc();
    List<PlayerStats> findAllByOrderByBlocksMinedDesc();
}
