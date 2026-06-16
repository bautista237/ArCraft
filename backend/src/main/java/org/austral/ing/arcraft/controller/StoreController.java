package org.austral.ing.arcraft.controller;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.repository.PlayerRepository;
import org.austral.ing.arcraft.service.StoreService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;
    private final PlayerRepository playerRepository;

    private Player current(Principal principal) {
        return playerRepository.findByUsername(principal.getName()).orElse(null);
    }

    @GetMapping("/store")
    public String store(Model model, Principal principal) {
        Player player = current(principal);
        model.addAttribute("items", storeService.getAllItems());
        model.addAttribute("ownedItemIds", player != null ? storeService.getOwnedItemIds(player) : java.util.Set.of());
        model.addAttribute("coins", player != null ? player.getCoins() : 0L);
        model.addAttribute("purchases", player != null ? storeService.getPurchases(player) : java.util.List.of());
        model.addAttribute("equippedTitle", player != null ? storeService.getEquippedTitle(player) : null);
        return "store";
    }

    @PostMapping("/store/buy")
    public String buy(@RequestParam UUID itemId, Principal principal, RedirectAttributes ra) {
        Player player = current(principal);
        if (player == null) {
            ra.addFlashAttribute("error", "You must be logged in to buy.");
            return "redirect:/store";
        }
        String error = storeService.purchase(player, itemId);
        if (error != null) {
            ra.addFlashAttribute("error", error);
        } else {
            ra.addFlashAttribute("success", "Purchase complete!");
        }
        return "redirect:/store";
    }

    @PostMapping("/store/equip")
    public String equip(@RequestParam UUID purchaseId, Principal principal, RedirectAttributes ra) {
        Player player = current(principal);
        if (player == null) {
            ra.addFlashAttribute("error", "You must be logged in.");
            return "redirect:/store";
        }
        String error = storeService.equipTitle(player, purchaseId);
        if (error != null) {
            ra.addFlashAttribute("error", error);
        } else {
            ra.addFlashAttribute("success", "Title equipped.");
        }
        return "redirect:/store";
    }

    @PostMapping("/store/toggle")
    public String toggle(@RequestParam UUID purchaseId, Principal principal, RedirectAttributes ra) {
        Player player = current(principal);
        if (player == null) {
            ra.addFlashAttribute("error", "You must be logged in.");
            return "redirect:/store";
        }
        Boolean nowEquipped = storeService.toggleCosmetic(player, purchaseId);
        if (nowEquipped == null) {
            ra.addFlashAttribute("error", "That cosmetic could not be toggled.");
        } else {
            ra.addFlashAttribute("success", nowEquipped ? "Cosmetic equipped." : "Cosmetic unequipped.");
        }
        return "redirect:/store";
    }
}
