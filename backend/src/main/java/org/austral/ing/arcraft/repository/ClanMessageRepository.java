package org.austral.ing.arcraft.repository;

import org.austral.ing.arcraft.entity.ClanMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClanMessageRepository extends JpaRepository<ClanMessage, UUID> {
    List<ClanMessage> findByClanIdOrderBySentAtAsc(UUID clanId);
}
