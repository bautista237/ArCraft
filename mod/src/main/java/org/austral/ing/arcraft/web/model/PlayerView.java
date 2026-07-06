package org.austral.ing.arcraft.web.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-model of a player row, with the same getters the old JPA entity exposed so the
 * Thymeleaf templates (OGNL property access) carry over unchanged.
 */
public class PlayerView {

    private final UUID id;
    private final String username;
    private final boolean admin;
    private final long coins;
    private final String email;
    private final boolean emailVerified;
    private final Instant createdAt;
    private ClanView clan;

    public PlayerView(UUID id, String username, boolean admin, long coins,
                      String email, boolean emailVerified, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.admin = admin;
        this.coins = coins;
        this.email = email;
        this.emailVerified = emailVerified;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public boolean isAdmin() { return admin; }
    public long getCoins() { return coins; }
    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }
    public Instant getCreatedAt() { return createdAt; }
    public ClanView getClan() { return clan; }
    public void setClan(ClanView clan) { this.clan = clan; }
}
