package org.austral.ing.arcraft.service;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.entity.PlayerPurchase;
import org.austral.ing.arcraft.entity.StoreItem;
import org.austral.ing.arcraft.repository.PlayerPurchaseRepository;
import org.austral.ing.arcraft.repository.PlayerRepository;
import org.austral.ing.arcraft.repository.StoreItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class StoreService {

    private final StoreItemRepository storeItemRepository;
    private final PlayerPurchaseRepository playerPurchaseRepository;
    private final PlayerRepository playerRepository;

    // ── Store items (admin) ──────────────────────────────────

    @Transactional(readOnly = true)
    public List<StoreItem> getAllItems() {
        return storeItemRepository.findAllByOrderByCategoryAscPriceAsc();
    }

    public String createItem(String name, String description, long price, StoreItem.Category category, String effect) {
        if (name == null || name.isBlank()) {
            return "Item name is required.";
        }
        if (price < 0) {
            return "Price cannot be negative.";
        }
        StoreItem item = new StoreItem();
        item.setName(name.trim());
        item.setDescription(description == null ? "" : description.trim());
        item.setPrice(price);
        item.setCategory(category == null ? StoreItem.Category.COSMETIC : category);
        item.setEffect(effect == null || effect.isBlank() ? null : effect.trim().toUpperCase());
        item.setCreatedAt(Instant.now());
        storeItemRepository.save(item);
        return null;
    }

    public void deleteItem(UUID itemId) {
        StoreItem item = storeItemRepository.findById(itemId).orElse(null);
        if (item == null) return;
        // Remove purchases of this item first to satisfy FK constraints.
        for (PlayerPurchase p : playerPurchaseRepository.findAll()) {
            if (p.getItem().getId().equals(itemId)) {
                playerPurchaseRepository.delete(p);
            }
        }
        storeItemRepository.deleteById(itemId);
    }

    // ── Coins (admin grant / awards) ─────────────────────────

    public String grantCoins(UUID playerId, long amount) {
        Player player = playerRepository.findById(playerId).orElse(null);
        if (player == null) {
            return "Player not found.";
        }
        long newBalance = player.getCoins() + amount;
        if (newBalance < 0) newBalance = 0;
        player.setCoins(newBalance);
        playerRepository.save(player);
        return null;
    }

    // ── Purchases (player-facing) ────────────────────────────

    @Transactional(readOnly = true)
    public List<PlayerPurchase> getPurchases(Player player) {
        return playerPurchaseRepository.findByPlayerOrderByPurchasedAtDesc(player);
    }

    @Transactional(readOnly = true)
    public Set<UUID> getOwnedItemIds(Player player) {
        Set<UUID> owned = new HashSet<>();
        for (PlayerPurchase p : playerPurchaseRepository.findByPlayerOrderByPurchasedAtDesc(player)) {
            owned.add(p.getItem().getId());
        }
        return owned;
    }

    public String purchase(Player player, UUID itemId) {
        StoreItem item = storeItemRepository.findById(itemId).orElse(null);
        if (item == null) {
            return "That item no longer exists.";
        }
        if (playerPurchaseRepository.existsByPlayerAndItem(player, item)) {
            return "You already own this item.";
        }
        if (player.getCoins() < item.getPrice()) {
            return "Not enough coins. You need " + item.getPrice() + " but have " + player.getCoins() + ".";
        }
        player.setCoins(player.getCoins() - item.getPrice());
        playerRepository.save(player);

        PlayerPurchase purchase = new PlayerPurchase();
        purchase.setPlayer(player);
        purchase.setItem(item);
        purchase.setPricePaid(item.getPrice());
        purchase.setPurchasedAt(Instant.now());
        // Auto-equip the first title; auto-equip cosmetics (effects) so they work immediately.
        if (item.getCategory() == StoreItem.Category.TITLE) {
            if (getEquippedTitle(player) == null) purchase.setEquipped(true);
        } else if (item.getEffect() != null && !item.getEffect().isBlank()) {
            purchase.setEquipped(true);
        }
        playerPurchaseRepository.save(purchase);
        return null;
    }

    /** Toggle a cosmetic (particle trail / glow) on or off. Returns whether it is now equipped. */
    public Boolean toggleCosmetic(Player player, UUID purchaseId) {
        PlayerPurchase target = playerPurchaseRepository.findByPlayerOrderByPurchasedAtDesc(player).stream()
                .filter(p -> p.getId().equals(purchaseId))
                .findFirst().orElse(null);
        if (target == null || target.getItem().getEffect() == null || target.getItem().getEffect().isBlank()) {
            return null;
        }
        target.setEquipped(!target.isEquipped());
        playerPurchaseRepository.save(target);
        return target.isEquipped();
    }

    /** Equip a title the player owns (un-equips any other title). */
    public String equipTitle(Player player, UUID purchaseId) {
        List<PlayerPurchase> purchases = playerPurchaseRepository.findByPlayerOrderByPurchasedAtDesc(player);
        PlayerPurchase target = purchases.stream()
                .filter(p -> p.getId().equals(purchaseId))
                .findFirst().orElse(null);
        if (target == null || target.getItem().getCategory() != StoreItem.Category.TITLE) {
            return "Title not found in your inventory.";
        }
        for (PlayerPurchase p : purchases) {
            if (p.getItem().getCategory() == StoreItem.Category.TITLE) {
                p.setEquipped(p.getId().equals(purchaseId));
                playerPurchaseRepository.save(p);
            }
        }
        return null;
    }

    // ── Titles (display) ─────────────────────────────────────

    /** The title string a player currently displays next to their name, or null. */
    @Transactional(readOnly = true)
    public String getEquippedTitle(Player player) {
        PlayerPurchase equipped = null;
        PlayerPurchase fallback = null;
        for (PlayerPurchase p : playerPurchaseRepository.findByPlayerOrderByPurchasedAtDesc(player)) {
            if (p.getItem().getCategory() == StoreItem.Category.TITLE) {
                if (p.isEquipped()) { equipped = p; break; }
                if (fallback == null) fallback = p;
            }
        }
        PlayerPurchase chosen = equipped != null ? equipped : fallback;
        return chosen == null ? null : chosen.getItem().getName();
    }

    /** username → equipped title, for every player that owns a title. Used by rankings. */
    @Transactional(readOnly = true)
    public Map<String, String> getTitlesByUsername() {
        Map<String, String> titles = new HashMap<>();
        Map<String, String> equipped = new HashMap<>();
        // oldest first; later purchases overwrite earlier ones as the default fallback
        for (PlayerPurchase p : playerPurchaseRepository.findByItem_CategoryOrderByPurchasedAtAsc(StoreItem.Category.TITLE)) {
            String username = p.getPlayer().getUsername();
            titles.put(username, p.getItem().getName());
            if (p.isEquipped()) {
                equipped.put(username, p.getItem().getName());
            }
        }
        titles.putAll(equipped); // equipped title takes precedence
        return titles;
    }
}
