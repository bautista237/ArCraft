package org.austral.ing.arcraft.service;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.entity.PlayerStats;
import org.austral.ing.arcraft.repository.PlayerStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.ToLongFunction;

/**
 * Produces the titles shown next to a player's name. Two sources are merged:
 * <ul>
 *   <li><b>Special rank titles</b> — automatically held by the current #1 in each category
 *       (Top Killer, Most Deaths, Explorer, Top Miner, Master Crafter, Top Builder).</li>
 *   <li><b>Store titles</b> — bought in the shop.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TitleService {

    private final PlayerStatsRepository playerStatsRepository;
    private final StoreService storeService;

    /** A title chip; {@code special} rank titles are styled differently from bought ones. */
    public record Title(String text, boolean special) {}

    private record TopDef(String label, ToLongFunction<PlayerStats> metric) {}

    private static final List<TopDef> TOP_DEFS = List.of(
            new TopDef("Top Killer", PlayerStats::getKills),
            new TopDef("Most Deaths", PlayerStats::getDeaths),
            new TopDef("Explorer", RankingsService::computeTotalDistance),
            new TopDef("Top Miner", PlayerStats::getBlocksMined),
            new TopDef("Master Crafter", PlayerStats::getItemsCrafted),
            new TopDef("Top Builder", PlayerStats::getBlocksPlaced)
    );

    /** username → its rank titles (only the current leader of each category, value > 0). */
    @Transactional(readOnly = true)
    public Map<String, List<String>> computeTopTitles() {
        List<PlayerStats> all = playerStatsRepository.findAll();
        Map<String, List<String>> result = new HashMap<>();
        for (TopDef def : TOP_DEFS) {
            PlayerStats top = all.stream()
                    .filter(s -> s.getPlayer() != null && def.metric().applyAsLong(s) > 0)
                    .max(Comparator.comparingLong(def.metric()))
                    .orElse(null);
            if (top != null) {
                result.computeIfAbsent(top.getPlayer().getUsername(), k -> new ArrayList<>()).add(def.label());
            }
        }
        return result;
    }

    /** username → all titles (rank titles first, then store title) for everyone who has any. */
    @Transactional(readOnly = true)
    public Map<String, List<Title>> getAllTitles() {
        Map<String, List<Title>> merged = new HashMap<>();
        computeTopTitles().forEach((user, labels) -> {
            List<Title> list = merged.computeIfAbsent(user, k -> new ArrayList<>());
            for (String l : labels) list.add(new Title(l, true));
        });
        storeService.getTitlesByUsername().forEach((user, t) ->
                merged.computeIfAbsent(user, k -> new ArrayList<>()).add(new Title(t, false)));
        return merged;
    }

    @Transactional(readOnly = true)
    public List<Title> getTitles(Player player) {
        return getAllTitles().getOrDefault(player.getUsername(), List.of());
    }
}
