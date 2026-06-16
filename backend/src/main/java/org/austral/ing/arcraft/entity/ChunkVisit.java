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

    // Heatmap counters (accumulated per player per chunk).
    @Column(name = "blocks_mined", nullable = false)
    private long blocksMined = 0;

    @Column(name = "blocks_placed", nullable = false)
    private long blocksPlaced = 0;

    // "Staying" intensity: incremented once per ~second the player spends in the chunk.
    @Column(name = "stay_ticks", nullable = false)
    private long stayTicks = 0;

    // Surface height at the chunk centre — used to shade terrain relief on the web map.
    @Column(name = "surface_y")
    private int surfaceY = 0;

    @Column(nullable = false)
    private LocalDateTime firstVisited;

    @Column(nullable = false)
    private LocalDateTime lastVisited;
}
