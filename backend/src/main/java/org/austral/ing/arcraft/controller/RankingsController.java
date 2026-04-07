package org.austral.ing.arcraft.controller;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.service.RankingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class RankingsController {

    private final RankingsService rankingsService;

    @GetMapping("/rankings")
    public String rankings(@RequestParam(defaultValue = "kills") String sort, Model model) {
        model.addAttribute("players", rankingsService.getSorted(sort));
        model.addAttribute("currentSort", sort);
        return "rankings";
    }
}
