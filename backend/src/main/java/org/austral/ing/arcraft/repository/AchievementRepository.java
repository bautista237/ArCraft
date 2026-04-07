package org.austral.ing.arcraft.repository;

import org.austral.ing.arcraft.entity.Achievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AchievementRepository extends JpaRepository<Achievement, UUID> {
}
