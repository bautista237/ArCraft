package org.austral.ing.arcraft.controller;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.entity.PlayerStats;
import org.austral.ing.arcraft.service.PlayerProfileService;
import org.austral.ing.arcraft.service.RankingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerProfileService profileService;

    @GetMapping("/players/{username}")
    public String playerProfile(@PathVariable String username, Model model) {
        Player player = profileService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Player not found: " + username));

        PlayerStats stats = profileService.getStats(player).orElse(null);

        model.addAttribute("player", player);
        model.addAttribute("stats", stats);
        model.addAttribute("kdRatio", stats != null ? RankingsService.computeKdRatio(stats) : 0);
        model.addAttribute("bowAccuracy", stats != null ? RankingsService.computeBowAccuracy(stats) : 0);
        model.addAttribute("totalDistance", stats != null ? RankingsService.computeTotalDistance(stats) : 0);
        model.addAttribute("topBlocksMined", profileService.getTopBlocksMined(player, 5));
        model.addAttribute("topBlocksPlaced", profileService.getTopBlocksPlaced(player, 5));
        model.addAttribute("topItemsCrafted", profileService.getTopItemsCrafted(player, 5));
        model.addAttribute("topMobsKilled", profileService.getTopMobsKilled(player, 5));
        model.addAttribute("pvpHistory", profileService.getRecentPvP(player));

        return "player";
    }
}
