package org.austral.ing.arcraft.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "block_stat_entry",
        uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "block_type"}))
@Getter @Setter @NoArgsConstructor
public class BlockStatEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "block_type", nullable = false)
    private String blockType;

    private long mined = 0;
    private long placed = 0;
}
