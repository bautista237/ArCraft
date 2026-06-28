package org.austral.ing.arcraft.seed;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.*;
import org.austral.ing.arcraft.entity.EventLog.EventType;
import org.austral.ing.arcraft.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final PlayerRepository playerRepo;
    private final PlayerStatsRepository statsRepo;
    private final StoreItemRepository storeItemRepo;
    private final EventRepository eventRepo;
    private final ClanRepository clanRepo;
    private final PvPEventRepository pvpEventRepo;
    private final PvPHitRepository pvpHitRepo;
    private final BlockStatEntryRepository blockRepo;
    private final ItemStatEntryRepository itemRepo;
    private final MobStatEntryRepository mobRepo;
    private final EventLogRepository eventLogRepo;
    private final ServerConfigRepository serverConfigRepo;
    private final PasswordEncoder passwordEncoder;

    @Value("${arcraft.seed.demo-data:true}")
    private boolean seedDemoData;

    @Override
    @Transactional
    public void run(String... args) {
        if (playerRepo.count() == 0) {
            Player admin = new Player();
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode("admin"));
            admin.setAdmin(true);
            admin.setCoins(1000);
            playerRepo.save(admin);

            PlayerStats adminStats = new PlayerStats();
            adminStats.setPlayer(admin);
            statsRepo.save(adminStats);
        }


        if (storeItemRepo.count() == 0) {
            // Titles (shown next to the player's name on the web).
            storeItemRepo.save(item("Dragon Slayer", "Title for those who've felled the Ender Dragon.", 500, StoreItem.Category.TITLE, null));
            storeItemRepo.save(item("Veteran", "A title marking a seasoned player.", 250, StoreItem.Category.TITLE, null));
            // Cosmetic-only particle trails + glow — applied in-game while you're online.
            storeItemRepo.save(item("Sparkle Trail", "Trail of green sparkles as you walk.", 150, StoreItem.Category.COSMETIC, "TRAIL_SPARKLE"));
            storeItemRepo.save(item("Flame Trail", "Leave a blazing trail of fire.", 200, StoreItem.Category.COSMETIC, "TRAIL_FLAME"));
            storeItemRepo.save(item("Soul Trail", "Eerie blue soul flames follow you.", 220, StoreItem.Category.COSMETIC, "TRAIL_SOUL"));
            storeItemRepo.save(item("Heart Trail", "Spread the love with floating hearts.", 180, StoreItem.Category.COSMETIC, "TRAIL_HEART"));
            storeItemRepo.save(item("Enchanter's Trail", "Glowing enchantment glyphs swirl around you.", 260, StoreItem.Category.COSMETIC, "TRAIL_ENCHANT"));
            storeItemRepo.save(item("Starlight Trail", "Sparkling white star dust.", 240, StoreItem.Category.COSMETIC, "TRAIL_STARLIGHT"));
            storeItemRepo.save(item("Portal Trail", "Mysterious purple portal particles.", 230, StoreItem.Category.COSMETIC, "TRAIL_PORTAL"));
            storeItemRepo.save(item("Frost Trail", "Leave a flurry of snowflakes.", 190, StoreItem.Category.COSMETIC, "TRAIL_SNOW"));
            storeItemRepo.save(item("Melody Trail", "Colourful music notes trail behind you.", 210, StoreItem.Category.COSMETIC, "TRAIL_NOTE"));
            storeItemRepo.save(item("Cherry Blossom Trail", "Drifting pink cherry petals.", 280, StoreItem.Category.COSMETIC, "TRAIL_CHERRY"));
            storeItemRepo.save(item("Totem Trail", "Radiant totem sparkles.", 300, StoreItem.Category.COSMETIC, "TRAIL_TOTEM"));
            storeItemRepo.save(item("Smoke Trail", "A wisp of campfire smoke.", 160, StoreItem.Category.COSMETIC, "TRAIL_SMOKE"));
            storeItemRepo.save(item("Critical Trail", "Sharp critical-hit stars.", 170, StoreItem.Category.COSMETIC, "TRAIL_CRIT"));
            storeItemRepo.save(item("Glowing Aura", "Glow so others can see you (visible to others / in F5).", 200, StoreItem.Category.COSMETIC, "GLOW"));
        }

        if (eventRepo.count() == 0) {
            Event e = new Event();
            e.setTitle("Welcome to ArCraft!");
            e.setDescription("The server is live. Explore, build, and climb the rankings.");
            e.setStartDate(Instant.now().minus(1, ChronoUnit.DAYS));
            e.setEndDate(Instant.now().plus(14, ChronoUnit.DAYS));
            eventRepo.save(e);
        }

        if (serverConfigRepo.count() == 0) {
            ServerConfig cfg = new ServerConfig();
            cfg.setServerName("ArCraft");
            cfg.setServerStartDate(Instant.now().minus(42, ChronoUnit.DAYS));
            cfg.setOnlineMode(false);
            serverConfigRepo.save(cfg);
        }

        // Rich demo data so the dashboard, rankings, profiles, PvP pages and AI chat have
        // realistic content to show. Disable in prod with arcraft.seed.demo-data=false.
        if (seedDemoData && playerRepo.count() <= 1) {
            seedDemoWorld();
        }
    }

    // ── Demo world ───────────────────────────────────────────────────────────

    private void seedDemoWorld() {
        Random rng = new Random(42); // deterministic, reproducible demo data

        // Players with hand-tuned stats so rankings/profiles look believable.
        Player steve     = demoPlayer("Steve",     900, statSet(120, 30, 18, 540, 22000, 5400, 9100, 2400, 1800, 380, 290, 1850, 1200, 64));
        Player alex      = demoPlayer("Alex",       640, statSet( 95, 41, 25, 410, 15000, 7200, 6300, 3100,  900, 220, 410, 1500, 1100, 58));
        Player notch     = demoPlayer("Notch",     1500, statSet(210, 52, 33, 980, 41000, 9100, 14200, 5200, 2600, 510, 180, 3200, 2600, 102));
        Player herobrine = demoPlayer("Herobrine", 1200, statSet(180, 64, 47, 760, 33000, 4100, 11800, 1800, 4200, 640, 60, 2900, 1700, 88));
        Player enderman  = demoPlayer("Enderman",   430, statSet( 60, 78, 51, 240,  9000, 2200, 4100,  900, 6200, 980, 20, 700, 360, 41));
        Player creeper   = demoPlayer("CreeperKing",310, statSet( 45, 90, 70, 180,  6000, 12000, 2900, 700,  300, 90, 8, 520, 210, 33));
        List<Player> players = List.of(steve, alex, notch, herobrine, enderman, creeper);

        // Clans
        Clan netherLords = demoClan("Nether Lords", "NL", steve);
        Clan enderGuild  = demoClan("Ender Guild",  "EG", notch);
        assignClan(steve, netherLords); assignClan(alex, netherLords); assignClan(creeper, netherLords);
        assignClan(notch, enderGuild);  assignClan(herobrine, enderGuild); assignClan(enderman, enderGuild);

        // Block / item / mob breakdowns
        for (Player p : players) {
            block(p, "minecraft:stone", 4000 + rng.nextInt(8000), 200 + rng.nextInt(800));
            block(p, "minecraft:diamond_ore", 40 + rng.nextInt(120), 0);
            block(p, "minecraft:oak_log", 600 + rng.nextInt(1500), 0);
            block(p, "minecraft:dirt", 1200 + rng.nextInt(3000), 1500 + rng.nextInt(4000));
            item(p, "minecraft:torch", 300 + rng.nextInt(900));
            item(p, "minecraft:diamond_sword", 1 + rng.nextInt(4));
            item(p, "minecraft:bread", 120 + rng.nextInt(400));
            item(p, "minecraft:bow", rng.nextInt(3));
            mob(p, "minecraft:zombie", 80 + rng.nextInt(300));
            mob(p, "minecraft:skeleton", 60 + rng.nextInt(250));
            mob(p, "minecraft:creeper", 20 + rng.nextInt(120));
        }
        mob(notch, "minecraft:ender_dragon", 2);
        mob(herobrine, "minecraft:wither", 3);
        mob(steve, "minecraft:warden", 1);

        // PvP encounters between rivals — each with several hits so the Combat Breakdown,
        // weapon stats, head-to-head records and nemesis/victim analysis are populated.
        String[] weapons = {"minecraft:diamond_sword", "minecraft:netherite_sword", "minecraft:iron_axe",
                "minecraft:bow", "minecraft:trident"};
        // (killer, victim, how many fights)
        record Rivalry(Player a, Player b, int fights) {}
        List<Rivalry> rivalries = List.of(
                new Rivalry(steve, alex, 4),
                new Rivalry(notch, herobrine, 3),
                new Rivalry(herobrine, steve, 2),
                new Rivalry(enderman, creeper, 3),
                new Rivalry(notch, steve, 2),
                new Rivalry(alex, enderman, 2)
        );
        int fightIdx = 0;
        for (Rivalry r : rivalries) {
            for (int f = 0; f < r.fights(); f++) {
                // alternate winner occasionally so head-to-head isn't 100%
                Player killer = (f % 3 == 2) ? r.b() : r.a();
                Player victim = (killer == r.a()) ? r.b() : r.a();
                Instant start = Instant.now().minus(fightIdx + 1L, ChronoUnit.DAYS).minus(rng.nextInt(600), ChronoUnit.MINUTES);
                Instant end = start.plus(8 + rng.nextInt(40), ChronoUnit.SECONDS);
                PvPEvent ev = new PvPEvent();
                ev.setKiller(killer);
                ev.setVictim(victim);
                ev.setStartedAt(start);
                ev.setEndedAt(end);
                pvpEventRepo.save(ev);

                int hits = 4 + rng.nextInt(6);
                Instant t = start;
                for (int h = 0; h < hits; h++) {
                    boolean killerHits = rng.nextInt(100) < 65; // killer lands most blows
                    Player attacker = killerHits ? killer : victim;
                    Player recv = killerHits ? victim : killer;
                    PvPHit hit = new PvPHit();
                    hit.setPvpEvent(ev);
                    hit.setAttacker(attacker);
                    hit.setVictim(recv);
                    hit.setDamage(2.0f + rng.nextFloat() * 8.0f);
                    hit.setWeapon(weapons[rng.nextInt(weapons.length)]);
                    t = t.plus(1 + rng.nextInt(5), ChronoUnit.SECONDS);
                    hit.setHitAt(t);
                    pvpHitRepo.save(hit);
                }
                eventLog(EventType.PVP_KILL, killer.getUsername() + " defeated " + victim.getUsername()
                        + " in a " + ChronoUnit.SECONDS.between(start, end) + "s duel", killer, end);
                fightIdx++;
            }
        }

        // Notable timeline events
        eventLog(EventType.BOSS_KILL, "Notch slew the Ender Dragon", notch, Instant.now().minus(20, ChronoUnit.DAYS));
        eventLog(EventType.BOSS_KILL, "Herobrine defeated the Wither", herobrine, Instant.now().minus(12, ChronoUnit.DAYS));
        eventLog(EventType.ACHIEVEMENT, "Steve reached 100 PvP kills", steve, Instant.now().minus(6, ChronoUnit.DAYS));
        eventLog(EventType.SERVER_MILESTONE, "The server passed 1,000,000 blocks mined", null, Instant.now().minus(3, ChronoUnit.DAYS));
        eventLog(EventType.PLAYER_DEATH, "CreeperKing was blown up by a creeper (the irony)", creeper, Instant.now().minus(1, ChronoUnit.DAYS));
    }

    /** kills, deaths, pvpDeaths, mobsKilled, blocksMined, blocksPlaced, itemsCrafted, distWalked,
     *  distFlown, shotsFired, shotsHit, dmgDealt, dmgReceived, longestShot */
    private record StatSet(long kills, long deaths, long pvpDeaths, long mobs, long mined, long placed,
                           long crafted, long walked, long flown, long shotsFired, long shotsHit,
                           float dmgDealt, float dmgReceived, long longestShot) {}

    private StatSet statSet(long kills, long deaths, long pvpDeaths, long mobs, long mined, long placed,
                            long crafted, long walked, long flown, long shotsFired, long shotsHit,
                            float dmgDealt, float dmgReceived, long longestShot) {
        return new StatSet(kills, deaths, pvpDeaths, mobs, mined, placed, crafted, walked, flown,
                shotsFired, shotsHit, dmgDealt, dmgReceived, longestShot);
    }

    private Player demoPlayer(String name, long coins, StatSet s) {
        Player p = new Player();
        p.setUsername(name);
        p.setPasswordHash(passwordEncoder.encode("demo"));
        p.setAdmin(false);
        p.setCoins(coins);
        playerRepo.save(p);

        PlayerStats ps = new PlayerStats();
        ps.setPlayer(p);
        ps.setKills(s.kills()); ps.setDeaths(s.deaths()); ps.setPvpDeaths(s.pvpDeaths());
        ps.setMobsKilled(s.mobs()); ps.setBlocksMined(s.mined()); ps.setBlocksPlaced(s.placed());
        ps.setItemsCrafted(s.crafted()); ps.setDistanceWalked(s.walked()); ps.setDistanceFlown(s.flown());
        ps.setShotsFired(s.shotsFired()); ps.setShotsHit(s.shotsHit());
        ps.setDamageDealt(s.dmgDealt()); ps.setDamageReceived(s.dmgReceived());
        ps.setPvpDamageDealt(s.dmgDealt() * 0.4f); ps.setPvpDamageReceived(s.dmgReceived() * 0.4f);
        ps.setLongestShotBlocks(s.longestShot());
        statsRepo.save(ps);
        return p;
    }

    private Clan demoClan(String name, String tag, Player leader) {
        Clan c = new Clan();
        c.setName(name);
        c.setTag(tag);
        c.setLeader(leader);
        return clanRepo.save(c);
    }

    private void assignClan(Player p, Clan c) {
        p.setClan(c);
        playerRepo.save(p);
    }

    private void block(Player p, String type, long mined, long placed) {
        BlockStatEntry b = new BlockStatEntry();
        b.setPlayer(p); b.setBlockType(type); b.setMined(mined); b.setPlaced(placed);
        blockRepo.save(b);
    }

    private void item(Player p, String type, long count) {
        ItemStatEntry i = new ItemStatEntry();
        i.setPlayer(p); i.setItemType(type); i.setCount(count);
        itemRepo.save(i);
    }

    private void mob(Player p, String type, long count) {
        MobStatEntry m = new MobStatEntry();
        m.setPlayer(p); m.setMobType(type); m.setCount(count);
        mobRepo.save(m);
    }

    private void eventLog(EventType type, String desc, Player p, Instant when) {
        EventLog e = new EventLog();
        e.setType(type); e.setDescription(desc); e.setPlayer(p); e.setOccurredAt(when);
        eventLogRepo.save(e);
    }

    private StoreItem item(String name, String desc, long price, StoreItem.Category cat, String effect) {
        StoreItem i = new StoreItem();
        i.setName(name);
        i.setDescription(desc);
        i.setPrice(price);
        i.setCategory(cat);
        i.setEffect(effect);
        return i;
    }
}
