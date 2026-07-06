package org.austral.ing.arcraft.web.routes;

import io.javalin.Javalin;
import org.austral.ing.arcraft.db.Database;
import org.austral.ing.arcraft.web.Flash;
import org.austral.ing.arcraft.web.Renderer;
import org.austral.ing.arcraft.web.dao.Daos;
import org.austral.ing.arcraft.web.model.PlayerView;
import org.austral.ing.arcraft.web.model.PvPEventView;
import org.austral.ing.arcraft.web.service.IconService;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** PvP encounter detail: the full hit timeline + combat breakdown (ex PvPController). */
public final class PvPRoutes {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    /** A single hit, prepared for display: who hit whom, hearts of damage, and the weapon. */
    public record HitView(PlayerView attacker, PlayerView victim,
                          float damage, int fullHearts, boolean halfHeart,
                          String weaponName, String weaponIcon, boolean hasWeaponIcon,
                          String time) {}

    /** Per-fighter combat summary for the encounter. */
    public record FighterStats(PlayerView player, long hits, float totalDamage,
                               float biggestHit, float avgHit, float dps) {}

    /** Weapon usage across the whole encounter. */
    public record WeaponStat(String name, String icon, boolean hasIcon, long hits, float totalDamage) {}

    private record HitRow(PlayerView attacker, PlayerView victim, float damage, String weapon, Instant hitAt) {}

    private PvPRoutes() {
    }

    public static void register(Javalin app) {
        app.get("/pvp/{eventId}", ctx -> {
            UUID eventId;
            try {
                eventId = UUID.fromString(ctx.pathParam("eventId"));
            } catch (IllegalArgumentException e) {
                eventId = null;
            }
            PvPEventView ev = eventId == null ? null : findEvent(eventId);
            if (ev == null) {
                Flash.error(ctx, "That PvP encounter no longer exists.");
                ctx.redirect("/rankings");
                return;
            }

            IconService icons = IconService.INSTANCE;
            List<HitView> hitViews = new ArrayList<>();
            for (HitRow h : findHits(ev.getId())) {
                PlayerView attacker = h.attacker();
                PlayerView victim = h.victim();
                if (victim == null) { // legacy rows: derive the recipient from the encounter
                    victim = (attacker != null && ev.getKiller() != null
                            && attacker.getId().equals(ev.getKiller().getId())) ? ev.getVictim() : ev.getKiller();
                }
                float dmg = Math.max(0, h.damage());
                int full = Math.min(20, (int) (dmg / 2));
                boolean half = (dmg - full * 2) >= 1f;
                String weaponId = h.weapon();
                boolean hasIcon = weaponId != null && !weaponId.isBlank() && !weaponId.contains("air");
                String weaponName = (weaponId == null || weaponId.isBlank() || weaponId.contains("air"))
                        ? "Fists" : icons.pretty(weaponId);
                String weaponIcon = hasIcon ? icons.icon(weaponId) : null;
                String time = h.hitAt() != null ? TIME.format(h.hitAt()) : "";
                hitViews.add(new HitView(attacker, victim, dmg, full, half, weaponName, weaponIcon, hasIcon, time));
            }

            long durationSec = (ev.getStartedAt() != null && ev.getEndedAt() != null)
                    ? Math.max(0, Duration.between(ev.getStartedAt(), ev.getEndedAt()).getSeconds()) : 0;

            // ── Combat breakdown: per-fighter totals + weapon usage ──
            Map<UUID, float[]> dmgByAttacker = new LinkedHashMap<>(); // [hits, total, biggest]
            Map<UUID, PlayerView> attackerRef = new HashMap<>();
            Map<String, WeaponStat> weapons = new LinkedHashMap<>();
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

            Map<String, Object> m = new HashMap<>();
            m.put("event", ev);
            m.put("hits", hitViews);
            m.put("totalHits", hitViews.size());
            m.put("durationSec", durationSec);
            m.put("totalDamage", totalDamage);
            m.put("fighters", fighters);
            m.put("weaponStats", weaponStats);
            Renderer.render(ctx, "pvp-detail", m);
        });
    }

    private static PvPEventView findEvent(UUID id) {
        return Database.jdbi().withHandle(h -> h
                .createQuery("""
                        SELECT e.id e_id, e.started_at e_start, e.ended_at e_end, %s, %s
                        FROM pvp_event e
                        JOIN player k ON e.killer_id = k.id
                        JOIN player v ON e.victim_id = v.id
                        WHERE e.id = :id
                        """.formatted(Daos.playerCols("k"), Daos.playerCols("v")))
                .bind("id", id)
                .map((rs, c) -> {
                    PvPEventView ev = new PvPEventView(Daos.uuid(rs, "e_id"),
                            Daos.instant(rs, "e_start"), Daos.instant(rs, "e_end"));
                    ev.setKiller(Daos.mapPlayer(rs, "k"));
                    ev.setVictim(Daos.mapPlayer(rs, "v"));
                    return ev;
                })
                .findFirst().orElse(null));
    }

    private static List<HitRow> findHits(UUID eventId) {
        return Database.jdbi().withHandle(h -> h
                .createQuery("""
                        SELECT hit.damage h_dmg, hit.weapon h_weapon, hit.hit_at h_at, %s, %s
                        FROM pvp_hit hit
                        LEFT JOIN player a ON hit.attacker_id = a.id
                        LEFT JOIN player v ON hit.victim_id = v.id
                        WHERE hit.pvp_event_id = :eid
                        ORDER BY hit.hit_at ASC
                        """.formatted(Daos.playerCols("a"), Daos.playerCols("v")))
                .bind("eid", eventId)
                .map((rs, c) -> new HitRow(Daos.mapPlayer(rs, "a"), Daos.mapPlayer(rs, "v"),
                        rs.getFloat("h_dmg"), rs.getString("h_weapon"), Daos.instant(rs, "h_at")))
                .list());
    }
}
