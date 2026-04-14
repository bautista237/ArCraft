package org.austral.ing.arcraft.controller;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.EventLog;
import org.austral.ing.arcraft.service.AdminService;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Long.class, new CustomNumberEditor(Long.class, true));
        binder.registerCustomEditor(Float.class, new CustomNumberEditor(Float.class, true));
        binder.registerCustomEditor(Double.class, new CustomNumberEditor(Double.class, true));
    }

    // ── Admin Home ───────────────────────────────────────────

    @GetMapping
    public String adminHome() {
        return "admin/index";
    }

    // ── Players ──────────────────────────────────────────────

    @GetMapping("/players")
    public String listPlayers(Model model) {
        model.addAttribute("players", adminService.getAllPlayers());
        return "admin/players";
    }

    @PostMapping("/players")
    public String createPlayer(@RequestParam String username,
                               @RequestParam String password,
                               @RequestParam(defaultValue = "false") boolean isAdmin,
                               RedirectAttributes redirectAttributes) {
        boolean created = adminService.createPlayer(username, password, isAdmin);
        if (!created) {
            redirectAttributes.addFlashAttribute("error",
                    "A player with username '" + username + "' already exists.");
        } else {
            redirectAttributes.addFlashAttribute("success",
                    "Player '" + username + "' created successfully.");
        }
        return "redirect:/admin/players";
    }

    @GetMapping("/players/{id}/edit")
    public String editPlayer(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        var playerOpt = adminService.findPlayer(id);
        if (playerOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "No se puede acceder a las estadísticas de un jugador inexistente.");
            return "redirect:/admin/players";
        }
        model.addAttribute("player", playerOpt.get());
        model.addAttribute("stats", adminService.findPlayerStats(id).orElse(new org.austral.ing.arcraft.entity.PlayerStats()));
        return "admin/player-edit";
    }

    @PostMapping("/players/{id}/edit")
    public String updatePlayerStats(@PathVariable UUID id,
                                    @RequestParam(required = false) Double kills,
                                    @RequestParam(required = false) Double deaths,
                                    @RequestParam(required = false) Float damageDealt,
                                    @RequestParam(required = false) Float damageReceived,
                                    @RequestParam(required = false) Double mobsKilled,
                                    @RequestParam(required = false) Double blocksPlaced,
                                    @RequestParam(required = false) Double blocksMined,
                                    @RequestParam(required = false) Double itemsCrafted,
                                    @RequestParam(required = false) Double distanceWalked,
                                    @RequestParam(required = false) Double distanceSwum,
                                    @RequestParam(required = false) Double distanceFlown,
                                    @RequestParam(required = false) Double distanceSailed,
                                    @RequestParam(required = false) Double shotsFired,
                                    @RequestParam(required = false) Double shotsHit,
                                    @RequestParam(required = false) Double longestShotBlocks) {
        adminService.updatePlayerStats(id,
                roundL(kills), roundL(deaths), roundF(damageDealt), roundF(damageReceived),
                roundL(mobsKilled), roundL(blocksPlaced), roundL(blocksMined), roundL(itemsCrafted),
                roundL(distanceWalked), roundL(distanceSwum), roundL(distanceFlown), roundL(distanceSailed),
                roundL(shotsFired), roundL(shotsHit), roundL(longestShotBlocks));
        return "redirect:/admin/players";
    }

    private static long roundL(Double v) { return v == null ? 0L : Math.round(v); }
    private static float roundF(Float v) { return v == null ? 0f : Math.round(v); }

    @PostMapping("/players/{id}/delete")
    public String deletePlayer(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        String error = adminService.deletePlayer(id);
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
            return "redirect:/admin/players/" + id + "/edit";
        }
        redirectAttributes.addFlashAttribute("success", "Player deleted successfully.");
        return "redirect:/admin/players";
    }

    // ── Events ───────────────────────────────────────────────

    @GetMapping("/events")
    public String listEvents(Model model) {
        model.addAttribute("events", adminService.getRecentEvents());
        model.addAttribute("players", adminService.getAllPlayers());
        model.addAttribute("eventTypes", EventLog.EventType.values());
        return "admin/events";
    }

    @PostMapping("/events")
    public String createEvent(@RequestParam EventLog.EventType type,
                              @RequestParam String description,
                              @RequestParam(required = false) UUID playerId) {
        adminService.createEvent(type, description, playerId);
        return "redirect:/admin/events";
    }

    // ── Clans ────────────────────────────────────────────────

    @GetMapping("/clans")
    public String listClans(Model model) {
        var clans = adminService.getAllClans();
        model.addAttribute("clans", clans);
        model.addAttribute("players", adminService.getAllPlayers());
        // build member count map
        var memberCounts = new java.util.HashMap<UUID, Long>();
        for (var clan : clans) {
            memberCounts.put(clan.getId(), adminService.getClanMemberCount(clan.getId()));
        }
        model.addAttribute("memberCounts", memberCounts);
        return "admin/clans";
    }

    @PostMapping("/clans")
    public String createClan(@RequestParam String name,
                             @RequestParam String tag,
                             @RequestParam UUID leaderId,
                             @RequestParam(defaultValue = "false") boolean friendlyFireEnabled,
                             RedirectAttributes redirectAttributes) {
        String error = adminService.createClan(name, tag, leaderId, friendlyFireEnabled);
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
        } else {
            redirectAttributes.addFlashAttribute("success", "Clan '" + name + "' created successfully.");
        }
        return "redirect:/admin/clans";
    }

    @PostMapping("/clans/{id}/delete")
    public String deleteClan(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        String error = adminService.deleteClan(id);
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
        } else {
            redirectAttributes.addFlashAttribute("success", "Clan deleted. All members have been removed from it.");
        }
        return "redirect:/admin/clans";
    }

    @GetMapping("/clans/{id}/edit")
    public String editClan(@PathVariable UUID id, Model model) {
        model.addAttribute("clan", adminService.getClan(id));
        model.addAttribute("members", adminService.getClanMembers(id));
        model.addAttribute("availablePlayers", adminService.getPlayersNotInClan(id));
        return "admin/clan-edit";
    }

    @PostMapping("/clans/{id}/edit")
    public String updateClan(@PathVariable UUID id,
                             @RequestParam String name,
                             @RequestParam String tag,
                             @RequestParam UUID leaderId,
                             @RequestParam(defaultValue = "false") boolean friendlyFireEnabled) {
        adminService.updateClan(id, name, tag, leaderId, friendlyFireEnabled);
        return "redirect:/admin/clans/" + id + "/edit";
    }

    @PostMapping("/clans/{id}/add-member")
    public String addMember(@PathVariable UUID id, @RequestParam UUID playerId) {
        adminService.addMemberToClan(id, playerId);
        return "redirect:/admin/clans/" + id + "/edit";
    }

    @PostMapping("/clans/{id}/remove-member")
    public String removeMember(@PathVariable UUID id, @RequestParam UUID playerId) {
        adminService.removeMemberFromClan(playerId);
        return "redirect:/admin/clans/" + id + "/edit";
    }
}
