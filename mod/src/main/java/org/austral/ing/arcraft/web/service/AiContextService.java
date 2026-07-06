package org.austral.ing.arcraft.web.service;

import org.austral.ing.arcraft.db.Database;
import org.austral.ing.arcraft.web.dao.Daos;
import org.austral.ing.arcraft.web.model.EventLogView;
import org.austral.ing.arcraft.web.model.PlayerView;
import org.austral.ing.arcraft.web.model.StatsView;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Builds a compact, human-readable snapshot of the ArCraft database that is fed to the AI as
 * grounding context. The dataset is small (a private server), so we can summarise the whole
 * thing in the prompt rather than letting the model run queries — keeping answers grounded in
 * real data and impossible to use for injection/DB access.
 */
public final class AiContextService {

    public static final AiContextService INSTANCE = new AiContextService();

    private AiContextService() {
    }

    public String buildContext() {
        StringBuilder sb = new StringBuilder();

        // Server
        sb.append("SERVER\n");
        Database.jdbi().useHandle(h -> h
                .createQuery("SELECT server_name, server_start_date, online_mode FROM server_config")
                .map((rs, c) -> {
                    String name = rs.getString("server_name");
                    sb.append("- name: ").append(name == null ? "ArCraft" : name).append('\n');
                    var start = Daos.instant(rs, "server_start_date");
                    if (start != null) {
                        sb.append("- days online: ").append(Duration.between(start, Instant.now()).toDays()).append('\n');
                    }
                    Object om = rs.getObject("online_mode");
                    if (om != null) {
                        sb.append("- mode: ").append(rs.getBoolean("online_mode") ? "premium/online" : "cracked/offline").append('\n');
                    }
                    return null;
                })
                .findFirst());
        long totalPlayers = Database.jdbi().withHandle(h ->
                h.createQuery("SELECT COUNT(*) FROM player").mapTo(Long.class).one());
        long totalClans = Database.jdbi().withHandle(h ->
                h.createQuery("SELECT COUNT(*) FROM clan").mapTo(Long.class).one());
        sb.append("- total players: ").append(totalPlayers).append('\n');
        sb.append("- total clans: ").append(totalClans).append("\n\n");

        // Players with stats
        sb.append("PLAYERS (username | kills | deaths | K/D | mobsKilled | blocksMined | blocksPlaced | itemsCrafted | coins | clan)\n");
        List<StatsView> stats = new java.util.ArrayList<>(Daos.allStatsWithPlayers());
        stats.sort(Comparator.comparingLong(StatsView::getKills).reversed());
        for (StatsView s : stats) {
            PlayerView p = s.getPlayer();
            if (p == null) continue;
            double kd = s.getDeaths() == 0 ? s.getKills() : (double) s.getKills() / s.getDeaths();
            sb.append("- ").append(p.getUsername())
              .append(" | ").append(s.getKills())
              .append(" | ").append(s.getDeaths())
              .append(" | ").append(String.format("%.2f", kd))
              .append(" | ").append(s.getMobsKilled())
              .append(" | ").append(s.getBlocksMined())
              .append(" | ").append(s.getBlocksPlaced())
              .append(" | ").append(s.getItemsCrafted())
              .append(" | ").append(p.getCoins())
              .append(" | ").append(p.getClan() == null ? "-" : p.getClan().getName())
              .append('\n');
        }
        sb.append('\n');

        // Clans
        sb.append("CLANS (name [tag] | leader)\n");
        for (var clan : ClanService.INSTANCE.getAllClans()) {
            sb.append("- ").append(clan.getName()).append(" [").append(clan.getTag()).append("]")
              .append(" | leader: ").append(clan.getLeader() == null ? "-" : clan.getLeader().getUsername())
              .append('\n');
        }
        sb.append('\n');

        // Recent events
        sb.append("RECENT EVENTS (newest first)\n");
        for (EventLogView e : Daos.recentEvents(20)) {
            sb.append("- [").append(e.getType()).append("] ").append(e.getDescription()).append('\n');
        }

        return sb.toString();
    }
}
