package org.austral.ing.arcraft.controller;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.Clan;
import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.entity.PlayerStats;
import org.austral.ing.arcraft.repository.PlayerRepository;
import org.austral.ing.arcraft.service.ClanService;
import org.austral.ing.arcraft.service.RankingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.*;

@Controller
@RequiredArgsConstructor
public class ClanController {

    private final ClanService clanService;
    private final PlayerRepository playerRepository;

    @GetMapping("/clans")
    public String clansList(Model model) {
        List<Clan> clans = clanService.getAllClans();

        List<Map<String, Object>> clanRows = new ArrayList<>();
        for (Clan clan : clans) {
            Map<String, Object> row = new HashMap<>();
            row.put("clan", clan);
            List<Player> members = clanService.getMembers(clan);
            row.put("memberCount", members.size());
            long[] agg = clanService.getAggregateStats(members);
            row.put("totalKills", agg[0]);
            row.put("totalMobsKilled", agg[2]);
            row.put("totalBlocksMined", agg[3]);
            clanRows.add(row);
        }

        model.addAttribute("clanRows", clanRows);
        return "clans";
    }

    @GetMapping("/clans/{tag}")
    public String clanProfile(@PathVariable String tag, Model model, Principal principal,
                              RedirectAttributes redirectAttributes) {
        Optional<Clan> clanOpt = clanService.findByTag(tag);
        if (clanOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Clan no longer exists.");
            return "redirect:/clans";
        }
        Clan clan = clanOpt.get();

        List<Player> members = clanService.getMembers(clan);
        long[] agg = clanService.getAggregateStats(members);

        // Build member rows with stats
        List<Map<String, Object>> memberRows = new ArrayList<>();
        for (Player member : members) {
            Map<String, Object> row = new HashMap<>();
            row.put("player", member);
            Optional<PlayerStats> statsOpt = clanService.getPlayerStats(member);
            if (statsOpt.isPresent()) {
                PlayerStats s = statsOpt.get();
                row.put("kills", s.getKills());
                row.put("deaths", s.getDeaths());
                row.put("kdRatio", RankingsService.computeKdRatio(s));
                row.put("mobsKilled", s.getMobsKilled());
            } else {
                row.put("kills", 0L);
                row.put("deaths", 0L);
                row.put("kdRatio", 0.0);
                row.put("mobsKilled", 0L);
            }
            memberRows.add(row);
        }

        // Current user context for join/leave/chat
        Player currentPlayer = null;
        boolean isMember = false;
        boolean isAdmin = false;
        boolean hasNoClan = false;
        boolean isLeader = false;
        if (principal != null) {
            currentPlayer = playerRepository.findByUsername(principal.getName()).orElse(null);
            if (currentPlayer != null) {
                isAdmin = currentPlayer.isAdmin();
                isMember = currentPlayer.getClan() != null && currentPlayer.getClan().getId().equals(clan.getId());
                hasNoClan = currentPlayer.getClan() == null;
                isLeader = clan.getLeader() != null && clan.getLeader().getId().equals(currentPlayer.getId());
            }
        }

        model.addAttribute("clan", clan);
        model.addAttribute("memberRows", memberRows);
        model.addAttribute("memberCount", members.size());
        model.addAttribute("totalKills", agg[0]);
        model.addAttribute("totalDeaths", agg[1]);
        model.addAttribute("totalMobsKilled", agg[2]);
        model.addAttribute("totalBlocksMined", agg[3]);
        model.addAttribute("totalBlocksPlaced", agg[4]);
        model.addAttribute("totalItemsCrafted", agg[5]);
        model.addAttribute("totalDistance", agg[6]);
        if (isMember) {
            model.addAttribute("messages", clanService.getRecentMessages(clan.getId(), 50));
        }
        model.addAttribute("isMember", isMember);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("hasNoClan", hasNoClan);
        model.addAttribute("isLeader", isLeader);
        model.addAttribute("leaderId", clan.getLeader() != null ? clan.getLeader().getId() : null);

        return "clan-profile";
    }

    @PostMapping("/clans/{tag}/message")
    public String postMessage(@PathVariable String tag,
                              @RequestParam String content,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {
        Optional<Clan> clanOpt = clanService.findByTag(tag);
        if (clanOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Clan no longer exists.");
            return "redirect:/clans";
        }
        Clan clan = clanOpt.get();
        Player player = playerRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Player not found"));

        boolean isMember = player.getClan() != null && player.getClan().getId().equals(clan.getId());
        if (!isMember) {
            redirectAttributes.addFlashAttribute("error", "You must be a member of this clan to send messages.");
            return "redirect:/clans/" + tag;
        }

        if (content != null && !content.isBlank()) {
            clanService.postMessage(clan, player, content.trim());
        }
        return "redirect:/clans/" + tag;
    }

    @PostMapping("/clans/{tag}/join")
    public String joinClan(@PathVariable String tag,
                           Principal principal,
                           RedirectAttributes redirectAttributes) {
        Optional<Clan> clanOpt = clanService.findByTag(tag);
        if (clanOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Clan no longer exists.");
            return "redirect:/clans";
        }
        Clan clan = clanOpt.get();
        Player player = playerRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Player not found"));

        if (player.getClan() != null) {
            redirectAttributes.addFlashAttribute("error", "You are already in a clan.");
            return "redirect:/clans/" + tag;
        }

        clanService.joinClan(player, clan);
        redirectAttributes.addFlashAttribute("success", "You joined " + clan.getName() + "!");
        return "redirect:/clans/" + tag;
    }

    @PostMapping("/clans/{tag}/leave")
    public String leaveClan(@PathVariable String tag,
                            Principal principal,
                            RedirectAttributes redirectAttributes) {
        Optional<Clan> clanOpt = clanService.findByTag(tag);
        if (clanOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Clan no longer exists.");
            return "redirect:/clans";
        }
        Clan clan = clanOpt.get();
        Player player = playerRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Player not found"));

        String error = clanService.leaveClan(player, clan);
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
        } else {
            redirectAttributes.addFlashAttribute("success", "You left " + clan.getName() + ".");
        }
        return "redirect:/clans/" + tag;
    }

    @PostMapping("/clans/{tag}/kick")
    public String kickMember(@PathVariable String tag,
                             @RequestParam String username,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {
        Optional<Clan> clanOpt = clanService.findByTag(tag);
        if (clanOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Clan no longer exists.");
            return "redirect:/clans";
        }
        Clan clan = clanOpt.get();
        Player leader = playerRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Player not found"));
        Player target = playerRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Target player not found"));

        String error = clanService.kickMember(leader, target, clan);
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
        } else {
            redirectAttributes.addFlashAttribute("success", "Removed " + target.getUsername() + " from the clan.");
        }
        return "redirect:/clans/" + tag;
    }
}
