package org.austral.ing.arcraft.service;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.Clan;
import org.austral.ing.arcraft.entity.ClanMessage;
import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.entity.PlayerStats;
import org.austral.ing.arcraft.repository.ClanMessageRepository;
import org.austral.ing.arcraft.repository.ClanRepository;
import org.austral.ing.arcraft.repository.PlayerRepository;
import org.austral.ing.arcraft.repository.PlayerStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanService {

    private final ClanRepository clanRepository;
    private final PlayerRepository playerRepository;
    private final PlayerStatsRepository playerStatsRepository;
    private final ClanMessageRepository clanMessageRepository;

    public List<Clan> getAllClans() {
        return clanRepository.findAll();
    }

    public Optional<Clan> findByTag(String tag) {
        return clanRepository.findByTag(tag);
    }

    public List<Player> getMembers(Clan clan) {
        return playerRepository.findByClan(clan);
    }

    public long getMemberCount(Clan clan) {
        return playerRepository.countByClan(clan);
    }

    public Optional<PlayerStats> getPlayerStats(Player player) {
        return playerStatsRepository.findByPlayer(player);
    }

    public List<ClanMessage> getRecentMessages(UUID clanId, int limit) {
        List<ClanMessage> all = clanMessageRepository.findByClanIdOrderBySentAtAsc(clanId);
        if (all.size() > limit) {
            return all.subList(all.size() - limit, all.size());
        }
        return all;
    }

    @Transactional
    public void postMessage(Clan clan, Player sender, String content) {
        ClanMessage msg = new ClanMessage();
        msg.setClan(clan);
        msg.setSender(sender);
        msg.setContent(content);
        msg.setSentAt(Instant.now());
        clanMessageRepository.save(msg);
    }

    @Transactional
    public void joinClan(Player player, Clan clan) {
        player.setClan(clan);
        playerRepository.save(player);
    }

    @Transactional
    public String leaveClan(Player player, Clan clan) {
        if (clan.getLeader().getId().equals(player.getId())) {
            return "The clan leader cannot leave the clan. Transfer leadership first.";
        }
        player.setClan(null);
        playerRepository.save(player);
        return null;
    }

    @Transactional
    public String kickMember(Player leader, Player target, Clan clan) {
        if (!clan.getLeader().getId().equals(leader.getId())) {
            return "Only the clan leader can remove members.";
        }
        if (target.getClan() == null || !target.getClan().getId().equals(clan.getId())) {
            return "That player is not a member of this clan.";
        }
        if (target.getId().equals(leader.getId())) {
            return "The leader cannot kick themselves.";
        }
        target.setClan(null);
        playerRepository.save(target);
        return null;
    }

    /** Aggregate stats for all members of a clan. Returns long[7]: kills, deaths, mobsKilled, blocksMined, blocksPlaced, itemsCrafted, totalDistance */
    public long[] getAggregateStats(List<Player> members) {
        long kills = 0, deaths = 0, mobsKilled = 0, blocksMined = 0, blocksPlaced = 0, itemsCrafted = 0, totalDistance = 0;
        for (Player member : members) {
            Optional<PlayerStats> opt = playerStatsRepository.findByPlayer(member);
            if (opt.isPresent()) {
                PlayerStats s = opt.get();
                kills += s.getKills();
                deaths += s.getDeaths();
                mobsKilled += s.getMobsKilled();
                blocksMined += s.getBlocksMined();
                blocksPlaced += s.getBlocksPlaced();
                itemsCrafted += s.getItemsCrafted();
                totalDistance += RankingsService.computeTotalDistance(s);
            }
        }
        return new long[]{kills, deaths, mobsKilled, blocksMined, blocksPlaced, itemsCrafted, totalDistance};
    }
}
