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
}
