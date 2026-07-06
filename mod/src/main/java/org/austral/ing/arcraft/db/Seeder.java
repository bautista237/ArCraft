package org.austral.ing.arcraft.db;

import com.mojang.logging.LogUtils;
import org.austral.ing.arcraft.ArcraftConfig;
import org.jdbi.v3.core.Handle;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * First-boot seeding (idempotent, same rules as the old Spring DataSeeder):
 * <ul>
 *   <li>baseline: the {@code admin/admin} account, the store catalog, a welcome banner and a
 *       server_config row — only when the corresponding table is empty;</li>
 *   <li>demo world (6 fake players, clans, PvP fights, breakdowns, timeline) — only when
 *       {@code general.seedDemoData} is enabled in the TOML AND the server has no real players.</li>
 * </ul>
 */
public final class Seeder {

    private static final Logger LOGGER = LogUtils.getLogger();

    private Seeder() {
    }

    public static void run() {
        Database.jdbi().useHandle(h -> {
            baseline(h);
            if (ArcraftConfig.SEED_DEMO_DATA.get() && count(h, "player") <= 1) {
                LOGGER.info("[ArCraft] Seeding demo world (general.seedDemoData = true)");
                demoWorld(h);
            }
        });
    }

    private static long count(Handle h, String table) {
        return h.createQuery("SELECT COUNT(*) FROM " + table).mapTo(Long.class).one();
    }

    // ── Baseline ─────────────────────────────────────────────────────────────

    private static void baseline(Handle h) {
        if (count(h, "player") == 0) {
            UUID adminId = insertPlayer(h, "admin", BCrypt.hashpw("admin", BCrypt.gensalt()), true, 1000);
            insertStats(h, adminId);
            LOGGER.info("[ArCraft] Seeded default admin account (admin/admin) — change its password!");
        }
        if (count(h, "store_item") == 0) {
            storeItem(h, "Dragon Slayer", "Title for those who've felled the Ender Dragon.", 500, "TITLE", null);
            storeItem(h, "Veteran", "A title marking a seasoned player.", 250, "TITLE", null);
            storeItem(h, "Sparkle Trail", "Trail of green sparkles as you walk.", 150, "COSMETIC", "TRAIL_SPARKLE");
            storeItem(h, "Flame Trail", "Leave a blazing trail of fire.", 200, "COSMETIC", "TRAIL_FLAME");
            storeItem(h, "Soul Trail", "Eerie blue soul flames follow you.", 220, "COSMETIC", "TRAIL_SOUL");
            storeItem(h, "Heart Trail", "Spread the love with floating hearts.", 180, "COSMETIC", "TRAIL_HEART");
            storeItem(h, "Enchanter's Trail", "Glowing enchantment glyphs swirl around you.", 260, "COSMETIC", "TRAIL_ENCHANT");
            storeItem(h, "Starlight Trail", "Sparkling white star dust.", 240, "COSMETIC", "TRAIL_STARLIGHT");
            storeItem(h, "Portal Trail", "Mysterious purple portal particles.", 230, "COSMETIC", "TRAIL_PORTAL");
            storeItem(h, "Frost Trail", "Leave a flurry of snowflakes.", 190, "COSMETIC", "TRAIL_SNOW");
            storeItem(h, "Melody Trail", "Colourful music notes trail behind you.", 210, "COSMETIC", "TRAIL_NOTE");
            storeItem(h, "Cherry Blossom Trail", "Drifting pink cherry petals.", 280, "COSMETIC", "TRAIL_CHERRY");
            storeItem(h, "Totem Trail", "Radiant totem sparkles.", 300, "COSMETIC", "TRAIL_TOTEM");
            storeItem(h, "Smoke Trail", "A wisp of campfire smoke.", 160, "COSMETIC", "TRAIL_SMOKE");
            storeItem(h, "Critical Trail", "Sharp critical-hit stars.", 170, "COSMETIC", "TRAIL_CRIT");
            storeItem(h, "Glowing Aura", "Glow so others can see you (visible to others / in F5).", 200, "COSMETIC", "GLOW");
        }
        if (count(h, "event") == 0) {
            h.createUpdate("""
                            INSERT INTO event (id, title, description, start_date, end_date, created_at,
                                               reminder_sent, remind_during, before_reminder_sent, during_reminder_sent)
                            VALUES (:id, :t, :d, :s, :e, :c, FALSE, FALSE, FALSE, FALSE)
                            """)
                    .bind("id", UUID.randomUUID())
                    .bind("t", "Welcome to ArCraft!")
                    .bind("d", "The server is live. Explore, build, and climb the rankings.")
                    .bind("s", Instant.now().minus(1, ChronoUnit.DAYS))
                    .bind("e", Instant.now().plus(14, ChronoUnit.DAYS))
                    .bind("c", Instant.now())
                    .execute();
        }
        if (count(h, "server_config") == 0) {
            h.createUpdate("INSERT INTO server_config (id, server_name, server_start_date) VALUES (:id, 'ArCraft', :s)")
                    .bind("id", UUID.randomUUID())
                    .bind("s", Instant.now())
                    .execute();
        }
    }

    // ── Demo world ───────────────────────────────────────────────────────────

    /** kills, deaths, pvpDeaths, mobs, mined, placed, crafted, walked, flown, shotsFired,
     *  shotsHit, dmgDealt, dmgReceived, longestShot */
    private record StatSet(long kills, long deaths, long pvpDeaths, long mobs, long mined, long placed,
                           long crafted, long walked, long flown, long shotsFired, long shotsHit,
                           float dmgDealt, float dmgReceived, long longestShot) {}

    private record DemoPlayer(UUID id, String name) {}

    private static void demoWorld(Handle h) {
        Random rng = new Random(42); // deterministic, reproducible demo data

        DemoPlayer steve = demoPlayer(h, "Steve", 900, new StatSet(120, 30, 18, 540, 22000, 5400, 9100, 2400, 1800, 380, 290, 1850, 1200, 64));
        DemoPlayer alex = demoPlayer(h, "Alex", 640, new StatSet(95, 41, 25, 410, 15000, 7200, 6300, 3100, 900, 220, 410, 1500, 1100, 58));
        DemoPlayer notch = demoPlayer(h, "Notch", 1500, new StatSet(210, 52, 33, 980, 41000, 9100, 14200, 5200, 2600, 510, 180, 3200, 2600, 102));
        DemoPlayer herobrine = demoPlayer(h, "Herobrine", 1200, new StatSet(180, 64, 47, 760, 33000, 4100, 11800, 1800, 4200, 640, 60, 2900, 1700, 88));
        DemoPlayer enderman = demoPlayer(h, "Enderman", 430, new StatSet(60, 78, 51, 240, 9000, 2200, 4100, 900, 6200, 980, 20, 700, 360, 41));
        DemoPlayer creeper = demoPlayer(h, "CreeperKing", 310, new StatSet(45, 90, 70, 180, 6000, 12000, 2900, 700, 300, 90, 8, 520, 210, 33));
        List<DemoPlayer> players = List.of(steve, alex, notch, herobrine, enderman, creeper);

        UUID netherLords = demoClan(h, "Nether Lords", "NL", steve.id());
        UUID enderGuild = demoClan(h, "Ender Guild", "EG", notch.id());
        assignClan(h, steve.id(), netherLords);
        assignClan(h, alex.id(), netherLords);
        assignClan(h, creeper.id(), netherLords);
        assignClan(h, notch.id(), enderGuild);
        assignClan(h, herobrine.id(), enderGuild);
        assignClan(h, enderman.id(), enderGuild);

        for (DemoPlayer p : players) {
            block(h, p.id(), "minecraft:stone", 4000 + rng.nextInt(8000), 200 + rng.nextInt(800));
            block(h, p.id(), "minecraft:diamond_ore", 40 + rng.nextInt(120), 0);
            block(h, p.id(), "minecraft:oak_log", 600 + rng.nextInt(1500), 0);
            block(h, p.id(), "minecraft:dirt", 1200 + rng.nextInt(3000), 1500 + rng.nextInt(4000));
            item(h, p.id(), "minecraft:torch", 300 + rng.nextInt(900));
            item(h, p.id(), "minecraft:diamond_sword", 1 + rng.nextInt(4));
            item(h, p.id(), "minecraft:bread", 120 + rng.nextInt(400));
            item(h, p.id(), "minecraft:bow", rng.nextInt(3));
            mob(h, p.id(), "minecraft:zombie", 80 + rng.nextInt(300));
            mob(h, p.id(), "minecraft:skeleton", 60 + rng.nextInt(250));
            mob(h, p.id(), "minecraft:creeper", 20 + rng.nextInt(120));
        }
        mob(h, notch.id(), "minecraft:ender_dragon", 2);
        mob(h, herobrine.id(), "minecraft:wither", 3);
        mob(h, steve.id(), "minecraft:warden", 1);

        // PvP encounters between rivals — several hits each so the Combat Breakdown,
        // weapon stats, head-to-head records and nemesis/victim analysis are populated.
        String[] weapons = {"minecraft:diamond_sword", "minecraft:netherite_sword", "minecraft:iron_axe",
                "minecraft:bow", "minecraft:trident"};
        record Rivalry(DemoPlayer a, DemoPlayer b, int fights) {}
        List<Rivalry> rivalries = List.of(
                new Rivalry(steve, alex, 4),
                new Rivalry(notch, herobrine, 3),
                new Rivalry(herobrine, steve, 2),
                new Rivalry(enderman, creeper, 3),
                new Rivalry(notch, steve, 2),
                new Rivalry(alex, enderman, 2));
        int fightIdx = 0;
        for (Rivalry r : rivalries) {
            for (int f = 0; f < r.fights(); f++) {
                // alternate winner occasionally so head-to-head isn't 100%
                DemoPlayer killer = (f % 3 == 2) ? r.b() : r.a();
                DemoPlayer victim = (killer == r.a()) ? r.b() : r.a();
                Instant start = Instant.now().minus(fightIdx + 1L, ChronoUnit.DAYS)
                        .minus(rng.nextInt(600), ChronoUnit.MINUTES);
                Instant end = start.plus(8 + rng.nextInt(40), ChronoUnit.SECONDS);
                UUID evId = UUID.randomUUID();
                h.createUpdate("""
                                INSERT INTO pvp_event (id, killer_id, victim_id, started_at, ended_at)
                                VALUES (:id, :k, :v, :s, :e)
                                """)
                        .bind("id", evId).bind("k", killer.id()).bind("v", victim.id())
                        .bind("s", start).bind("e", end).execute();

                int hits = 4 + rng.nextInt(6);
                Instant t = start;
                for (int hi = 0; hi < hits; hi++) {
                    boolean killerHits = rng.nextInt(100) < 65; // killer lands most blows
                    DemoPlayer attacker = killerHits ? killer : victim;
                    DemoPlayer recv = killerHits ? victim : killer;
                    t = t.plus(1 + rng.nextInt(5), ChronoUnit.SECONDS);
                    h.createUpdate("""
                                    INSERT INTO pvp_hit (id, pvp_event_id, attacker_id, victim_id, damage, weapon, hit_at)
                                    VALUES (:id, :ev, :a, :v, :d, :w, :t)
                                    """)
                            .bind("id", UUID.randomUUID()).bind("ev", evId)
                            .bind("a", attacker.id()).bind("v", recv.id())
                            .bind("d", 2.0f + rng.nextFloat() * 8.0f)
                            .bind("w", weapons[rng.nextInt(weapons.length)])
                            .bind("t", t).execute();
                }
                eventLog(h, "PVP_KILL", killer.name() + " defeated " + victim.name()
                        + " in a " + ChronoUnit.SECONDS.between(start, end) + "s duel", killer.id(), end);
                fightIdx++;
            }
        }

        eventLog(h, "BOSS_KILL", "Notch slew the Ender Dragon", notch.id(), Instant.now().minus(20, ChronoUnit.DAYS));
        eventLog(h, "BOSS_KILL", "Herobrine defeated the Wither", herobrine.id(), Instant.now().minus(12, ChronoUnit.DAYS));
        eventLog(h, "ACHIEVEMENT", "Steve reached 100 PvP kills", steve.id(), Instant.now().minus(6, ChronoUnit.DAYS));
        eventLog(h, "SERVER_MILESTONE", "The server passed 1,000,000 blocks mined", null, Instant.now().minus(3, ChronoUnit.DAYS));
        eventLog(h, "PLAYER_DEATH", "CreeperKing was blown up by a creeper (the irony)", creeper.id(), Instant.now().minus(1, ChronoUnit.DAYS));
    }

    // ── insert helpers ───────────────────────────────────────────────────────

    private static UUID insertPlayer(Handle h, String username, String hash, boolean admin, long coins) {
        UUID id = UUID.randomUUID();
        h.createUpdate("""
                        INSERT INTO player (id, username, password_hash, is_admin, coins,
                                            email_verified, verification_sent, created_at)
                        VALUES (:id, :u, :hash, :admin, :coins, FALSE, FALSE, :c)
                        """)
                .bind("id", id).bind("u", username).bind("hash", hash)
                .bind("admin", admin).bind("coins", coins).bind("c", Instant.now())
                .execute();
        return id;
    }

    private static void insertStats(Handle h, UUID playerId) {
        h.createUpdate("INSERT INTO player_stats (id, player_id) VALUES (:id, :pid)")
                .bind("id", UUID.randomUUID()).bind("pid", playerId).execute();
    }

    private static DemoPlayer demoPlayer(Handle h, String name, long coins, StatSet s) {
        UUID id = insertPlayer(h, name, BCrypt.hashpw("demo", BCrypt.gensalt()), false, coins);
        h.createUpdate("""
                        INSERT INTO player_stats (id, player_id, kills, deaths, pvp_deaths, mobs_killed,
                            blocks_mined, blocks_placed, items_crafted, distance_walked, distance_flown,
                            shots_fired, shots_hit, damage_dealt, damage_received,
                            pvp_damage_dealt, pvp_damage_received, longest_shot_blocks)
                        VALUES (:id, :pid, :k, :d, :pd, :m, :bm, :bp, :ic, :dw, :df, :sf, :sh, :dd, :dr, :pdd, :pdr, :ls)
                        """)
                .bind("id", UUID.randomUUID()).bind("pid", id)
                .bind("k", s.kills()).bind("d", s.deaths()).bind("pd", s.pvpDeaths()).bind("m", s.mobs())
                .bind("bm", s.mined()).bind("bp", s.placed()).bind("ic", s.crafted())
                .bind("dw", s.walked()).bind("df", s.flown())
                .bind("sf", s.shotsFired()).bind("sh", s.shotsHit())
                .bind("dd", s.dmgDealt()).bind("dr", s.dmgReceived())
                .bind("pdd", s.dmgDealt() * 0.4f).bind("pdr", s.dmgReceived() * 0.4f)
                .bind("ls", s.longestShot())
                .execute();
        return new DemoPlayer(id, name);
    }

    private static UUID demoClan(Handle h, String name, String tag, UUID leaderId) {
        UUID id = UUID.randomUUID();
        h.createUpdate("""
                        INSERT INTO clan (id, name, tag, leader_id, friendly_fire_enabled, created_at)
                        VALUES (:id, :n, :t, :l, FALSE, :c)
                        """)
                .bind("id", id).bind("n", name).bind("t", tag).bind("l", leaderId).bind("c", Instant.now())
                .execute();
        return id;
    }

    private static void assignClan(Handle h, UUID playerId, UUID clanId) {
        h.createUpdate("UPDATE player SET clan_id = :c WHERE id = :p")
                .bind("c", clanId).bind("p", playerId).execute();
    }

    private static void block(Handle h, UUID pid, String type, long mined, long placed) {
        h.createUpdate("INSERT INTO block_stat_entry (id, player_id, block_type, mined, placed) VALUES (:id, :p, :t, :m, :pl)")
                .bind("id", UUID.randomUUID()).bind("p", pid).bind("t", type).bind("m", mined).bind("pl", placed)
                .execute();
    }

    private static void item(Handle h, UUID pid, String type, long count) {
        h.createUpdate("INSERT INTO item_stat_entry (id, player_id, item_type, count) VALUES (:id, :p, :t, :c)")
                .bind("id", UUID.randomUUID()).bind("p", pid).bind("t", type).bind("c", count).execute();
    }

    private static void mob(Handle h, UUID pid, String type, long count) {
        h.createUpdate("INSERT INTO mob_stat_entry (id, player_id, mob_type, count) VALUES (:id, :p, :t, :c)")
                .bind("id", UUID.randomUUID()).bind("p", pid).bind("t", type).bind("c", count).execute();
    }

    private static void eventLog(Handle h, String type, String desc, UUID pid, Instant when) {
        h.createUpdate("INSERT INTO event_log (id, type, description, player_id, occurred_at) VALUES (:id, :t, :d, :p, :w)")
                .bind("id", UUID.randomUUID()).bind("t", type).bind("d", desc).bind("p", pid).bind("w", when)
                .execute();
    }

    private static void storeItem(Handle h, String name, String desc, long price, String cat, String effect) {
        h.createUpdate("""
                        INSERT INTO store_item (id, name, description, price, category, effect, created_at)
                        VALUES (:id, :n, :d, :p, :c, :e, :at)
                        """)
                .bind("id", UUID.randomUUID()).bind("n", name).bind("d", desc)
                .bind("p", price).bind("c", cat).bind("e", effect).bind("at", Instant.now())
                .execute();
    }
}
