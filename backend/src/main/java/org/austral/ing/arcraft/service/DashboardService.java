package org.austral.ing.arcraft.service;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.BlockStatEntry;
import org.austral.ing.arcraft.entity.EventLog;
import org.austral.ing.arcraft.entity.ItemStatEntry;
import org.austral.ing.arcraft.entity.MobStatEntry;
import org.austral.ing.arcraft.entity.PlayerStats;
import org.austral.ing.arcraft.repository.BlockStatEntryRepository;
import org.austral.ing.arcraft.repository.EventLogRepository;
import org.austral.ing.arcraft.repository.ItemStatEntryRepository;
import org.austral.ing.arcraft.repository.MobStatEntryRepository;
import org.austral.ing.arcraft.repository.PlayerRepository;
import org.austral.ing.arcraft.repository.PlayerStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final PlayerRepository playerRepository;
    private final PlayerStatsRepository playerStatsRepository;
    private final EventLogRepository eventLogRepository;
    private final ItemStatEntryRepository itemStatEntryRepository;
    private final BlockStatEntryRepository blockStatEntryRepository;
    private final MobStatEntryRepository mobStatEntryRepository;
    private final IconService iconService;

    private enum Kind { ITEM, BLOCK, MOB }

    /** A server-wide aggregate row: pretty name, icon URL, total, and the icon's average colour. */
    public record StatSlice(String name, String iconUrl, long count, String color) {}

    public List<StatSlice> getTopItemsCraftedGlobal(int limit) {
        return aggregate(itemStatEntryRepository.findAll(), ItemStatEntry::getItemType,
                ItemStatEntry::getCount, Kind.ITEM, limit);
    }

    public List<StatSlice> getTopBlocksMinedGlobal(int limit) {
        return aggregate(blockStatEntryRepository.findAll(), BlockStatEntry::getBlockType,
                BlockStatEntry::getMined, Kind.BLOCK, limit);
    }

    public List<StatSlice> getTopBlocksPlacedGlobal(int limit) {
        return aggregate(blockStatEntryRepository.findAll(), BlockStatEntry::getBlockType,
                BlockStatEntry::getPlaced, Kind.BLOCK, limit);
    }

    public List<StatSlice> getTopMobsKilledGlobal(int limit) {
        return aggregate(mobStatEntryRepository.findAll(), MobStatEntry::getMobType,
                MobStatEntry::getCount, Kind.MOB, limit);
    }

    private <T> List<StatSlice> aggregate(List<T> rows, java.util.function.Function<T, String> typeFn,
                                          ToLongFunction<T> countFn, Kind kind, int limit) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (T row : rows) {
            long c = countFn.applyAsLong(row);
            if (c <= 0) continue;
            totals.merge(typeFn.apply(row), c, Long::sum);
        }
        return totals.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> {
                    String id = e.getKey();
                    String iconUrl = kind == Kind.MOB ? iconService.mobIcon(id) : iconService.icon(id);
                    String color = kind == Kind.MOB ? iconService.mobColor(id) : iconService.color(id);
                    return new StatSlice(iconService.pretty(id), iconUrl, e.getValue(), color);
                })
                .toList();
    }

    public long getTotalPlayers() {
        return playerRepository.count();
    }

    public long getTotalKills() {
        return playerStatsRepository.findAll().stream()
                .mapToLong(PlayerStats::getKills)
                .sum();
    }

    public long getTotalMobsKilled() {
        return playerStatsRepository.findAll().stream()
                .mapToLong(PlayerStats::getMobsKilled)
                .sum();
    }

    public List<PlayerStats> getTop5Killers() {
        List<PlayerStats> all = playerStatsRepository.findAllByOrderByKillsDesc();
        return all.size() > 5 ? all.subList(0, 5) : all;
    }

    public List<EventLog> getRecentEvents() {
        return eventLogRepository.findTop5ByOrderByOccurredAtDesc();
    }
}
