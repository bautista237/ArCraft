package org.austral.ing.arcraft.web.service;

import org.austral.ing.arcraft.web.dao.Daos;
import org.austral.ing.arcraft.web.model.StatsView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Titles shown next to a player's name: automatic rank titles (current #1 of each category)
 * merged with store-bought titles. {@link Title} is rendered by the layout's playerCell
 * fragment ({@code t.text} / {@code t.special}).
 */
public final class TitleService {

    public static final TitleService INSTANCE = new TitleService();

    /** A title chip; {@code special} rank titles are styled differently from bought ones. */
    public record Title(String text, boolean special) {}

    private record TopDef(String label, ToLongFunction<StatsView> metric) {}

    private static final List<TopDef> TOP_DEFS = List.of(
            new TopDef("Top Killer", StatsView::getKills),
            new TopDef("Most Deaths", StatsView::getDeaths),
            new TopDef("Explorer", RankingsService::computeTotalDistance),
            new TopDef("Top Miner", StatsView::getBlocksMined),
            new TopDef("Master Crafter", StatsView::getItemsCrafted),
            new TopDef("Top Builder", StatsView::getBlocksPlaced));

    private TitleService() {
    }

    /** username → its rank titles (only the current leader of each category, value > 0). */
    public Map<String, List<String>> computeTopTitles() {
        List<StatsView> all = Daos.allStatsWithPlayers();
        Map<String, List<String>> result = new HashMap<>();
        for (TopDef def : TOP_DEFS) {
            StatsView top = all.stream()
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
    public Map<String, List<Title>> getAllTitles() {
        Map<String, List<Title>> merged = new HashMap<>();
        computeTopTitles().forEach((user, labels) -> {
            List<Title> list = merged.computeIfAbsent(user, k -> new ArrayList<>());
            for (String l : labels) list.add(new Title(l, true));
        });
        StoreService.INSTANCE.getTitlesByUsername().forEach((user, t) ->
                merged.computeIfAbsent(user, k -> new ArrayList<>()).add(new Title(t, false)));
        return merged;
    }

    public List<Title> getTitles(String username) {
        return getAllTitles().getOrDefault(username, List.of());
    }
}
