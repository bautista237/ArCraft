package org.austral.ing.arcraft.service;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.PlayerStats;
import org.austral.ing.arcraft.repository.PlayerStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingsService {

    private final PlayerStatsRepository playerStatsRepository;

    public List<PlayerStats> getAllStats() {
        return playerStatsRepository.findAll();
    }

    public static double computeKdRatio(PlayerStats s) {
        return s.getDeaths() == 0 ? s.getKills() : Math.round((double) s.getKills() / s.getDeaths() * 100.0) / 100.0;
    }

    public static double computeBowAccuracy(PlayerStats s) {
        return s.getShotsFired() == 0 ? 0 : Math.round((double) s.getShotsHit() / s.getShotsFired() * 10000.0) / 100.0;
    }

    public static long computeTotalDistance(PlayerStats s) {
        return s.getDistanceWalked() + s.getDistanceSwum() + s.getDistanceFlown() + s.getDistanceSailed();
    }

    public List<PlayerStats> getSorted(String sortBy) {
        List<PlayerStats> all = playerStatsRepository.findAll();
        Comparator<PlayerStats> cmp = switch (sortBy) {
            case "deaths" -> Comparator.comparingLong(PlayerStats::getDeaths);
            case "kd" -> Comparator.comparingDouble(RankingsService::computeKdRatio);
            case "blocksMined" -> Comparator.comparingLong(PlayerStats::getBlocksMined);
            case "blocksPlaced" -> Comparator.comparingLong(PlayerStats::getBlocksPlaced);
            case "mobsKilled" -> Comparator.comparingLong(PlayerStats::getMobsKilled);
            case "itemsCrafted" -> Comparator.comparingLong(PlayerStats::getItemsCrafted);
            case "distance" -> Comparator.comparingLong(RankingsService::computeTotalDistance);
            case "accuracy" -> Comparator.comparingDouble(RankingsService::computeBowAccuracy);
            default -> Comparator.comparingLong(PlayerStats::getKills);
        };
        all.sort(cmp.reversed());
        return all;
    }
}
