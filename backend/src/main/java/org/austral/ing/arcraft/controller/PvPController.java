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

    /** Per-fighter combat summary for the encounter. */
    public record FighterStats(Player player, long hits, float totalDamage,
                               float biggestHit, float avgHit, float dps) {}

    /** Weapon usage across the whole encounter. */
    public record WeaponStat(String name, String icon, boolean hasIcon, long hits, float totalDamage) {}

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

        // ── Combat breakdown: per-fighter totals + weapon usage ──
        java.util.Map<UUID, float[]> dmgByAttacker = new java.util.LinkedHashMap<>(); // [hits, total, biggest]
        java.util.Map<UUID, Player> attackerRef = new java.util.HashMap<>();
        java.util.Map<String, WeaponStat> weapons = new java.util.LinkedHashMap<>();
        float totalDamage = 0;
        for (HitView h : hitViews) {
            totalDamage += h.damage();
            if (h.attacker() != null) {
                attackerRef.putIfAbsent(h.attacker().getId(), h.attacker());
                float[] agg = dmgByAttacker.computeIfAbsent(h.attacker().getId(), k -> new float[3]);
                agg[0] += 1;
                agg[1] += h.damage();
                agg[2] = Math.max(agg[2], h.damage());
            }
            WeaponStat prev = weapons.get(h.weaponName());
            long wHits = (prev == null ? 0 : prev.hits()) + 1;
            float wDmg = (prev == null ? 0 : prev.totalDamage()) + h.damage();
            weapons.put(h.weaponName(), new WeaponStat(h.weaponName(), h.weaponIcon(), h.hasWeaponIcon(), wHits, wDmg));
        }
        List<FighterStats> fighters = new ArrayList<>();
        for (var e : dmgByAttacker.entrySet()) {
            float[] agg = e.getValue();
            float avg = agg[0] > 0 ? agg[1] / agg[0] : 0;
            float dps = durationSec > 0 ? agg[1] / durationSec : agg[1];
            fighters.add(new FighterStats(attackerRef.get(e.getKey()), (long) agg[0], agg[1], agg[2], avg, dps));
        }
        fighters.sort((a, b) -> Float.compare(b.totalDamage(), a.totalDamage()));
        List<WeaponStat> weaponStats = new ArrayList<>(weapons.values());
        weaponStats.sort((a, b) -> Long.compare(b.hits(), a.hits()));

        model.addAttribute("event", ev);
        model.addAttribute("hits", hitViews);
        model.addAttribute("totalHits", hitViews.size());
        model.addAttribute("durationSec", durationSec);
        model.addAttribute("totalDamage", totalDamage);
        model.addAttribute("fighters", fighters);
        model.addAttribute("weaponStats", weaponStats);
        return "pvp-detail";
    }
}
