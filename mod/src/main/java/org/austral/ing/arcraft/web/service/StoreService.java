package org.austral.ing.arcraft.web.service;

import org.austral.ing.arcraft.db.Database;
import org.austral.ing.arcraft.web.dao.Daos;
import org.austral.ing.arcraft.web.model.PlayerView;
import org.austral.ing.arcraft.web.model.PurchaseView;
import org.austral.ing.arcraft.web.model.StoreItemView;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The coin store: items (skins/titles/cosmetics), purchases, equipping, and admin coin grants.
 * Same behavior as the old Spring StoreService, with jdbi SQL instead of JPA repositories.
 */
public final class StoreService {

    public static final StoreService INSTANCE = new StoreService();

    private static final String ITEM_COLS =
            "i.id i_id, i.name i_name, i.description i_desc, i.price i_price, "
            + "i.category i_cat, i.effect i_effect, i.created_at i_created";

    private StoreService() {
    }

    private static StoreItemView mapItem(ResultSet rs) throws SQLException {
        return new StoreItemView(Daos.uuid(rs, "i_id"), rs.getString("i_name"), rs.getString("i_desc"),
                rs.getLong("i_price"), rs.getString("i_cat"), rs.getString("i_effect"),
                Daos.instant(rs, "i_created"));
    }

    // ── Store items ─────────────────────────────────────────────────────────

    public List<StoreItemView> getAllItems() {
        return Database.jdbi().withHandle(h -> h
                .createQuery("SELECT " + ITEM_COLS + " FROM store_item i ORDER BY i.category, i.price")
                .map((rs, c) -> mapItem(rs))
                .list());
    }

    /** Creates an item; returns an error message or null on success (old service contract). */
    public String createItem(String name, String description, long price, String category, String effect) {
        if (name == null || name.isBlank()) return "Item name is required.";
        if (price < 0) return "Price cannot be negative.";
        Database.jdbi().useHandle(h -> h
                .createUpdate("""
                        INSERT INTO store_item (id, name, description, price, category, effect, created_at)
                        VALUES (:id, :name, :desc, :price, :cat, :effect, :created)
                        """)
                .bind("id", UUID.randomUUID())
                .bind("name", name.trim())
                .bind("desc", description == null ? "" : description.trim())
                .bind("price", price)
                .bind("cat", StoreItemView.Category.of(category).name())
                .bind("effect", effect == null || effect.isBlank() ? null : effect.trim().toUpperCase())
                .bind("created", Instant.now())
                .execute());
        return null;
    }

    public void deleteItem(UUID itemId) {
        Database.jdbi().useTransaction(h -> {
            h.createUpdate("DELETE FROM player_purchase WHERE item_id = :id").bind("id", itemId).execute();
            h.createUpdate("DELETE FROM store_item WHERE id = :id").bind("id", itemId).execute();
        });
    }

    // ── Coins ───────────────────────────────────────────────────────────────

    /** Adds (or removes, clamped at 0) coins. Returns an error message or null. */
    public String grantCoins(UUID playerId, long amount) {
        Integer updated = Database.jdbi().withHandle(h -> h
                .createUpdate("UPDATE player SET coins = GREATEST(0, coins + :d) WHERE id = :id")
                .bind("d", amount)
                .bind("id", playerId)
                .execute());
        return updated != null && updated > 0 ? null : "Player not found.";
    }

    // ── Purchases ───────────────────────────────────────────────────────────

    public List<PurchaseView> getPurchases(UUID playerId) {
        return Database.jdbi().withHandle(h -> h
                .createQuery("""
                        SELECT pp.id pp_id, pp.price_paid pp_paid, pp.equipped pp_eq, pp.purchased_at pp_at, %s
                        FROM player_purchase pp JOIN store_item i ON pp.item_id = i.id
                        WHERE pp.player_id = :pid ORDER BY pp.purchased_at DESC
                        """.formatted(ITEM_COLS))
                .bind("pid", playerId)
                .map((rs, c) -> new PurchaseView(Daos.uuid(rs, "pp_id"), rs.getLong("pp_paid"),
                        rs.getBoolean("pp_eq"), Daos.instant(rs, "pp_at"), mapItem(rs)))
                .list());
    }

    public Set<UUID> getOwnedItemIds(UUID playerId) {
        return new HashSet<>(Database.jdbi().withHandle(h -> h
                .createQuery("SELECT item_id FROM player_purchase WHERE player_id = :pid")
                .bind("pid", playerId)
                .map((rs, c) -> Daos.uuid(rs, "item_id"))
                .list()));
    }

    /** Buys an item with coins. Returns an error message or null on success. */
    public String purchase(PlayerView player, UUID itemId) {
        return Database.jdbi().inTransaction(h -> {
            Optional<StoreItemView> itemOpt = h
                    .createQuery("SELECT " + ITEM_COLS + " FROM store_item i WHERE i.id = :id")
                    .bind("id", itemId)
                    .map((rs, c) -> mapItem(rs))
                    .findFirst();
            if (itemOpt.isEmpty()) return "That item no longer exists.";
            StoreItemView item = itemOpt.get();

            boolean owned = h.createQuery(
                            "SELECT COUNT(*) FROM player_purchase WHERE player_id = :pid AND item_id = :iid")
                    .bind("pid", player.getId()).bind("iid", itemId)
                    .mapTo(Long.class).one() > 0;
            if (owned) return "You already own this item.";

            long coins = h.createQuery("SELECT coins FROM player WHERE id = :pid")
                    .bind("pid", player.getId()).mapTo(Long.class).one();
            if (coins < item.getPrice()) {
                return "Not enough coins. You need " + item.getPrice() + " but have " + coins + ".";
            }
            h.createUpdate("UPDATE player SET coins = coins - :p WHERE id = :pid")
                    .bind("p", item.getPrice()).bind("pid", player.getId()).execute();

            // Auto-equip the first title; auto-equip cosmetics (effects) so they work immediately.
            boolean equip = false;
            if (item.getCategory() == StoreItemView.Category.TITLE) {
                equip = getEquippedTitle(player.getId()) == null;
            } else if (item.getEffect() != null && !item.getEffect().isBlank()) {
                equip = true;
            }
            h.createUpdate("""
                            INSERT INTO player_purchase (id, player_id, item_id, price_paid, equipped, purchased_at)
                            VALUES (:id, :pid, :iid, :paid, :eq, :at)
                            """)
                    .bind("id", UUID.randomUUID())
                    .bind("pid", player.getId())
                    .bind("iid", itemId)
                    .bind("paid", item.getPrice())
                    .bind("eq", equip)
                    .bind("at", Instant.now())
                    .execute();
            return null;
        });
    }

    /** Toggle a cosmetic (particle trail / glow) on or off. Returns the new state, or null. */
    public Boolean toggleCosmetic(UUID playerId, UUID purchaseId) {
        return Database.jdbi().inTransaction(h -> {
            var row = h.createQuery("""
                            SELECT pp.equipped, i.effect FROM player_purchase pp
                            JOIN store_item i ON pp.item_id = i.id
                            WHERE pp.id = :id AND pp.player_id = :pid
                            """)
                    .bind("id", purchaseId).bind("pid", playerId)
                    .map((rs, c) -> new Object[]{rs.getBoolean(1), rs.getString(2)})
                    .findFirst().orElse(null);
            if (row == null || row[1] == null || ((String) row[1]).isBlank()) return null;
            boolean now = !((Boolean) row[0]);
            h.createUpdate("UPDATE player_purchase SET equipped = :eq WHERE id = :id")
                    .bind("eq", now).bind("id", purchaseId).execute();
            return now;
        });
    }

    /** Equip a title the player owns (un-equips any other title). Error message or null. */
    public String equipTitle(UUID playerId, UUID purchaseId) {
        return Database.jdbi().inTransaction(h -> {
            boolean isTitle = h.createQuery("""
                            SELECT COUNT(*) FROM player_purchase pp JOIN store_item i ON pp.item_id = i.id
                            WHERE pp.id = :id AND pp.player_id = :pid AND i.category = 'TITLE'
                            """)
                    .bind("id", purchaseId).bind("pid", playerId)
                    .mapTo(Long.class).one() > 0;
            if (!isTitle) return "Title not found in your inventory.";
            h.createUpdate("""
                            UPDATE player_purchase SET equipped = (id = :id)
                            WHERE player_id = :pid
                              AND item_id IN (SELECT id FROM store_item WHERE category = 'TITLE')
                            """)
                    .bind("id", purchaseId).bind("pid", playerId).execute();
            return null;
        });
    }

    // ── Titles (display) ────────────────────────────────────────────────────

    /** The title string a player currently displays next to their name, or null. */
    public String getEquippedTitle(UUID playerId) {
        List<PurchaseView> purchases = getPurchases(playerId);
        PurchaseView equipped = null;
        PurchaseView fallback = null;
        for (PurchaseView p : purchases) {
            if (p.getItem().getCategory() == StoreItemView.Category.TITLE) {
                if (p.isEquipped()) { equipped = p; break; }
                if (fallback == null) fallback = p;
            }
        }
        PurchaseView chosen = equipped != null ? equipped : fallback;
        return chosen == null ? null : chosen.getItem().getName();
    }

    /** username → equipped title, for every player that owns a title. Used by rankings. */
    public Map<String, String> getTitlesByUsername() {
        Map<String, String> titles = new HashMap<>();
        Map<String, String> equipped = new HashMap<>();
        Database.jdbi().useHandle(h -> h
                .createQuery("""
                        SELECT p.username u, i.name n, pp.equipped eq
                        FROM player_purchase pp
                        JOIN store_item i ON pp.item_id = i.id
                        JOIN player p ON pp.player_id = p.id
                        WHERE i.category = 'TITLE'
                        ORDER BY pp.purchased_at ASC
                        """)
                .map((rs, c) -> {
                    titles.put(rs.getString("u"), rs.getString("n"));
                    if (rs.getBoolean("eq")) equipped.put(rs.getString("u"), rs.getString("n"));
                    return null;
                })
                .list());
        titles.putAll(equipped); // equipped title takes precedence
        return titles;
    }
}
