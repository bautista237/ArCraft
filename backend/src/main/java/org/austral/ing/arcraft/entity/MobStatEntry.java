package org.austral.ing.arcraft.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "mob_stat_entry",
        uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "mob_type"}))
@Getter @Setter @NoArgsConstructor
public class MobStatEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "mob_type", nullable = false)
    private String mobType;

    private long count = 0;
}
