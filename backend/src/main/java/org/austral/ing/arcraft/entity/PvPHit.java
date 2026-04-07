package org.austral.ing.arcraft.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pvp_hit")
@Getter @Setter @NoArgsConstructor
public class PvPHit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pvp_event_id", nullable = false)
    private PvPEvent pvpEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attacker_id", nullable = false)
    private Player attacker;

    @Column(nullable = false)
    private float damage;

    @Column(nullable = false)
    private String weapon;

    @Column(nullable = false)
    private Instant hitAt;
}
