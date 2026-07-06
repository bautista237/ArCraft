package org.austral.ing.arcraft.web.model;

import java.time.Instant;
import java.util.UUID;

/** Read-model of a store_item row. Category stays an enum: templates call {@code .name()}. */
public class StoreItemView {

    public enum Category {
        SKIN, TITLE, COSMETIC;

        public static Category of(String raw) {
            if (raw == null) return COSMETIC;
            try {
                return valueOf(raw);
            } catch (IllegalArgumentException e) {
                return COSMETIC;
            }
        }
    }

    private final UUID id;
    private final String name;
    private final String description;
    private final long price;
    private final Category category;
    private final String effect;
    private final Instant createdAt;

    public StoreItemView(UUID id, String name, String description, long price,
                         String category, String effect, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = Category.of(category);
        this.effect = effect;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public long getPrice() { return price; }
    public Category getCategory() { return category; }
    public String getEffect() { return effect; }
    public Instant getCreatedAt() { return createdAt; }
}
