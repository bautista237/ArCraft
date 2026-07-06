package org.austral.ing.arcraft.web.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-model of an event_log row (live feed). {@code type} stays an enum because the
 * templates call {@code e.type.name()}; unknown strings map to {@link EventType#CUSTOM}.
 */
public class EventLogView {

    public enum EventType {
        PLAYER_DEATH, BOSS_KILL, ACHIEVEMENT, PVP_KILL, SERVER_MILESTONE, CHAT, CUSTOM;

        public static EventType of(String raw) {
            if (raw == null) return CUSTOM;
            try {
                return valueOf(raw);
            } catch (IllegalArgumentException e) {
                return CUSTOM;
            }
        }
    }

    private final UUID id;
    private final EventType type;
    private final String description;
    private final String imageUrl;
    private final Instant occurredAt;
    private PlayerView player;

    public EventLogView(UUID id, String type, String description, String imageUrl, Instant occurredAt) {
        this.id = id;
        this.type = EventType.of(type);
        this.description = description;
        this.imageUrl = imageUrl;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public EventType getType() { return type; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public Instant getOccurredAt() { return occurredAt; }
    public PlayerView getPlayer() { return player; }
    public void setPlayer(PlayerView player) { this.player = player; }
}
