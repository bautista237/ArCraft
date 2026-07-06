package org.austral.ing.arcraft.web.model;

import java.util.UUID;

/** Read-model of a player_stats row (JPA-entity-compatible getters). */
public class StatsView {

    private final UUID playerId;
    private PlayerView player;

    private long kills;
    private long deaths;
    private long pvpDeaths;
    private float damageDealt;
    private float damageReceived;
    private float pvpDamageDealt;
    private float pvpDamageReceived;
    private long mobsKilled;
    private long blocksPlaced;
    private long blocksMined;
    private long itemsCrafted;
    private long distanceWalked;
    private long distanceSwum;
    private long distanceFlown;
    private long distanceSailed;
    private long shotsFired;
    private long shotsHit;
    private long longestShotBlocks;

    public StatsView(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() { return playerId; }
    public PlayerView getPlayer() { return player; }
    public void setPlayer(PlayerView player) { this.player = player; }

    public long getKills() { return kills; }
    public long getDeaths() { return deaths; }
    public long getPvpDeaths() { return pvpDeaths; }
    public float getDamageDealt() { return damageDealt; }
    public float getDamageReceived() { return damageReceived; }
    public float getPvpDamageDealt() { return pvpDamageDealt; }
    public float getPvpDamageReceived() { return pvpDamageReceived; }
    public long getMobsKilled() { return mobsKilled; }
    public long getBlocksPlaced() { return blocksPlaced; }
    public long getBlocksMined() { return blocksMined; }
    public long getItemsCrafted() { return itemsCrafted; }
    public long getDistanceWalked() { return distanceWalked; }
    public long getDistanceSwum() { return distanceSwum; }
    public long getDistanceFlown() { return distanceFlown; }
    public long getDistanceSailed() { return distanceSailed; }
    public long getShotsFired() { return shotsFired; }
    public long getShotsHit() { return shotsHit; }
    public long getLongestShotBlocks() { return longestShotBlocks; }

    public void setKills(long v) { kills = v; }
    public void setDeaths(long v) { deaths = v; }
    public void setPvpDeaths(long v) { pvpDeaths = v; }
    public void setDamageDealt(float v) { damageDealt = v; }
    public void setDamageReceived(float v) { damageReceived = v; }
    public void setPvpDamageDealt(float v) { pvpDamageDealt = v; }
    public void setPvpDamageReceived(float v) { pvpDamageReceived = v; }
    public void setMobsKilled(long v) { mobsKilled = v; }
    public void setBlocksPlaced(long v) { blocksPlaced = v; }
    public void setBlocksMined(long v) { blocksMined = v; }
    public void setItemsCrafted(long v) { itemsCrafted = v; }
    public void setDistanceWalked(long v) { distanceWalked = v; }
    public void setDistanceSwum(long v) { distanceSwum = v; }
    public void setDistanceFlown(long v) { distanceFlown = v; }
    public void setDistanceSailed(long v) { distanceSailed = v; }
    public void setShotsFired(long v) { shotsFired = v; }
    public void setShotsHit(long v) { shotsHit = v; }
    public void setLongestShotBlocks(long v) { longestShotBlocks = v; }
}
