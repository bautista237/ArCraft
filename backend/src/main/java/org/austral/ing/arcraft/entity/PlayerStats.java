package org.austral.ing.arcraft.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "player_stats")
@Getter @Setter @NoArgsConstructor
public class PlayerStats {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false, unique = true)
    private Player player;

    private long kills = 0;
    private long deaths = 0;
    private float damageDealt = 0;
    private float damageReceived = 0;
    private long mobsKilled = 0;
    private long blocksPlaced = 0;
    private long blocksMined = 0;
    private long itemsCrafted = 0;
    private long distanceWalked = 0;
    private long distanceSwum = 0;
    private long distanceFlown = 0;
    private long distanceSailed = 0;
    private long shotsFired = 0;
    private long shotsHit = 0;
    private long longestShotBlocks = 0;
}
