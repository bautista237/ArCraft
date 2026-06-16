package org.austral.ing.arcraft.controller;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.entity.PvPEvent;
import org.austral.ing.arcraft.entity.PvPHit;
import org.austral.ing.arcraft.repository.PvPEventRepository;
import org.austral.ing.arcraft.repository.PvPHitRepository;
import org.austral.ing.arcraft.service.IconService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class PvPController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final PvPEventRepository pvpEventRepository;
    private final PvPHitRepository pvpHitRepository;
    private final IconService iconService;

    /** A single hit, prepared for display: who hit whom, hearts of damage, and the weapon. */
    public record HitView(Player attacker, Player victim,
                          float damage, int fullHearts, boolean halfHeart,
                          String weaponName, String weaponIcon, boolean hasWeaponIcon,
                          String time) {}

    @GetMapping("/pvp/{eventId}")
    public String pvpEncounter(@PathVariable UUID eventId, Model model, RedirectAttributes ra) {
        Optional<PvPEvent> evOpt = pvpEventRepository.findById(eventId);
        if (evOpt.isEmpty()) {
            ra.addFlashAttribute("error", "That PvP encounter no longer exists.");
            return "redirect:/rankings";
        }
        PvPEvent ev = evOpt.get();
        List<PvPHit> hits = pvpHitRepository.findByPvpEventOrderByHitAtAsc(ev);

        List<HitView> hitViews = new ArrayList<>();
        for (PvPHit h : hits) {
            Player attacker = h.getAttacker();
            Player victim = h.getVictim();
            if (victim == null) { // legacy rows: derive the recipient from the encounter
                victim = (attacker != null && ev.getKiller() != null
                        && attacker.getId().equals(ev.getKiller().getId())) ? ev.getVictim() : ev.getKiller();
            }
            float dmg = Math.max(0, h.getDamage());
            int full = Math.min(20, (int) (dmg / 2));
            boolean half = (dmg - full * 2) >= 1f;
            String weaponId = h.getWeapon();
            boolean hasIcon = weaponId != null && !weaponId.isBlank() && !weaponId.contains("air");
            String weaponName = (weaponId == null || weaponId.isBlank() || weaponId.contains("air"))
                    ? "Fists" : iconService.pretty(weaponId);
            String weaponIcon = hasIcon ? iconService.icon(weaponId) : null;
            String time = h.getHitAt() != null ? TIME.format(h.getHitAt()) : "";
            hitViews.add(new HitView(attacker, victim, dmg, full, half, weaponName, weaponIcon, hasIcon, time));
        }

        long durationSec = (ev.getStartedAt() != null && ev.getEndedAt() != null)
                ? Math.max(0, java.time.Duration.between(ev.getStartedAt(), ev.getEndedAt()).getSeconds()) : 0;

        model.addAttribute("event", ev);
        model.addAttribute("hits", hitViews);
        model.addAttribute("totalHits", hitViews.size());
        model.addAttribute("durationSec", durationSec);
        return "pvp-detail";
    }
}
