package org.austral.ing.arcraft.repository;

import org.austral.ing.arcraft.entity.PlayerAchievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlayerAchievementRepository extends JpaRepository<PlayerAchievement, UUID> {
    List<PlayerAchievement> findByPlayerIdOrderByEarnedAtDesc(UUID playerId);
}
