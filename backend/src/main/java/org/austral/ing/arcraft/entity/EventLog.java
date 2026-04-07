package org.austral.ing.arcraft.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_log")
@Getter @Setter @NoArgsConstructor
public class EventLog {

    public enum EventType {
        PLAYER_DEATH, BOSS_KILL, ACHIEVEMENT, PVP_KILL, SERVER_MILESTONE, CUSTOM
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType type;

    @Column(nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private Player player;

    @Column(nullable = false)
    private Instant occurredAt;
}
