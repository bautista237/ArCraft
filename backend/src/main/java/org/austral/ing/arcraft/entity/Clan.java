package org.austral.ing.arcraft.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clan")
@Getter @Setter @NoArgsConstructor
public class Clan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String tag;

    // Leader is a Player — stored as FK, resolved lazily to break circular load
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_id", nullable = false)
    private Player leader;

    @Column(nullable = false)
    private boolean friendlyFireEnabled = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
