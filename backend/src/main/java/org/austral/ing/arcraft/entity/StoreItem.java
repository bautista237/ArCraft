package org.austral.ing.arcraft.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "store_item")
@Getter @Setter @NoArgsConstructor
public class StoreItem {

    public enum Category {
        SKIN, TITLE, COSMETIC
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 1024)
    private String description;

    @Column(nullable = false)
    private long price = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category = Category.COSMETIC;

    // In-game effect the mod grants while the owner is online. One of:
    // PARTICLE_TRAIL, SPEED, JUMP, HASTE, NIGHT_VISION, GLOW (or null/blank for none).
    @Column
    private String effect;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
