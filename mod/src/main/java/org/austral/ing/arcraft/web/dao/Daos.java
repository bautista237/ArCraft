package org.austral.ing.arcraft.web.dao;

import org.austral.ing.arcraft.db.Database;
import org.austral.ing.arcraft.web.model.ClanView;
import org.austral.ing.arcraft.web.model.PlayerView;
import org.austral.ing.arcraft.web.model.StatsView;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared jdbi queries + row mappers for the web layer. SQL-first (no ORM): every mapper reads
 * explicit aliased columns, and all UUID/timestamp conversions go through the helpers below so
 * they stay portable across H2 / PostgreSQL / MariaDB.
 */
public final class Daos {

    private Daos() {
    }

    // ── portable column helpers ────────────────────────────────────────────

    public static UUID uuid(ResultSet rs, String col) throws SQLException {
        String s = rs.getString(col);
        return s == null ? null : UUID.fromString(s);
    }

    public static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    // ── player (+clan) ─────────────────────────────────────────────────────

    /** Columns for {@link #mapPlayer}; prefix the player table alias, e.g. PLAYER_COLS("p"). */
    public static String playerCols(String a) {
        return ("%a.id %a_id, %a.username %a_username, %a.is_admin %a_admin, %a.coins %a_coins, "
                + "%a.email %a_email, %a.email_verified %a_everified, %a.created_at %a_created")
                .replace("%a", a);
    }

    public static PlayerView mapPlayer(ResultSet rs, String a) throws SQLException {
        if (uuid(rs, a + "_id") == null) return null;
        return new PlayerView(
                uuid(rs, a + "_id"),
                rs.getString(a + "_username"),
                rs.getBoolean(a + "_admin"),
                rs.getLong(a + "_coins"),
                rs.getString(a + "_email"),
                rs.getBoolean(a + "_everified"),
                instant(rs, a + "_created"));
    }

    public static String clanCols(String a) {
        return ("%a.id %a_id, %a.name %a_name, %a.tag %a_tag, "
                + "%a.friendly_fire_enabled %a_ff, %a.created_at %a_created")
                .replace("%a", a);
    }

    public static ClanView mapClan(ResultSet rs, String a) throws SQLException {
        UUID id = uuid(rs, a + "_id");
        if (id == null) return null;
        return new ClanView(id, rs.getString(a + "_name"), rs.getString(a + "_tag"),
                rs.getBoolean(a + "_ff"), instant(rs, a + "_created"));
    }

    private static final String PLAYER_WITH_CLAN = """
            SELECT %s, %s FROM player p LEFT JOIN clan c ON p.clan_id = c.id
            """.formatted(playerCols("p"), clanCols("c"));

    private static PlayerView mapPlayerWithClan(ResultSet rs) throws SQLException {
        PlayerView p = mapPlayer(rs, "p");
        if (p != null) p.setClan(mapClan(rs, "c")); // p is null on LEFT JOINs without a player
        return p;
    }

    public static Optional<PlayerView> playerByUsername(String username) {
        return Database.jdbi().withHandle(h -> h
                .createQuery(PLAYER_WITH_CLAN + " WHERE p.username = :u")
                .bind("u", username)
                .map((rs, c) -> mapPlayerWithClan(rs))
                .findFirst());
    }

    public static Optional<PlayerView> playerById(UUID id) {
        return Database.jdbi().withHandle(h -> h
                .createQuery(PLAYER_WITH_CLAN + " WHERE p.id = :id")
                .bind("id", id)
                .map((rs, c) -> mapPlayerWithClan(rs))
                .findFirst());
    }

    public static List<PlayerView> allPlayers() {
        return Database.jdbi().withHandle(h -> h
                .createQuery(PLAYER_WITH_CLAN + " ORDER BY p.username")
                .map((rs, c) -> mapPlayerWithClan(rs))
                .list());
    }

    /** id, username, password_hash, is_admin — for the login check only. */
    public record AuthRow(UUID id, String username, String passwordHash, boolean admin) {}

    public static Optional<AuthRow> auth(String username) {
        return Database.jdbi().withHandle(h -> h
                .createQuery("SELECT id, username, password_hash, is_admin FROM player WHERE username = :u")
                .bind("u", username)
                .map((rs, c) -> new AuthRow(uuid(rs, "id"), rs.getString("username"),
                        rs.getString("password_hash"), rs.getBoolean("is_admin")))
                .findFirst());
    }

    // ── player_stats (+player +clan) ───────────────────────────────────────

    public static String statsCols(String a) {
        return ("%a.player_id %a_pid, %a.kills %a_kills, %a.deaths %a_deaths, %a.pvp_deaths %a_pvpdeaths, "
                + "%a.damage_dealt %a_dd, %a.damage_received %a_dr, "
                + "%a.pvp_damage_dealt %a_pdd, %a.pvp_damage_received %a_pdr, "
                + "%a.mobs_killed %a_mobs, %a.blocks_placed %a_bp, %a.blocks_mined %a_bm, "
                + "%a.items_crafted %a_ic, %a.distance_walked %a_dw, %a.distance_swum %a_ds, "
                + "%a.distance_flown %a_df, %a.distance_sailed %a_dsl, "
                + "%a.shots_fired %a_sf, %a.shots_hit %a_sh, %a.longest_shot_blocks %a_lsb")
                .replace("%a", a);
    }

    public static StatsView mapStats(ResultSet rs, String a) throws SQLException {
        StatsView s = new StatsView(uuid(rs, a + "_pid"));
        s.setKills(rs.getLong(a + "_kills"));
        s.setDeaths(rs.getLong(a + "_deaths"));
        s.setPvpDeaths(rs.getLong(a + "_pvpdeaths"));
        s.setDamageDealt(rs.getFloat(a + "_dd"));
        s.setDamageReceived(rs.getFloat(a + "_dr"));
        s.setPvpDamageDealt(rs.getFloat(a + "_pdd"));
        s.setPvpDamageReceived(rs.getFloat(a + "_pdr"));
        s.setMobsKilled(rs.getLong(a + "_mobs"));
        s.setBlocksPlaced(rs.getLong(a + "_bp"));
        s.setBlocksMined(rs.getLong(a + "_bm"));
        s.setItemsCrafted(rs.getLong(a + "_ic"));
        s.setDistanceWalked(rs.getLong(a + "_dw"));
        s.setDistanceSwum(rs.getLong(a + "_ds"));
        s.setDistanceFlown(rs.getLong(a + "_df"));
        s.setDistanceSailed(rs.getLong(a + "_dsl"));
        s.setShotsFired(rs.getLong(a + "_sf"));
        s.setShotsHit(rs.getLong(a + "_sh"));
        s.setLongestShotBlocks(rs.getLong(a + "_lsb"));
        return s;
    }

    private static final String STATS_WITH_PLAYER = """
            SELECT %s, %s, %s FROM player_stats s
            JOIN player p ON s.player_id = p.id
            LEFT JOIN clan c ON p.clan_id = c.id
            """.formatted(statsCols("s"), playerCols("p"), clanCols("c"));

    public static List<StatsView> allStatsWithPlayers() {
        return Database.jdbi().withHandle(h -> h
                .createQuery(STATS_WITH_PLAYER)
                .map((rs, c) -> {
                    StatsView s = mapStats(rs, "s");
                    s.setPlayer(mapPlayerWithClan(rs));
                    return s;
                })
                .list());
    }

    // ── event_log (+player) ────────────────────────────────────────────────

    /** The most recent {@code limit} live-feed entries, newest first, with their player. */
    public static List<org.austral.ing.arcraft.web.model.EventLogView> recentEvents(int limit) {
        return Database.jdbi().withHandle(h -> h
                .createQuery("""
                        SELECT e.id e_id, e.type e_type, e.description e_desc, e.image_url e_img,
                               e.occurred_at e_at, %s, %s
                        FROM event_log e
                        LEFT JOIN player p ON e.player_id = p.id
                        LEFT JOIN clan c ON p.clan_id = c.id
                        ORDER BY e.occurred_at DESC LIMIT :n
                        """.formatted(playerCols("p"), clanCols("c")))
                .bind("n", limit)
                .map((rs, c) -> {
                    var ev = new org.austral.ing.arcraft.web.model.EventLogView(
                            uuid(rs, "e_id"), rs.getString("e_type"), rs.getString("e_desc"),
                            rs.getString("e_img"), instant(rs, "e_at"));
                    ev.setPlayer(mapPlayerWithClan(rs));
                    return ev;
                })
                .list());
    }

    public static Optional<StatsView> statsByPlayerId(UUID playerId) {
        return Database.jdbi().withHandle(h -> h
                .createQuery(STATS_WITH_PLAYER + " WHERE s.player_id = :id")
                .bind("id", playerId)
                .map((rs, c) -> {
                    StatsView s = mapStats(rs, "s");
                    s.setPlayer(mapPlayerWithClan(rs));
                    return s;
                })
                .findFirst());
    }
}
