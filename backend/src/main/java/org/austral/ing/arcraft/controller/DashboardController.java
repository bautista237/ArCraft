package org.austral.ing.arcraft.controller;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.service.DashboardService;
import org.austral.ing.arcraft.service.EventService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final EventService eventService;

    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("totalPlayers", dashboardService.getTotalPlayers());
        model.addAttribute("totalKills", dashboardService.getTotalKills());
        model.addAttribute("totalMobsKilled", dashboardService.getTotalMobsKilled());
        model.addAttribute("topKillers", dashboardService.getTop5Killers());
        model.addAttribute("recentEvents", dashboardService.getRecentEvents());
        model.addAttribute("banners", eventService.getActiveBanners());
        model.addAttribute("topItemsCrafted", dashboardService.getTopItemsCraftedGlobal(8));
        model.addAttribute("topBlocksMined", dashboardService.getTopBlocksMinedGlobal(8));
        model.addAttribute("topBlocksPlaced", dashboardService.getTopBlocksPlacedGlobal(8));
        model.addAttribute("topMobsKilled", dashboardService.getTopMobsKilledGlobal(8));
        return "dashboard";
    }
}
