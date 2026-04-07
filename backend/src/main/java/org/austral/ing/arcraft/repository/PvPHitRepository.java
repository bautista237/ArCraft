package org.austral.ing.arcraft.repository;

import org.austral.ing.arcraft.entity.PvPEvent;
import org.austral.ing.arcraft.entity.PvPHit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PvPHitRepository extends JpaRepository<PvPHit, UUID> {
    List<PvPHit> findByPvpEventOrderByHitAtAsc(PvPEvent pvpEvent);
}
