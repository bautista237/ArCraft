package org.austral.ing.arcraft.web.service;

import org.austral.ing.arcraft.db.Database;
import org.austral.ing.arcraft.web.dao.Daos;
import org.austral.ing.arcraft.web.model.EventLogView;
import org.austral.ing.arcraft.web.model.StatsView;

import java.util.List;

/** Server-wide aggregates for the dashboard (totals, top killers, live feed, global top-N). */
public final class DashboardService {

    public static final DashboardService INSTANCE = new DashboardService();

    /** A server-wide aggregate row: pretty name, icon URL, total, and the icon's average colour. */
    public record StatSlice(String name, String iconUrl, long count, String color) {}

    private enum Kind { ITEM, BLOCK, MOB }

    private DashboardService() {
    }

    public long getTotalPlayers() {
        return Database.jdbi().withHandle(h ->
                h.createQuery("SELECT COUNT(*) FROM player").mapTo(Long.class).one());
    }

    public long getTotalKills() {
        return Database.jdbi().withHandle(h ->
                h.createQuery("SELECT COALESCE(SUM(kills), 0) FROM player_stats").mapTo(Long.class).one());
    }

    public long getTotalMobsKilled() {
        return Database.jdbi().withHandle(h ->
                h.createQuery("SELECT COALESCE(SUM(mobs_killed), 0) FROM player_stats").mapTo(Long.class).one());
    }

    public List<StatsView> getTop5Killers() {
        return Daos.allStatsWithPlayers().stream()
                .sorted(java.util.Comparator.comparingLong(StatsView::getKills).reversed())
                .limit(5)
                .toList();
    }

    public List<EventLogView> getRecentEvents() {
        return Daos.recentEvents(5);
    }

    public List<StatSlice> getTopItemsCraftedGlobal(int limit) {
        return aggregate("SELECT item_type t, SUM(count) c FROM item_stat_entry GROUP BY item_type", Kind.ITEM, limit);
    }

    public List<StatSlice> getTopBlocksMinedGlobal(int limit) {
        return aggregate("SELECT block_type t, SUM(mined) c FROM block_stat_entry GROUP BY block_type", Kind.BLOCK, limit);
    }

    public List<StatSlice> getTopBlocksPlacedGlobal(int limit) {
        return aggregate("SELECT block_type t, SUM(placed) c FROM block_stat_entry GROUP BY block_type", Kind.BLOCK, limit);
    }

    public List<StatSlice> getTopMobsKilledGlobal(int limit) {
        return aggregate("SELECT mob_type t, SUM(count) c FROM mob_stat_entry GROUP BY mob_type", Kind.MOB, limit);
    }

    private List<StatSlice> aggregate(String groupedSql, Kind kind, int limit) {
        IconService icons = IconService.INSTANCE;
        return Database.jdbi().withHandle(h -> h
                .createQuery(groupedSql + " ORDER BY c DESC LIMIT :n")
                .bind("n", limit)
                .map((rs, c) -> {
                    String id = rs.getString("t");
                    long count = rs.getLong("c");
                    if (count <= 0) return null;
                    String iconUrl = kind == Kind.MOB ? icons.mobIcon(id) : icons.icon(id);
                    String color = kind == Kind.MOB ? icons.mobColor(id) : icons.color(id);
                    return new StatSlice(icons.pretty(id), iconUrl, count, color);
                })
                .list())
                .stream().filter(java.util.Objects::nonNull).toList();
    }
}
