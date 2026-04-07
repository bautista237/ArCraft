package org.austral.ing.arcraft.seed;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.*;
import org.austral.ing.arcraft.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final PlayerRepository playerRepo;
    private final PlayerStatsRepository statsRepo;
    private final ClanRepository clanRepo;
    private final BlockStatEntryRepository blockRepo;
    private final ItemStatEntryRepository itemRepo;
    private final MobStatEntryRepository mobRepo;
    private final PvPEventRepository pvpEventRepo;
    private final PvPHitRepository pvpHitRepo;
    private final EventLogRepository eventLogRepo;
    private final ServerConfigRepository serverConfigRepo;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (playerRepo.count() > 0) return; // already seeded

        Random rng = new Random(42);

        // --- Server config ---
        ServerConfig config = new ServerConfig();
        config.setServerName("ArCraft SMP");
        config.setServerStartDate(Instant.now().minus(30, ChronoUnit.DAYS));
        serverConfigRepo.save(config);

        // --- Admin player (save first so clan leader FK works) ---
        Player admin = new Player();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("admin"));
        admin.setAdmin(true);
        playerRepo.save(admin);

        // --- 5 fake players ---
        String[] names = {"Steve", "Alex", "Notch", "Herobrine", "Jeb"};
        Player[] players = new Player[5];
        for (int i = 0; i < 5; i++) {
            Player p = new Player();
            p.setUsername(names[i]);
            p.setPasswordHash(passwordEncoder.encode("password" + (i + 1)));
            playerRepo.save(p);
            players[i] = p;
        }

        // --- Clan (players[0] as leader, players[0..2] as members) ---
        Clan clan = new Clan();
        clan.setName("Arc Raiders");
        clan.setTag("ARC");
        clan.setLeader(players[0]);
        clan.setFriendlyFireEnabled(false);
        clanRepo.save(clan);

        players[0].setClan(clan);
        players[1].setClan(clan);
        players[2].setClan(clan);
        playerRepo.saveAll(List.of(players[0], players[1], players[2]));

        // --- PlayerStats for all fake players ---
        for (Player p : players) {
            PlayerStats s = new PlayerStats();
            s.setPlayer(p);
            s.setKills(rng.nextInt(200));
            s.setDeaths(rng.nextInt(100));
            s.setDamageDealt(rng.nextFloat() * 50000);
            s.setDamageReceived(rng.nextFloat() * 30000);
            s.setMobsKilled(rng.nextInt(5000));
            s.setBlocksPlaced(rng.nextInt(20000));
            s.setBlocksMined(rng.nextInt(30000));
            s.setItemsCrafted(rng.nextInt(3000));
            s.setDistanceWalked(rng.nextInt(500000));
            s.setDistanceSwum(rng.nextInt(10000));
            s.setDistanceFlown(rng.nextInt(50000));
            s.setDistanceSailed(rng.nextInt(8000));
            s.setShotsFired(rng.nextInt(2000));
            s.setShotsHit(rng.nextInt(1000));
            s.setLongestShotBlocks(rng.nextInt(200));
            statsRepo.save(s);
        }

        // --- Admin PlayerStats (zeroed, admin doesn't play) ---
        PlayerStats adminStats = new PlayerStats();
        adminStats.setPlayer(admin);
        statsRepo.save(adminStats);

        // --- BlockStatEntry samples ---
        String[] blockTypes = {"minecraft:diamond_ore", "minecraft:iron_ore", "minecraft:coal_ore",
                "minecraft:oak_log", "minecraft:stone"};
        for (Player p : players) {
            for (String bt : blockTypes) {
                BlockStatEntry b = new BlockStatEntry();
                b.setPlayer(p);
                b.setBlockType(bt);
                b.setMined(rng.nextInt(500));
                b.setPlaced(rng.nextInt(200));
                blockRepo.save(b);
            }
        }

        // --- ItemStatEntry samples ---
        String[] itemTypes = {"minecraft:torch", "minecraft:crafting_table", "minecraft:chest",
                "minecraft:sword", "minecraft:pickaxe"};
        for (Player p : players) {
            for (String it : itemTypes) {
                ItemStatEntry item = new ItemStatEntry();
                item.setPlayer(p);
                item.setItemType(it);
                item.setCount(rng.nextInt(300));
                itemRepo.save(item);
            }
        }

        // --- MobStatEntry samples ---
        String[] mobTypes = {"minecraft:zombie", "minecraft:skeleton", "minecraft:creeper",
                "minecraft:enderman", "minecraft:ender_dragon"};
        for (Player p : players) {
            for (String mt : mobTypes) {
                MobStatEntry mob = new MobStatEntry();
                mob.setPlayer(p);
                mob.setMobType(mt);
                mob.setCount(mt.equals("minecraft:ender_dragon") ? rng.nextInt(3) : rng.nextInt(300));
                mobRepo.save(mob);
            }
        }

        // --- PvPEvents with PvPHits ---
        Instant base = Instant.now().minus(10, ChronoUnit.DAYS);
        int[][] fights = {{0, 1}, {1, 2}, {2, 0}};
        String[] weapons = {"minecraft:diamond_sword", "minecraft:iron_sword", "minecraft:bow"};

        for (int[] fight : fights) {
            Instant start = base.plus(rng.nextInt(72), ChronoUnit.HOURS);
            Instant end = start.plus(rng.nextInt(120) + 10, ChronoUnit.SECONDS);

            PvPEvent event = new PvPEvent();
            event.setKiller(players[fight[0]]);
            event.setVictim(players[fight[1]]);
            event.setStartedAt(start);
            event.setEndedAt(end);
            pvpEventRepo.save(event);

            int hitCount = rng.nextInt(5) + 3;
            for (int h = 0; h < hitCount; h++) {
                PvPHit hit = new PvPHit();
                hit.setPvpEvent(event);
                hit.setAttacker(rng.nextBoolean() ? players[fight[0]] : players[fight[1]]);
                hit.setDamage(rng.nextFloat() * 8 + 2);
                hit.setWeapon(weapons[rng.nextInt(weapons.length)]);
                hit.setHitAt(start.plus(h * 10L, ChronoUnit.SECONDS));
                pvpHitRepo.save(hit);
            }
        }

        // --- EventLog entries ---
        String[] descriptions = {
                "Steve killed Alex with a diamond sword",
                "Notch slew the Ender Dragon",
                "Herobrine reached 100 kills",
                "Server reached Day 30",
                "Jeb mined 10,000 blocks",
                "Alex joined the server for the first time",
                "Steve fell into the void",
                "Clan Arc Raiders was founded",
                "Notch crafted 500 items",
                "Herobrine was killed by a skeleton"
        };
        EventLog.EventType[] types = {
                EventLog.EventType.PVP_KILL, EventLog.EventType.BOSS_KILL,
                EventLog.EventType.ACHIEVEMENT, EventLog.EventType.SERVER_MILESTONE,
                EventLog.EventType.ACHIEVEMENT, EventLog.EventType.CUSTOM,
                EventLog.EventType.PLAYER_DEATH, EventLog.EventType.SERVER_MILESTONE,
                EventLog.EventType.ACHIEVEMENT, EventLog.EventType.PLAYER_DEATH
        };

        for (int i = 0; i < descriptions.length; i++) {
            EventLog e = new EventLog();
            e.setType(types[i]);
            e.setDescription(descriptions[i]);
            e.setOccurredAt(Instant.now().minus(i * 2L, ChronoUnit.DAYS));
            if (i < 5) e.setPlayer(players[i % 5]);
            eventLogRepo.save(e);
        }
    }
}
