package org.austral.ing.arcraft.web.service;

import org.austral.ing.arcraft.db.Database;
import org.austral.ing.arcraft.web.dao.Daos;
import org.austral.ing.arcraft.web.model.ClanView;
import org.austral.ing.arcraft.web.model.PlayerView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Clan pages: listing, profile, membership (join/leave/kick) and the clan chat bridge. */
public final class ClanService {

    public static final ClanService INSTANCE = new ClanService();

    /** A chat message with its sender resolved (rendered by clan-profile + polled as JSON). */
    public record Message(PlayerView sender, String content, Instant sentAt) {}

    private static final String CLAN_WITH_LEADER = """
            SELECT %s, %s FROM clan c JOIN player l ON c.leader_id = l.id
            """.formatted(Daos.clanCols("c"), Daos.playerCols("l"));

    private ClanService() {
    }

    public List<ClanView> getAllClans() {
        return Database.jdbi().withHandle(h -> h
                .createQuery(CLAN_WITH_LEADER + " ORDER BY c.name")
                .map((rs, x) -> {
                    ClanView clan = Daos.mapClan(rs, "c");
                    clan.setLeader(Daos.mapPlayer(rs, "l"));
                    return clan;
                })
                .list());
    }

    public Optional<ClanView> findByTag(String tag) {
        return Database.jdbi().withHandle(h -> h
                .createQuery(CLAN_WITH_LEADER + " WHERE c.tag = :tag")
                .bind("tag", tag)
                .map((rs, x) -> {
                    ClanView clan = Daos.mapClan(rs, "c");
                    clan.setLeader(Daos.mapPlayer(rs, "l"));
                    return clan;
                })
                .findFirst());
    }

    public List<PlayerView> getMembers(UUID clanId) {
        return Database.jdbi().withHandle(h -> h
                .createQuery("SELECT " + Daos.playerCols("p") + " FROM player p WHERE p.clan_id = :cid ORDER BY p.username")
                .bind("cid", clanId)
                .map((rs, x) -> Daos.mapPlayer(rs, "p"))
                .list());
    }

    /** Last {@code limit} messages, oldest→newest (same shape the old service returned). */
    public List<Message> getRecentMessages(UUID clanId, int limit) {
        List<Message> newestFirst = Database.jdbi().withHandle(h -> h
                .createQuery("""
                        SELECT m.content m_content, m.sent_at m_at, %s
                        FROM clan_message m JOIN player s ON m.sender_id = s.id
                        WHERE m.clan_id = :cid ORDER BY m.sent_at DESC LIMIT :n
                        """.formatted(Daos.playerCols("s")))
                .bind("cid", clanId).bind("n", limit)
                .map((rs, x) -> new Message(Daos.mapPlayer(rs, "s"),
                        rs.getString("m_content"), Daos.instant(rs, "m_at")))
                .list());
        return newestFirst.reversed();
    }

    public void postMessage(UUID clanId, UUID senderId, String content) {
        Database.jdbi().useHandle(h -> h
                .createUpdate("""
                        INSERT INTO clan_message (id, clan_id, sender_id, content, sent_at)
                        VALUES (:id, :cid, :sid, :content, :at)
                        """)
                .bind("id", UUID.randomUUID())
                .bind("cid", clanId)
                .bind("sid", senderId)
                .bind("content", content)
                .bind("at", Instant.now())
                .execute());
    }

    public void joinClan(UUID playerId, UUID clanId) {
        Database.jdbi().useHandle(h -> h
                .createUpdate("UPDATE player SET clan_id = :cid WHERE id = :pid")
                .bind("cid", clanId).bind("pid", playerId).execute());
    }

    /** Returns an error message or null on success. */
    public String leaveClan(PlayerView player, ClanView clan) {
        if (clan.getLeader() != null && clan.getLeader().getId().equals(player.getId())) {
            return "The clan leader cannot leave the clan. Transfer leadership first.";
        }
        Database.jdbi().useHandle(h -> h
                .createUpdate("UPDATE player SET clan_id = NULL WHERE id = :pid")
                .bind("pid", player.getId()).execute());
        return null;
    }

    /** Returns an error message or null on success. */
    public String kickMember(PlayerView leader, PlayerView target, ClanView clan) {
        if (clan.getLeader() == null || !clan.getLeader().getId().equals(leader.getId())) {
            return "Only the clan leader can remove members.";
        }
        if (target.getClan() == null || !target.getClan().getId().equals(clan.getId())) {
            return "That player is not a member of this clan.";
        }
        if (target.getId().equals(leader.getId())) {
            return "The leader cannot kick themselves.";
        }
        Database.jdbi().useHandle(h -> h
                .createUpdate("UPDATE player SET clan_id = NULL WHERE id = :pid")
                .bind("pid", target.getId()).execute());
        return null;
    }

    /**
     * Aggregate stats for a clan, computed in SQL.
     * Returns long[7]: kills, deaths, mobsKilled, blocksMined, blocksPlaced, itemsCrafted, totalDistance.
     */
    public long[] getAggregateStats(UUID clanId) {
        return Database.jdbi().withHandle(h -> h
                .createQuery("""
                        SELECT COALESCE(SUM(s.kills),0) k, COALESCE(SUM(s.deaths),0) d,
                               COALESCE(SUM(s.mobs_killed),0) mk, COALESCE(SUM(s.blocks_mined),0) bm,
                               COALESCE(SUM(s.blocks_placed),0) bp, COALESCE(SUM(s.items_crafted),0) ic,
                               COALESCE(SUM(s.distance_walked + s.distance_swum + s.distance_flown + s.distance_sailed),0) dist
                        FROM player_stats s JOIN player p ON s.player_id = p.id
                        WHERE p.clan_id = :cid
                        """)
                .bind("cid", clanId)
                .map((rs, x) -> new long[]{rs.getLong("k"), rs.getLong("d"), rs.getLong("mk"),
                        rs.getLong("bm"), rs.getLong("bp"), rs.getLong("ic"), rs.getLong("dist")})
                .one());
    }
}
