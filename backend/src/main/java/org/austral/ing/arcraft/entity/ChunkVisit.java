package org.austral.ing.arcraft.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chunk_visit",
        uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "chunk_x", "chunk_z", "dimension"}))
@Getter @Setter @NoArgsConstructor
public class ChunkVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "chunk_x", nullable = false)
    private int chunkX;

    @Column(name = "chunk_z", nullable = false)
    private int chunkZ;

    @Column(nullable = false)
    private String dimension = "minecraft:overworld";

    private String biome;

    @Column(name = "top_block")
    private String topBlock;

    @Column(name = "map_color_r")
    private int mapColorR = 100;

    @Column(name = "map_color_g")
    private int mapColorG = 140;

    @Column(name = "map_color_b")
    private int mapColorB = 100;

    @Column(nullable = false)
    private LocalDateTime firstVisited;

    @Column(nullable = false)
    private LocalDateTime lastVisited;
}
