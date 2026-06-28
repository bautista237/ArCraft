package org.austral.ing.arcraft.service;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.*;
import org.austral.ing.arcraft.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerProfileService {

    private final PlayerRepository playerRepository;
    private final PlayerStatsRepository playerStatsRepository;
    private final BlockStatEntryRepository blockStatEntryRepository;
    private final ItemStatEntryRepository itemStatEntryRepository;
    private final MobStatEntryRepository mobStatEntryRepository;
    private final PvPEventRepository pvpEventRepository;

    public Optional<Player> findByUsername(String username) {
        return playerRepository.findByUsername(username);
    }

    public Optional<PlayerStats> getStats(Player player) {
        return playerStatsRepository.findByPlayer(player);
    }

    public List<BlockStatEntry> getTopBlocksMined(Player player, int limit) {
        return blockStatEntryRepository.findByPlayer(player).stream()
                .sorted(Comparator.comparingLong(BlockStatEntry::getMined).reversed())
                .limit(limit)
                .toList();
    }

    public List<BlockStatEntry> getTopBlocksPlaced(Player player, int limit) {
        return blockStatEntryRepository.findByPlayer(player).stream()
                .sorted(Comparator.comparingLong(BlockStatEntry::getPlaced).reversed())
                .limit(limit)
                .toList();
    }

    public List<ItemStatEntry> getTopItemsCrafted(Player player, int limit) {
        return itemStatEntryRepository.findByPlayer(player).stream()
                .sorted(Comparator.comparingLong(ItemStatEntry::getCount).reversed())
                .limit(limit)
                .toList();
    }

    public List<MobStatEntry> getTopMobsKilled(Player player, int limit) {
        return mobStatEntryRepository.findByPlayer(player).stream()
                .sorted(Comparator.comparingLong(MobStatEntry::getCount).reversed())
                .limit(limit)
                .toList();
    }

    public List<PvPEvent> getRecentPvP(Player player) {
        return pvpEventRepository.findByKillerOrVictimOrderByEndedAtDesc(player, player);
    }

    // ── PvP analytics ────────────────────────────────────────

    /** Head-to-head record against a single opponent. */
    public record Matchup(Player opponent, long killsAgainst, long deathsTo) {
        public long total() { return killsAgainst + deathsTo; }
        public double winRate() { return total() == 0 ? 0 : (double) killsAgainst / total() * 100.0; }
    }

    /** Aggregated PvP picture for a player's profile. */
    public record PvpAnalytics(
            long totalFights, long kills, long deaths, double winRate,
            Matchup nemesis,      // opponent who killed this player the most
            Matchup favoriteVictim, // opponent this player killed the most
            List<Matchup> matchups) {}

    public PvpAnalytics getPvpAnalytics(Player player) {
        List<PvPEvent> events = pvpEventRepository.findByKillerOrVictimOrderByEndedAtDesc(player, player);

        // opponentId -> [killsAgainst, deathsTo], plus a reference to the opponent Player
        java.util.Map<java.util.UUID, long[]> tally = new java.util.HashMap<>();
        java.util.Map<java.util.UUID, Player> opponents = new java.util.HashMap<>();
        long kills = 0, deaths = 0;
        for (PvPEvent ev : events) {
            Player killer = ev.getKiller();
            Player victim = ev.getVictim();
            if (killer == null || victim == null) continue;
            boolean iAmKiller = killer.getId().equals(player.getId());
            Player opp = iAmKiller ? victim : killer;
            if (opp == null || opp.getId().equals(player.getId())) continue;
            long[] rec = tally.computeIfAbsent(opp.getId(), k -> new long[2]);
            opponents.putIfAbsent(opp.getId(), opp);
            if (iAmKiller) { rec[0]++; kills++; } else { rec[1]++; deaths++; }
        }

        List<Matchup> matchups = new java.util.ArrayList<>();
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
