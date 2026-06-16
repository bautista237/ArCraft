package org.austral.ing.arcraft.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_purchase",
        uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "item_id"}))
@Getter @Setter @NoArgsConstructor
public class PlayerPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private StoreItem item;

    @Column(nullable = false)
    private long pricePaid = 0;

    @Column(nullable = false)
    private boolean equipped = false;

    @Column(nullable = false)
    private Instant purchasedAt = Instant.now();
}
