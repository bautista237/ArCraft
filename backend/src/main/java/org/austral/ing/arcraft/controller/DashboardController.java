package org.austral.ing.arcraft.controller;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("totalPlayers", dashboardService.getTotalPlayers());
        model.addAttribute("totalKills", dashboardService.getTotalKills());
        model.addAttribute("totalMobsKilled", dashboardService.getTotalMobsKilled());
        model.addAttribute("topKillers", dashboardService.getTop5Killers());
        model.addAttribute("recentEvents", dashboardService.getRecentEvents());
        return "dashboard";
    }
}
