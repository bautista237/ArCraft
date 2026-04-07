package org.austral.ing.arcraft.repository;

import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.entity.PvPEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PvPEventRepository extends JpaRepository<PvPEvent, UUID> {
    List<PvPEvent> findByKiller(Player killer);
    List<PvPEvent> findByVictim(Player victim);
    List<PvPEvent> findByKillerOrVictimOrderByEndedAtDesc(Player killer, Player victim);
}
