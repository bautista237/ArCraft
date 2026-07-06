package org.austral.ing.arcraft.web.service;

import org.austral.ing.arcraft.db.Database;
import org.austral.ing.arcraft.web.dao.Daos;
import org.austral.ing.arcraft.web.model.ClanView;
import org.austral.ing.arcraft.web.model.PlayerView;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Admin panel operations: manage players (accounts/stats/coins), clans and live-feed events. */
public final class AdminService {

    public static final AdminService INSTANCE = new AdminService();

    private AdminService() {
    }

    // ── Players ──────────────────────────────────────────────────────────────

    public List<PlayerView> getAllPlayers() {
        return Daos.allPlayers();
    }

    public Optional<PlayerView> findPlayer(UUID id) {
        return Daos.playerById(id);
    }

    /** Creates a player + empty stats row. False when the username is taken/blank. */
    public boolean createPlayer(String username, String password, boolean isAdmin, String email) {
        if (username == null || username.isBlank()) return false;
        return Database.jdbi().inTransaction(h -> {
            boolean exists = h.createQuery("SELECT COUNT(*) FROM player WHERE username = :u")
                    .bind("u", username).mapTo(Long.class).one() > 0;
            if (exists) return false;
            UUID playerId = UUID.randomUUID();
            h.createUpdate("""
                            INSERT INTO player (id, username, password_hash, is_admin, email, email_verified,
                                                verification_sent, coins, created_at)
                            VALUES (:id, :u, :hash, :admin, :email, :verified, FALSE, 0, :created)
                            """)
                    .bind("id", playerId)
                    .bind("u", username)
                    .bind("hash", BCrypt.hashpw(password == null ? "" : password, BCrypt.gensalt()))
                    .bind("admin", isAdmin)
                    .bind("email", email == null || email.isBlank() ? null : email.trim())
                    .bind("verified", email != null && !email.isBlank())
                    .bind("created", Instant.now())
                    .execute();
            h.createUpdate("INSERT INTO player_stats (id, player_id) VALUES (:id, :pid)")
                    .bind("id", UUID.randomUUID()).bind("pid", playerId).execute();
            return true;
        });
    }

    public void updatePlayerEmail(UUID playerId, String email) {
        boolean has = email != null && !email.isBlank();
        Database.jdbi().useHandle(h -> h
                .createUpdate("""
                        UPDATE player SET email = :email, email_verified = :verified,
                                          verification_code = NULL, verification_sent = FALSE
                        WHERE id = :id
                        """)
                .bind("email", has ? email.trim() : null)
                // Admin-entered emails are trusted → mark verified so they receive reminders.
                .bind("verified", has)
                .bind("id", playerId)
                .execute());
    }

    public void updatePlayerStats(UUID playerId, long kills, long deaths,
                                  float damageDealt, float damageReceived,
                                  long mobsKilled, long blocksPlaced, long blocksMined,
                                  long itemsCrafted, long distanceWalked, long distanceSwum,
                                  long distanceFlown, long distanceSailed,
                                  long shotsFired, long shotsHit, long longestShotBlocks) {
        Database.jdbi().useHandle(h -> h
                .createUpdate("""
                        UPDATE player_stats SET kills = :kills, deaths = :deaths,
                               damage_dealt = :dd, damage_received = :dr,
                               mobs_killed = :mobs, blocks_placed = :bp, blocks_mined = :bm,
                               items_crafted = :ic, distance_walked = :dw, distance_swum = :ds,
                               distance_flown = :df, distance_sailed = :dsl,
                               shots_fired = :sf, shots_hit = :sh, longest_shot_blocks = :lsb
                        WHERE player_id = :pid
                        """)
                .bind("kills", kills).bind("deaths", deaths)
                .bind("dd", damageDealt).bind("dr", damageReceived)
                .bind("mobs", mobsKilled).bind("bp", blocksPlaced).bind("bm", blocksMined)
                .bind("ic", itemsCrafted).bind("dw", distanceWalked).bind("ds", distanceSwum)
                .bind("df", distanceFlown).bind("dsl", distanceSailed)
                .bind("sf", shotsFired).bind("sh", shotsHit).bind("lsb", longestShotBlocks)
                .bind("pid", playerId)
                .execute());
    }

    /** Deletes a player and every row referencing them. Error message or null on success. */
    public String deletePlayer(UUID playerId) {
        if (Daos.playerById(playerId).isEmpty()) return "Player not found.";
        boolean leadsClan = Database.jdbi().withHandle(h -> h
                .createQuery("SELECT COUNT(*) FROM clan WHERE leader_id = :pid")
                .bind("pid", playerId).mapTo(Long.class).one() > 0);
        if (leadsClan) {
            return "Cannot delete a player who is the leader of a clan. Reassign leadership first.";
        }
        Database.jdbi().useTransaction(h -> {
            h.createUpdate("""
                            DELETE FROM pvp_hit WHERE attacker_id = :pid OR pvp_event_id IN
                            (SELECT id FROM pvp_event WHERE killer_id = :pid OR victim_id = :pid)
                            """)
                    .bind("pid", playerId).execute();
            h.createUpdate("DELETE FROM pvp_event WHERE killer_id = :pid OR victim_id = :pid")
                    .bind("pid", playerId).execute();
            h.createUpdate("DELETE FROM clan_message WHERE sender_id = :pid").bind("pid", playerId).execute();
            h.createUpdate("UPDATE event_log SET player_id = NULL WHERE player_id = :pid").bind("pid", playerId).execute();
            h.createUpdate("DELETE FROM block_stat_entry WHERE player_id = :pid").bind("pid", playerId).execute();
            h.createUpdate("DELETE FROM item_stat_entry WHERE player_id = :pid").bind("pid", playerId).execute();
            h.createUpdate("DELETE FROM mob_stat_entry WHERE player_id = :pid").bind("pid", playerId).execute();
            h.createUpdate("DELETE FROM player_achievement WHERE player_id = :pid").bind("pid", playerId).execute();
            h.createUpdate("DELETE FROM player_purchase WHERE player_id = :pid").bind("pid", playerId).execute();
            h.createUpdate("DELETE FROM player_stats WHERE player_id = :pid").bind("pid", playerId).execute();
            h.createUpdate("DELETE FROM chunk_visit WHERE player_id = :pidStr")
                    .bind("pidStr", playerId.toString()).execute();
            h.createUpdate("DELETE FROM player WHERE id = :pid").bind("pid", playerId).execute();
        });
        return null;
    }

    // ── Live-feed events ─────────────────────────────────────────────────────

    public void createEvent(String type, String description, UUID playerId, String imageUrl) {
        Database.jdbi().useHandle(h -> h
                .createUpdate("""
                        INSERT INTO event_log (id, type, description, player_id, image_url, occurred_at)
                        VALUES (:id, :type, :desc, :pid, :img, :at)
                        """)
                .bind("id", UUID.randomUUID())
                .bind("type", org.austral.ing.arcraft.web.model.EventLogView.EventType.of(type).name())
                .bind("desc", description)
                .bind("pid", playerId)
                .bind("img", imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim())
                .bind("at", Instant.now())
                .execute());
    }

    // ── Clans ────────────────────────────────────────────────────────────────

    public List<PlayerView> getPlayersNotInClan(UUID clanId) {
        return Daos.allPlayers().stream()
                .filter(p -> p.getClan() == null || !p.getClan().getId().equals(clanId))
                .toList();
    }

    /** Creates a clan led by {@code leaderId}. Error message or null on success. */
    public String createClan(String name, String tag, UUID leaderId, boolean friendlyFireEnabled) {
        if (name == null || name.isBlank() || tag == null || tag.isBlank()) {
            return "Name and tag are required.";
        }
        return Database.jdbi().inTransaction(h -> {
            boolean nameTaken = h.createQuery("SELECT COUNT(*) FROM clan WHERE name = :n")
                    .bind("n", name).mapTo(Long.class).one() > 0;
            if (nameTaken) return "A clan with name '" + name + "' already exists.";
            PlayerView leader = Daos.playerById(leaderId).orElse(null);
            if (leader == null) return "Selected leader does not exist.";
            if (leader.getClan() != null) return "Selected leader already belongs to a clan.";

            UUID clanId = UUID.randomUUID();
            h.createUpdate("""
                            INSERT INTO clan (id, name, tag, leader_id, friendly_fire_enabled, created_at)
                            VALUES (:id, :name, :tag, :leader, :ff, :created)
                            """)
                    .bind("id", clanId).bind("name", name).bind("tag", tag)
                    .bind("leader", leaderId).bind("ff", friendlyFireEnabled)
                    .bind("created", Instant.now())
                    .execute();
            h.createUpdate("UPDATE player SET clan_id = :cid WHERE id = :pid")
                    .bind("cid", clanId).bind("pid", leaderId).execute();
            return null;
        });
    }

    public void updateClan(UUID clanId, String name, String tag, UUID leaderId, boolean friendlyFireEnabled) {
        Database.jdbi().useHandle(h -> h
                .createUpdate("""
                        UPDATE clan SET name = :name, tag = :tag, leader_id = :leader, friendly_fire_enabled = :ff
                        WHERE id = :id
                        """)
                .bind("name", name).bind("tag", tag).bind("leader", leaderId)
                .bind("ff", friendlyFireEnabled).bind("id", clanId)
                .execute());
    }

    public void addMemberToClan(UUID clanId, UUID playerId) {
        ClanService.INSTANCE.joinClan(playerId, clanId);
    }

    public void removeMemberFromClan(UUID playerId) {
        Database.jdbi().useHandle(h -> h
                .createUpdate("UPDATE player SET clan_id = NULL WHERE id = :pid")
                .bind("pid", playerId).execute());
    }

    /** Deletes a clan, detaching members and removing its chat. Error message or null. */
    public String deleteClan(UUID clanId) {
        boolean exists = Database.jdbi().withHandle(h -> h
                .createQuery("SELECT COUNT(*) FROM clan WHERE id = :id")
                .bind("id", clanId).mapTo(Long.class).one() > 0);
        if (!exists) return "Clan not found.";
        Database.jdbi().useTransaction(h -> {
            h.createUpdate("UPDATE player SET clan_id = NULL WHERE clan_id = :cid").bind("cid", clanId).execute();
            h.createUpdate("DELETE FROM clan_message WHERE clan_id = :cid").bind("cid", clanId).execute();
            h.createUpdate("DELETE FROM clan WHERE id = :cid").bind("cid", clanId).execute();
        });
        return null;
    }

    public Optional<ClanView> findClan(UUID id) {
        return ClanService.INSTANCE.getAllClans().stream()
                .filter(c -> c.getId().equals(id))
                .findFirst();
    }
}
