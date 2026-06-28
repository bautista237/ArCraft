package org.austral.ing.arcraft.controller;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.CoinOrder;
import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.repository.PlayerRepository;
import org.austral.ing.arcraft.service.CoinPackage;
import org.austral.ing.arcraft.service.MercadoPagoService;
import org.austral.ing.arcraft.service.StoreService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;
    private final PlayerRepository playerRepository;
    private final MercadoPagoService mercadoPagoService;

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
        model.addAttribute("coinPackages", CoinPackage.all());
        model.addAttribute("mpEnabled", mercadoPagoService.isConfigured());
        model.addAttribute("currency", mercadoPagoService.getCurrency());
        return "store";
    }

    // ── Buy coins with real (test) money via MercadoPago ─────────────────────

    @PostMapping("/store/coins/checkout")
    public String checkout(@RequestParam String pack, Principal principal, RedirectAttributes ra) {
        Player player = current(principal);
        CoinPackage selected = CoinPackage.bySlug(pack);
        if (player == null || selected == null) {
            ra.addFlashAttribute("error", "Invalid coin package.");
            return "redirect:/store";
        }
        try {
            String checkoutUrl = mercadoPagoService.startCheckout(player, selected);
            return "redirect:" + checkoutUrl;
        } catch (MercadoPagoService.MercadoPagoException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/store";
        }
    }

    /** MercadoPago redirects the player's browser back here after payment. */
    @GetMapping("/store/coins/return")
    public String coinsReturn(@RequestParam(required = false) String payment_id,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) String merchant_order_id,
                              @RequestParam(required = false) String external_reference,
                              RedirectAttributes ra) {
        CoinOrder credited = null;
        if (payment_id != null) credited = mercadoPagoService.confirmByPayment(payment_id);
        if (credited == null && merchant_order_id != null) {
            credited = mercadoPagoService.confirmByMerchantOrder(merchant_order_id);
        }
        if (credited != null) {
            ra.addFlashAttribute("success", "Payment confirmed! " + credited.getCoins() + " coins added to your balance.");
        } else if ("approved".equalsIgnoreCase(status)) {
            ra.addFlashAttribute("success", "Payment received — your coins will appear shortly.");
        } else if ("pending".equalsIgnoreCase(status)) {
            ra.addFlashAttribute("error", "Your payment is pending. Coins will be added once it's approved.");
        } else {
            ra.addFlashAttribute("error", "Payment was not completed.");
        }
        return "redirect:/store";
    }

    /** MercadoPago server-to-server notification (no browser/session). Always answer 200. */
    @PostMapping("/store/coins/webhook")
    @ResponseBody
    public String webhook(@RequestParam(required = false) String type,
                          @RequestParam(required = false) String topic,
                          @RequestParam(name = "data.id", required = false) String dataId,
                          @RequestParam(required = false) String id) {
        String kind = type != null ? type : topic;
        String resourceId = dataId != null ? dataId : id;
        try {
            if ("payment".equals(kind)) {
                mercadoPagoService.confirmByPayment(resourceId);
            } else if ("merchant_order".equals(kind)) {
                mercadoPagoService.confirmByMerchantOrder(resourceId);
            }
        } catch (Exception ignored) {
            // Never fail the webhook — MercadoPago retries on non-200.
        }
        return "ok";
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
