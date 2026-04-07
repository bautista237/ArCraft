package org.austral.ing.arcraft.service;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.EventLog;
import org.austral.ing.arcraft.entity.PlayerStats;
import org.austral.ing.arcraft.repository.EventLogRepository;
import org.austral.ing.arcraft.repository.PlayerRepository;
import org.austral.ing.arcraft.repository.PlayerStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final PlayerRepository playerRepository;
    private final PlayerStatsRepository playerStatsRepository;
    private final EventLogRepository eventLogRepository;

    public long getTotalPlayers() {
        return playerRepository.count();
    }

    public long getTotalKills() {
        return playerStatsRepository.findAll().stream()
                .mapToLong(PlayerStats::getKills)
                .sum();
    }

    public long getTotalMobsKilled() {
        return playerStatsRepository.findAll().stream()
                .mapToLong(PlayerStats::getMobsKilled)
                .sum();
    }

    public List<PlayerStats> getTop5Killers() {
        List<PlayerStats> all = playerStatsRepository.findAllByOrderByKillsDesc();
        return all.size() > 5 ? all.subList(0, 5) : all;
    }

    public List<EventLog> getRecentEvents() {
        return eventLogRepository.findTop20ByOrderByOccurredAtDesc();
    }
}
