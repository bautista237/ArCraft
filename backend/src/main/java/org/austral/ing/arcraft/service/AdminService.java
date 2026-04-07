package org.austral.ing.arcraft.service;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.*;
import org.austral.ing.arcraft.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {

    private final PlayerRepository playerRepository;
    private final PlayerStatsRepository playerStatsRepository;
    private final EventLogRepository eventLogRepository;
    private final ClanRepository clanRepository;
    private final PasswordEncoder passwordEncoder;

    // ── Players ──────────────────────────────────────────────

    public List<Player> getAllPlayers() {
        return playerRepository.findAll();
    }

    public Player getPlayer(UUID id) {
        return playerRepository.findById(id).orElseThrow();
    }

    public PlayerStats getPlayerStats(UUID playerId) {
        return playerStatsRepository.findByPlayerId(playerId).orElseThrow();
    }

    public void createPlayer(String username, String password, boolean isAdmin) {
        Player player = new Player();
        player.setUsername(username);
        player.setPasswordHash(passwordEncoder.encode(password));
        player.setAdmin(isAdmin);
        player.setCreatedAt(Instant.now());
        playerRepository.save(player);

        PlayerStats stats = new PlayerStats();
        stats.setPlayer(player);
        playerStatsRepository.save(stats);
    }

    public void updatePlayerStats(UUID playerId, long kills, long deaths,
                                   float damageDealt, float damageReceived,
                                   long mobsKilled, long blocksPlaced, long blocksMined,
                                   long itemsCrafted, long distanceWalked, long distanceSwum,
                                   long distanceFlown, long distanceSailed,
                                   long shotsFired, long shotsHit, long longestShotBlocks) {
        PlayerStats stats = playerStatsRepository.findByPlayerId(playerId).orElseThrow();
        stats.setKills(kills);
        stats.setDeaths(deaths);
        stats.setDamageDealt(damageDealt);
        stats.setDamageReceived(damageReceived);
        stats.setMobsKilled(mobsKilled);
        stats.setBlocksPlaced(blocksPlaced);
        stats.setBlocksMined(blocksMined);
        stats.setItemsCrafted(itemsCrafted);
        stats.setDistanceWalked(distanceWalked);
        stats.setDistanceSwum(distanceSwum);
        stats.setDistanceFlown(distanceFlown);
        stats.setDistanceSailed(distanceSailed);
        stats.setShotsFired(shotsFired);
        stats.setShotsHit(shotsHit);
        stats.setLongestShotBlocks(longestShotBlocks);
        playerStatsRepository.save(stats);
    }

    // ── Events ───────────────────────────────────────────────

    public List<EventLog> getRecentEvents() {
        return eventLogRepository.findTop20ByOrderByOccurredAtDesc();
    }

    public void createEvent(EventLog.EventType type, String description, UUID playerId) {
        EventLog event = new EventLog();
        event.setType(type);
        event.setDescription(description);
        event.setOccurredAt(Instant.now());
        if (playerId != null) {
            event.setPlayer(playerRepository.findById(playerId).orElse(null));
        }
        eventLogRepository.save(event);
    }

    // ── Clans ────────────────────────────────────────────────

    public List<Clan> getAllClans() {
        return clanRepository.findAll();
    }

    public Clan getClan(UUID id) {
        return clanRepository.findById(id).orElseThrow();
    }

    public List<Player> getClanMembers(UUID clanId) {
        return playerRepository.findAll().stream()
                .filter(p -> p.getClan() != null && p.getClan().getId().equals(clanId))
                .toList();
    }

    public List<Player> getPlayersNotInClan(UUID clanId) {
        return playerRepository.findAll().stream()
                .filter(p -> p.getClan() == null || !p.getClan().getId().equals(clanId))
                .toList();
    }

    public long getClanMemberCount(UUID clanId) {
        return playerRepository.findAll().stream()
                .filter(p -> p.getClan() != null && p.getClan().getId().equals(clanId))
                .count();
    }

    public void createClan(String name, String tag, UUID leaderId, boolean friendlyFireEnabled) {
        Clan clan = new Clan();
        clan.setName(name);
        clan.setTag(tag);
        clan.setLeader(playerRepository.findById(leaderId).orElseThrow());
        clan.setFriendlyFireEnabled(friendlyFireEnabled);
        clan.setCreatedAt(Instant.now());
        clanRepository.save(clan);
    }

    public void updateClan(UUID clanId, String name, String tag, UUID leaderId, boolean friendlyFireEnabled) {
        Clan clan = clanRepository.findById(clanId).orElseThrow();
        clan.setName(name);
        clan.setTag(tag);
        clan.setLeader(playerRepository.findById(leaderId).orElseThrow());
        clan.setFriendlyFireEnabled(friendlyFireEnabled);
        clanRepository.save(clan);
    }

    public void addMemberToClan(UUID clanId, UUID playerId) {
        Player player = playerRepository.findById(playerId).orElseThrow();
        Clan clan = clanRepository.findById(clanId).orElseThrow();
        player.setClan(clan);
        playerRepository.save(player);
    }

    public void removeMemberFromClan(UUID playerId) {
        Player player = playerRepository.findById(playerId).orElseThrow();
        player.setClan(null);
        playerRepository.save(player);
    }
}
