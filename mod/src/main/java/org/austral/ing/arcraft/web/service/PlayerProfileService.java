package org.austral.ing.arcraft.web.service;

import org.austral.ing.arcraft.db.Database;
import org.austral.ing.arcraft.web.dao.Daos;
import org.austral.ing.arcraft.web.model.PlayerView;
import org.austral.ing.arcraft.web.model.PvPEventView;
import org.austral.ing.arcraft.web.model.Stat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything the player profile page shows: breakdowns, PvP history and PvP analytics. */
public final class PlayerProfileService {

    public static final PlayerProfileService INSTANCE = new PlayerProfileService();

    private PlayerProfileService() {
    }

    public List<Stat.Block> getTopBlocksMined(UUID playerId, int limit) {
        return blocks(playerId, "mined", limit);
    }

    public List<Stat.Block> getTopBlocksPlaced(UUID playerId, int limit) {
        return blocks(playerId, "placed", limit);
    }

    private List<Stat.Block> blocks(UUID playerId, String orderCol, int limit) {
        return Database.jdbi().withHandle(h -> h
                .createQuery("SELECT block_type, mined, placed FROM block_stat_entry "
                        + "WHERE player_id = :pid ORDER BY " + orderCol + " DESC LIMIT :n")
                .bind("pid", playerId).bind("n", limit)
                .map((rs, c) -> new Stat.Block(rs.getString("block_type"), rs.getLong("mined"), rs.getLong("placed")))
                .list());
    }

    public List<Stat.Item> getTopItemsCrafted(UUID playerId, int limit) {
        return Database.jdbi().withHandle(h -> h
                .createQuery("SELECT item_type, count FROM item_stat_entry "
                        + "WHERE player_id = :pid ORDER BY count DESC LIMIT :n")
                .bind("pid", playerId).bind("n", limit)
                .map((rs, c) -> new Stat.Item(rs.getString("item_type"), rs.getLong("count")))
                .list());
    }

    public List<Stat.Mob> getTopMobsKilled(UUID playerId, int limit) {
        return Database.jdbi().withHandle(h -> h
                .createQuery("SELECT mob_type, count FROM mob_stat_entry "
                        + "WHERE player_id = :pid ORDER BY count DESC LIMIT :n")
                .bind("pid", playerId).bind("n", limit)
                .map((rs, c) -> new Stat.Mob(rs.getString("mob_type"), rs.getLong("count")))
                .list());
    }

    /** All fights this player took part in, newest first, both fighters resolved. */
    public List<PvPEventView> getRecentPvP(UUID playerId) {
        return Database.jdbi().withHandle(h -> h
                .createQuery("""
                        SELECT e.id e_id, e.started_at e_start, e.ended_at e_end,
                               %s, %s
                        FROM pvp_event e
                        JOIN player k ON e.killer_id = k.id
                        JOIN player v ON e.victim_id = v.id
                        WHERE e.killer_id = :pid OR e.victim_id = :pid
                        ORDER BY e.ended_at DESC
                        """.formatted(Daos.playerCols("k"), Daos.playerCols("v")))
                .bind("pid", playerId)
                .map((rs, c) -> {
                    PvPEventView ev = new PvPEventView(Daos.uuid(rs, "e_id"),
                            Daos.instant(rs, "e_start"), Daos.instant(rs, "e_end"));
                    ev.setKiller(Daos.mapPlayer(rs, "k"));
                    ev.setVictim(Daos.mapPlayer(rs, "v"));
                    return ev;
                })
                .list());
    }

    // ── PvP analytics ────────────────────────────────────────────────────────

    /** Head-to-head record against a single opponent. */
    public record Matchup(PlayerView opponent, long killsAgainst, long deathsTo) {
        public long total() { return killsAgainst + deathsTo; }
        public double winRate() { return total() == 0 ? 0 : (double) killsAgainst / total() * 100.0; }
    }

    /** Aggregated PvP picture for a player's profile. */
    public record PvpAnalytics(
            long totalFights, long kills, long deaths, double winRate,
            Matchup nemesis,        // opponent who killed this player the most
            Matchup favoriteVictim, // opponent this player killed the most
            List<Matchup> matchups) {}

    public PvpAnalytics getPvpAnalytics(UUID playerId) {
        List<PvPEventView> events = getRecentPvP(playerId);

        Map<UUID, long[]> tally = new HashMap<>();
        Map<UUID, PlayerView> opponents = new HashMap<>();
        long kills = 0, deaths = 0;
        for (PvPEventView ev : events) {
            PlayerView killer = ev.getKiller();
            PlayerView victim = ev.getVictim();
            if (killer == null || victim == null) continue;
            boolean iAmKiller = killer.getId().equals(playerId);
            PlayerView opp = iAmKiller ? victim : killer;
            if (opp == null || opp.getId().equals(playerId)) continue;
            long[] rec = tally.computeIfAbsent(opp.getId(), k -> new long[2]);
            opponents.putIfAbsent(opp.getId(), opp);
            if (iAmKiller) { rec[0]++; kills++; } else { rec[1]++; deaths++; }
        }

        List<Matchup> matchups = new ArrayList<>();
        for (var e : tally.entrySet()) {
            matchups.add(new Matchup(opponents.get(e.getKey()), e.getValue()[0], e.getValue()[1]));
        }
        matchups.sort(Comparator.comparingLong(Matchup::total).reversed());

        Matchup nemesis = matchups.stream()
                .filter(m -> m.deathsTo() > 0)
                .max(Comparator.comparingLong(Matchup::deathsTo)).orElse(null);
        Matchup favoriteVictim = matchups.stream()
                .filter(m -> m.killsAgainst() > 0)
                .max(Comparator.comparingLong(Matchup::killsAgainst)).orElse(null);

        long total = kills + deaths;
        double winRate = total == 0 ? 0 : (double) kills / total * 100.0;
        return new PvpAnalytics(events.size(), kills, deaths, winRate, nemesis, favoriteVictim, matchups);
    }
}
