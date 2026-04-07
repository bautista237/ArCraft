package org.austral.ing.arcraft.controller;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.EventLog;
import org.austral.ing.arcraft.service.AdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

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
                               @RequestParam(defaultValue = "false") boolean isAdmin) {
        adminService.createPlayer(username, password, isAdmin);
        return "redirect:/admin/players";
    }

    @GetMapping("/players/{id}/edit")
    public String editPlayer(@PathVariable UUID id, Model model) {
        model.addAttribute("player", adminService.getPlayer(id));
        model.addAttribute("stats", adminService.getPlayerStats(id));
        return "admin/player-edit";
    }

    @PostMapping("/players/{id}/edit")
    public String updatePlayerStats(@PathVariable UUID id,
                                    @RequestParam long kills,
                                    @RequestParam long deaths,
                                    @RequestParam float damageDealt,
                                    @RequestParam float damageReceived,
                                    @RequestParam long mobsKilled,
                                    @RequestParam long blocksPlaced,
                                    @RequestParam long blocksMined,
                                    @RequestParam long itemsCrafted,
                                    @RequestParam long distanceWalked,
                                    @RequestParam long distanceSwum,
                                    @RequestParam long distanceFlown,
                                    @RequestParam long distanceSailed,
                                    @RequestParam long shotsFired,
                                    @RequestParam long shotsHit,
                                    @RequestParam long longestShotBlocks) {
        adminService.updatePlayerStats(id, kills, deaths, damageDealt, damageReceived,
                mobsKilled, blocksPlaced, blocksMined, itemsCrafted,
                distanceWalked, distanceSwum, distanceFlown, distanceSailed,
                shotsFired, shotsHit, longestShotBlocks);
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
                             @RequestParam(defaultValue = "false") boolean friendlyFireEnabled) {
        adminService.createClan(name, tag, leaderId, friendlyFireEnabled);
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
