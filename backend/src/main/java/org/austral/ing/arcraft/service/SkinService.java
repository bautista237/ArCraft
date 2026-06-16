package org.austral.ing.arcraft.service;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.entity.ServerConfig;
import org.austral.ing.arcraft.repository.ServerConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds player skin-face URLs using minotar (crafatar has been unreliable).
 *
 * <p>Skin source depends on the Minecraft server's online-mode, which the mod records in
 * {@code server_config}:
 * <ul>
 *   <li><b>online-mode = true</b> (premium): render the official skin by the player's real
 *       Mojang UUID.</li>
 *   <li><b>online-mode = false</b> (cracked) or unknown: render by username, which matches what
 *       offline skin mods (OfflineSkins / SkinChanger / "SetSkin") display — the skin associated
 *       with that username.</li>
 * </ul>
 * Accessible from templates as {@code @skinService}.
 */
@Service
@RequiredArgsConstructor
public class SkinService {

    private static final long CACHE_MS = 30_000;

    private final ServerConfigRepository serverConfigRepository;

    private volatile Boolean cachedOnlineMode;
    private volatile long cachedAt;

    @Transactional(readOnly = true)
    public boolean isOnlineMode() {
        long now = System.currentTimeMillis();
        if (cachedOnlineMode == null || now - cachedAt > CACHE_MS) {
            cachedOnlineMode = serverConfigRepository.findAll().stream()
                    .map(ServerConfig::getOnlineMode)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(false); // default to cracked-friendly username skins
            cachedAt = now;
        }
        return cachedOnlineMode;
    }

    /** Face (with hat overlay) for a player at the given pixel size. */
    public String face(Player player, int size) {
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
