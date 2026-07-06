package org.austral.ing.arcraft.web.service;

import org.austral.ing.arcraft.web.dao.Daos;
import org.austral.ing.arcraft.web.model.StatsView;

import java.util.Comparator;
import java.util.List;

/** Sortable leaderboards over player_stats (same sort keys as the old Spring service). */
public final class RankingsService {

    public static final RankingsService INSTANCE = new RankingsService();

    private RankingsService() {
    }

    public static double computeKdRatio(StatsView s) {
        return s.getDeaths() == 0 ? s.getKills()
                : Math.round((double) s.getKills() / s.getDeaths() * 100.0) / 100.0;
    }

    public static double computeBowAccuracy(StatsView s) {
        return s.getShotsFired() == 0 ? 0
                : Math.round((double) s.getShotsHit() / s.getShotsFired() * 10000.0) / 100.0;
    }

    public static long computeTotalDistance(StatsView s) {
        return s.getDistanceWalked() + s.getDistanceSwum() + s.getDistanceFlown() + s.getDistanceSailed();
    }

    public List<StatsView> getSorted(String sortBy) {
        List<StatsView> all = Daos.allStatsWithPlayers();
        Comparator<StatsView> cmp = switch (sortBy) {
            case "deaths" -> Comparator.comparingLong(StatsView::getDeaths);
            case "kd" -> Comparator.comparingDouble(RankingsService::computeKdRatio);
            case "blocksMined" -> Comparator.comparingLong(StatsView::getBlocksMined);
            case "blocksPlaced" -> Comparator.comparingLong(StatsView::getBlocksPlaced);
            case "mobsKilled" -> Comparator.comparingLong(StatsView::getMobsKilled);
            case "itemsCrafted" -> Comparator.comparingLong(StatsView::getItemsCrafted);
            case "distance" -> Comparator.comparingLong(RankingsService::computeTotalDistance);
            case "accuracy" -> Comparator.comparingDouble(RankingsService::computeBowAccuracy);
            default -> Comparator.comparingLong(StatsView::getKills);
        };
        List<StatsView> sorted = new java.util.ArrayList<>(all);
        sorted.sort(cmp.reversed());
        return sorted;
    }
}
