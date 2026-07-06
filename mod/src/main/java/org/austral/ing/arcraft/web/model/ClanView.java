package org.austral.ing.arcraft.web.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Read-model of a clan row (JPA-entity-compatible getters for the templates). */
public class ClanView {

    private final UUID id;
    private final String name;
    private final String tag;
    private final boolean friendlyFireEnabled;
    private final Instant createdAt;
    private PlayerView leader;
    private List<PlayerView> members = new ArrayList<>();

    public ClanView(UUID id, String name, String tag, boolean friendlyFireEnabled, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.friendlyFireEnabled = friendlyFireEnabled;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getTag() { return tag; }
    public boolean isFriendlyFireEnabled() { return friendlyFireEnabled; }
    public Instant getCreatedAt() { return createdAt; }
    public PlayerView getLeader() { return leader; }
    public void setLeader(PlayerView leader) { this.leader = leader; }
    public List<PlayerView> getMembers() { return members; }
    public void setMembers(List<PlayerView> members) { this.members = members; }
}
