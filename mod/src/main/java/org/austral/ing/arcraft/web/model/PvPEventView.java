package org.austral.ing.arcraft.web.model;

import java.time.Instant;
import java.util.UUID;

/** Read-model of a pvp_event row with both fighters resolved. */
public class PvPEventView {

    private final UUID id;
    private final Instant startedAt;
    private final Instant endedAt;
    private PlayerView killer;
    private PlayerView victim;

    public PvPEventView(UUID id, Instant startedAt, Instant endedAt) {
        this.id = id;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public UUID getId() { return id; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public PlayerView getKiller() { return killer; }
    public void setKiller(PlayerView killer) { this.killer = killer; }
    public PlayerView getVictim() { return victim; }
    public void setVictim(PlayerView victim) { this.victim = victim; }
}
