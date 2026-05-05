package org.austral.ing.arcraft.seed;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.entity.PlayerStats;
import org.austral.ing.arcraft.repository.PlayerRepository;
import org.austral.ing.arcraft.repository.PlayerStatsRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final PlayerRepository playerRepo;
    private final PlayerStatsRepository statsRepo;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (playerRepo.count() > 0) return; // already seeded

        Player admin = new Player();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("admin"));
        admin.setAdmin(true);
        playerRepo.save(admin);

        PlayerStats adminStats = new PlayerStats();
        adminStats.setPlayer(admin);
        statsRepo.save(adminStats);
    }
}
