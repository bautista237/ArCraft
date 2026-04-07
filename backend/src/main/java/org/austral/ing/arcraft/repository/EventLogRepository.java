package org.austral.ing.arcraft.repository;

import org.austral.ing.arcraft.entity.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventLogRepository extends JpaRepository<EventLog, UUID> {
    List<EventLog> findTop20ByOrderByOccurredAtDesc();
    List<EventLog> findByPlayerIdOrderByOccurredAtDesc(UUID playerId);
}
