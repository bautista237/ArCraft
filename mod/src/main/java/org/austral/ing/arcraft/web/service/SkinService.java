package org.austral.ing.arcraft.web.service;

import org.austral.ing.arcraft.db.Database;
import org.austral.ing.arcraft.web.model.PlayerView;

/**
 * Builds player skin-face URLs using minotar. Skin source depends on the server's online-mode
 * (recorded by the mod in {@code server_config}): premium servers render by real Mojang UUID,
 * cracked servers by username (matching offline skin mods). Templates call this via
 * {@code ${skinService.face(player, size)}}.
 */
public final class SkinService {

    public static final SkinService INSTANCE = new SkinService();

    private static final long CACHE_MS = 30_000;

    private volatile Boolean cachedOnlineMode;
    private volatile long cachedAt;

    private SkinService() {
    }

    public boolean isOnlineMode() {
        long now = System.currentTimeMillis();
        if (cachedOnlineMode == null || now - cachedAt > CACHE_MS) {
            cachedOnlineMode = Database.jdbi().withHandle(h -> h
                    .createQuery("SELECT online_mode FROM server_config")
                    .map((rs, c) -> (Boolean) (Object) rs.getBoolean("online_mode"))
                    .findFirst()
                    .orElse(Boolean.FALSE));
            cachedAt = now;
        }
        return Boolean.TRUE.equals(cachedOnlineMode);
    }

    /** Face (with hat overlay) for a player at the given pixel size. */
    public String face(PlayerView player, int size) {
        if (player == null) return faceByUsername("Steve", size);
        String key = isOnlineMode() ? player.getId().toString() : player.getUsername();
        return minotarHelm(key, size);
    }

    public String faceByUsername(String username, int size) {
        return minotarHelm(username == null ? "Steve" : username, size);
    }

    private String minotarHelm(String key, int size) {
        return "https://minotar.net/helm/" + key + "/" + size + ".png";
    }
}
