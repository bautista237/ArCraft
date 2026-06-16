package org.austral.ing.arcraft.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player")
@Getter @Setter @NoArgsConstructor
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean isAdmin = false;

    @Column(nullable = false)
    private long coins = 0;

    // Optional — used for event reminder emails. Registered & verified in-game via /email.
    @Column
    private String email;

    @Column(nullable = false)
    private boolean emailVerified = false;

    // Pending 6-digit verification code (null once verified), and whether the backend has
    // already dispatched the verification email for the current code.
    @Column
    private String verificationCode;

    @Column(nullable = false)
    private boolean verificationSent = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clan_id")
    private Clan clan;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
