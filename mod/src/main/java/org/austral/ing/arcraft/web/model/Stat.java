package org.austral.ing.arcraft.web.model;

/** Per-player breakdown rows (block/item/mob), used on the profile page. */
public final class Stat {

    public record Block(String blockType, long mined, long placed) {}

    public record Item(String itemType, long count) {}

    public record Mob(String mobType, long count) {}

    private Stat() {
    }
}
