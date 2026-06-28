package org.austral.ing.arcraft.service;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.*;
import org.austral.ing.arcraft.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
@Service
@RequiredArgsConstructor
public class AiContextService {

    private final PlayerRepository playerRepository;
    private final PlayerStatsRepository playerStatsRepository;
    private final ClanRepository clanRepository;
    private final EventLogRepository eventLogRepository;
    private final ServerConfigRepository serverConfigRepository;

    @Transactional(readOnly = true)
    public String buildContext() {
        StringBuilder sb = new StringBuilder();

        // Server
        ServerConfig cfg = serverConfigRepository.findAll().stream().findFirst().orElse(null);
        sb.append("SERVER\n");
        if (cfg != null) {
            sb.append("- name: ").append(cfg.getServerName() == null ? "ArCraft" : cfg.getServerName()).append('\n');
            if (cfg.getServerStartDate() != null) {
                long days = Duration.between(cfg.getServerStartDate(), Instant.now()).toDays();
                sb.append("- days online: ").append(days).append('\n');
            }
            if (cfg.getOnlineMode() != null) {
                sb.append("- mode: ").append(cfg.getOnlineMode() ? "premium/online" : "cracked/offline").append('\n');
            }
        }
        sb.append("- total players: ").append(playerRepository.count()).append('\n');
        sb.append("- total clans: ").append(clanRepository.count()).append("\n\n");

        // Players with stats
        sb.append("PLAYERS (username | kills | deaths | K/D | mobsKilled | blocksMined | blocksPlaced | itemsCrafted | coins | clan)\n");
        List<PlayerStats> stats = playerStatsRepository.findAll();
        stats.sort(Comparator.comparingLong(PlayerStats::getKills).reversed());
        for (PlayerStats s : stats) {
            Player p = s.getPlayer();
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
        for (Clan c : clanRepository.findAll()) {
            sb.append("- ").append(c.getName()).append(" [").append(c.getTag()).append("]")
              .append(" | leader: ").append(c.getLeader() == null ? "-" : c.getLeader().getUsername())
              .append('\n');
        }
        sb.append('\n');

        // Recent events
        sb.append("RECENT EVENTS (newest first)\n");
        for (EventLog e : eventLogRepository.findTop20ByOrderByOccurredAtDesc()) {
            sb.append("- [").append(e.getType()).append("] ").append(e.getDescription()).append('\n');
        }

        return sb.toString();
    }
}
