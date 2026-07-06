package org.austral.ing.arcraft.web.model;

import java.time.Instant;
import java.util.UUID;

/** Read-model of a player_purchase row with its item (and optionally the buyer). */
public class PurchaseView {

    private final UUID id;
    private final long pricePaid;
    private final boolean equipped;
    private final Instant purchasedAt;
    private final StoreItemView item;
    private PlayerView player;

    public PurchaseView(UUID id, long pricePaid, boolean equipped, Instant purchasedAt, StoreItemView item) {
        this.id = id;
        this.pricePaid = pricePaid;
        this.equipped = equipped;
        this.purchasedAt = purchasedAt;
        this.item = item;
    }

    public UUID getId() { return id; }
    public long getPricePaid() { return pricePaid; }
    public boolean isEquipped() { return equipped; }
    public Instant getPurchasedAt() { return purchasedAt; }
    public StoreItemView getItem() { return item; }
    public PlayerView getPlayer() { return player; }
    public void setPlayer(PlayerView player) { this.player = player; }
}
