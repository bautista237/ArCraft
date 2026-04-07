package org.austral.ing.arcraft.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "item_stat_entry",
        uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "item_type"}))
@Getter @Setter @NoArgsConstructor
public class ItemStatEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "item_type", nullable = false)
    private String itemType;

    private long count = 0;
}
