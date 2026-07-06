package org.austral.ing.arcraft.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves Minecraft inventory icons and pretty display names for namespaced ids
 * (e.g. {@code minecraft:crafting_table}) via nerothe's pre-rendered icon API; mobs use their
 * spawn egg. Also computes an icon's average colour (cached) for the dashboard pie charts.
 * Exposed to templates as {@code ${iconService...}} through {@code BaseModel}.
 */
public final class IconService {

    public static final IconService INSTANCE = new IconService();

    private static final String BASE = "https://mc.nerothe.com/img/1.21/minecraft_";
    private static final Logger log = LoggerFactory.getLogger(IconService.class);

    private static final Map<String, String> MOB_EMOJI = Map.ofEntries(
            Map.entry("zombie", "🧟"), Map.entry("husk", "🧟"), Map.entry("drowned", "🧟"),
            Map.entry("skeleton", "💀"), Map.entry("stray", "💀"), Map.entry("wither_skeleton", "💀"),
            Map.entry("creeper", "🧨"), Map.entry("spider", "🕷"), Map.entry("cave_spider", "🕷"),
            Map.entry("enderman", "🟪"), Map.entry("ender_dragon", "🐉"), Map.entry("wither", "☠"),
            Map.entry("warden", "🟦"), Map.entry("elder_guardian", "🐡"), Map.entry("guardian", "🐡"),
            Map.entry("blaze", "🔥"), Map.entry("ghast", "👻"), Map.entry("slime", "🟩"),
            Map.entry("magma_cube", "🟧"), Map.entry("piglin", "🐗"), Map.entry("hoglin", "🐗"),
            Map.entry("zombified_piglin", "🐗"), Map.entry("cow", "🐄"), Map.entry("pig", "🐖"),
            Map.entry("sheep", "🐑"), Map.entry("chicken", "🐔"), Map.entry("villager", "🧑‍🌾"),
            Map.entry("phantom", "🦇"), Map.entry("bee", "🐝"));

    private final Map<String, String> colorCache = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private IconService() {
    }

    public String stripNs(String id) {
        if (id == null) return "";
        int i = id.indexOf(':');
        return i >= 0 ? id.substring(i + 1) : id;
    }

    /** "minecraft:crafting_table" → "Crafting Table". */
    public String pretty(String id) {
        String name = stripNs(id).replace('_', ' ').trim();
        if (name.isEmpty()) return id;
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    /** Inventory icon for any item or block id (nerothe renders both). */
    public String icon(String id) {
        return BASE + stripNs(id) + ".png";
    }

    public String itemIcon(String id) {
        return icon(id);
    }

    public String blockIcon(String id) {
        return icon(id);
    }

    /** Mob icon via its spawn egg (nerothe has one for every mob, incl. ender_dragon). */
    public String mobIcon(String id) {
        return BASE + stripNs(id) + "_spawn_egg.png";
    }

    public String mobEmoji(String id) {
        return MOB_EMOJI.getOrDefault(stripNs(id), "⚔");
    }

    public String color(String id) {
        return colorForUrl(icon(id));
    }

    public String mobColor(String id) {
        return colorForUrl(mobIcon(id));
    }

    /** Average RGB of the (mostly-opaque pixels of the) icon, as a hex string; cached. */
    public String colorForUrl(String url) {
        return colorCache.computeIfAbsent(url, this::computeColor);
    }

    private String computeColor(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(4)).GET().build();
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() / 100 != 2) return "#888888";
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(res.body()));
            if (img == null) return "#888888";
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getRGB(x, y);
                    int a = (argb >>> 24) & 0xff;
                    if (a < 32) continue; // skip transparent
                    r += (argb >> 16) & 0xff;
                    g += (argb >> 8) & 0xff;
                    b += argb & 0xff;
                    n++;
                }
            }
            if (n == 0) return "#888888";
            return String.format("#%02x%02x%02x", (int) (r / n), (int) (g / n), (int) (b / n));
        } catch (Exception e) {
            log.debug("color compute failed for {}: {}", url, e.toString());
            return "#888888";
        }
    }
}
