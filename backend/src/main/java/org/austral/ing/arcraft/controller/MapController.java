package org.austral.ing.arcraft.controller;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.repository.PlayerRepository;
import org.austral.ing.arcraft.service.MapService;
import org.austral.ing.arcraft.service.MapService.ChunkVisitDTO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/map")
@RequiredArgsConstructor
public class MapController {

    private final MapService mapService;
    private final PlayerRepository playerRepository;

    private static final String DEFAULT_DIM = "minecraft:overworld";

    @GetMapping
    public String globalMap(
            @RequestParam(defaultValue = DEFAULT_DIM) String dimension,
            Model model) {
        model.addAttribute("currentDimension", dimension);
        model.addAttribute("isPersonal", false);
        model.addAttribute("player", null);
        return "map";
    }

    @GetMapping("/player/{username}")
    public String playerMap(
            @PathVariable String username,
            @RequestParam(defaultValue = DEFAULT_DIM) String dimension,
            Model model,
            RedirectAttributes redirectAttributes) {
        Optional<Player> playerOpt = playerRepository.findByUsername(username);
        if (playerOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Player '" + username + "' not found.");
            return "redirect:/map";
        }
        Player player = playerOpt.get();
        model.addAttribute("player", player);
        model.addAttribute("currentDimension", dimension);
        model.addAttribute("isPersonal", true);
        model.addAttribute("chunksExplored", mapService.countChunksByPlayerAndDimension(player, dimension));
        return "map";
    }

    @GetMapping("/data")
    @ResponseBody
    public List<ChunkVisitDTO> mapData(
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = DEFAULT_DIM) String dimension) {
        if (username != null && !username.isBlank()) {
            Optional<Player> playerOpt = playerRepository.findByUsername(username);
            if (playerOpt.isEmpty()) return List.of();
            return mapService.getChunksAsDTO(playerOpt.get(), dimension);
        }
        return mapService.getGlobalMapData(dimension).chunks();
    }
}
