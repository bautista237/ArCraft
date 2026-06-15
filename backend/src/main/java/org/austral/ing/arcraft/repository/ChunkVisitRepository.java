package org.austral.ing.arcraft.repository;

import org.austral.ing.arcraft.entity.ChunkVisit;
import org.austral.ing.arcraft.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChunkVisitRepository extends JpaRepository<ChunkVisit, UUID> {
    List<ChunkVisit> findByPlayerOrderByChunkXAscChunkZAsc(Player player);
    List<ChunkVisit> findByPlayerAndDimension(Player player, String dimension);
    long countByPlayer(Player player);
    long countByPlayerAndDimension(Player player, String dimension);
    List<ChunkVisit> findAllByDimension(String dimension);
}
