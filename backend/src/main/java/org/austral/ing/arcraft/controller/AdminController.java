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
    public String editPlayer(@PathVariable UUID id, Model model) {
        model.addAttribute("player", adminService.getPlayer(id));
        model.addAttribute("stats", adminService.getPlayerStats(id));
        return "admin/player-edit";
    }

    @PostMapping("/players/{id}/edit")
    public String updatePlayerStats(@PathVariable UUID id,
                                    @RequestParam(required = false) Long kills,
                                    @RequestParam(required = false) Long deaths,
                                    @RequestParam(required = false) Float damageDealt,
                                    @RequestParam(required = false) Float damageReceived,
                                    @RequestParam(required = false) Long mobsKilled,
                                    @RequestParam(required = false) Long blocksPlaced,
                                    @RequestParam(required = false) Long blocksMined,
                                    @RequestParam(required = false) Long itemsCrafted,
                                    @RequestParam(required = false) Long distanceWalked,
                                    @RequestParam(required = false) Long distanceSwum,
                                    @RequestParam(required = false) Long distanceFlown,
                                    @RequestParam(required = false) Long distanceSailed,
                                    @RequestParam(required = false) Long shotsFired,
                                    @RequestParam(required = false) Long shotsHit,
                                    @RequestParam(required = false) Long longestShotBlocks) {
        adminService.updatePlayerStats(id,
                nz(kills), nz(deaths), nz(damageDealt), nz(damageReceived),
                nz(mobsKilled), nz(blocksPlaced), nz(blocksMined), nz(itemsCrafted),
                nz(distanceWalked), nz(distanceSwum), nz(distanceFlown), nz(distanceSailed),
                nz(shotsFired), nz(shotsHit), nz(longestShotBlocks));
        return "redirect:/admin/players";
    }

    private static long nz(Long v) { return v == null ? 0L : v; }
    private static float nz(Float v) { return v == null ? 0f : v; }

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
