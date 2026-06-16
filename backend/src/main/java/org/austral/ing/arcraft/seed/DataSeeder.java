package org.austral.ing.arcraft.seed;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.Event;
import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.entity.PlayerStats;
import org.austral.ing.arcraft.entity.StoreItem;
import org.austral.ing.arcraft.repository.EventRepository;
import org.austral.ing.arcraft.repository.PlayerRepository;
import org.austral.ing.arcraft.repository.PlayerStatsRepository;
import org.austral.ing.arcraft.repository.StoreItemRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final PlayerRepository playerRepo;
    private final PlayerStatsRepository statsRepo;
    private final StoreItemRepository storeItemRepo;
    private final EventRepository eventRepo;
    private final PasswordEncoder passwordEncoder;

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
