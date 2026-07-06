package org.austral.ing.arcraft.web.service;

import java.util.Arrays;
import java.util.List;

/**
 * The fixed coin bundles a player can buy with real (test) money via MercadoPago.
 * Prices are in the configured currency (ARS by default). Kept small and static so the
 * checkout has no admin surface — tweak the values here if needed.
 */
public enum CoinPackage {
    SMALL ("starter",  100,   500.0),
    MEDIUM("builder",  500,  2000.0),
    LARGE ("veteran", 1200,  4000.0),
    MEGA  ("legend",  3000,  9000.0);

    public final String slug;
    public final long coins;
    public final double price;

    CoinPackage(String slug, long coins, double price) {
        this.slug = slug;
        this.coins = coins;
        this.price = price;
    }

    public static CoinPackage bySlug(String slug) {
        return Arrays.stream(values()).filter(p -> p.slug.equalsIgnoreCase(slug)).findFirst().orElse(null);
    }

    public static List<CoinPackage> all() {
        return Arrays.asList(values());
    }

    /** Coins per currency unit, for a "best value" hint in the UI. */
    public double coinsPerUnit() {
        return coins / price;
    }

    // Explicit getters: templates read pack.slug/coins/price via OGNL property syntax.
    public String getSlug() { return slug; }
    public long getCoins() { return coins; }
    public double getPrice() { return price; }
    public double getCoinsPerUnit() { return coinsPerUnit(); }
}
