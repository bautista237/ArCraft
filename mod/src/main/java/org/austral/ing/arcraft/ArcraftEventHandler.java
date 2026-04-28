package org.austral.ing.arcraft;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = ArcraftMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ArcraftEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    // username → player.id (the row PK in the player table)
    private static final ConcurrentHashMap<String, UUID> PLAYER_ID_CACHE = new ConcurrentHashMap<>();

    private ArcraftEventHandler() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        final String username = player.getName().getString();
        final UUID mcUuid = player.getUUID();

        DatabaseManager.submit(() -> upsertPlayer(username, mcUuid));
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        Instant when = Instant.now();

        if (victim instanceof Player victimPlayer && attacker instanceof Player killerPlayer) {
            final String victimName = victimPlayer.getName().getString();
            final String killerName = killerPlayer.getName().getString();
            final String weapon = itemKey(killerPlayer.getMainHandItem());
            DatabaseManager.submit(() -> recordPvpKill(killerName, victimName, weapon, when));
            return;
        }

        if (attacker instanceof Player killerPlayer && !(victim instanceof Player)) {
            final String killerName = killerPlayer.getName().getString();
            final String mobType = entityKey(victim);
            DatabaseManager.submit(() -> recordMobKill(killerName, mobType));
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        final String username = player.getName().getString();
        final String blockType = blockKey(event.getState());
        DatabaseManager.submit(() -> recordBlockBreak(username, blockType));
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        final String username = player.getName().getString();
        final String blockType = blockKey(event.getPlacedBlock());
        DatabaseManager.submit(() -> recordBlockPlace(username, blockType));
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        final String username = player.getName().getString();
        ItemStack stack = event.getCrafting();
        final long count = stack.getCount();
        final String itemType = itemKey(stack);
        DatabaseManager.submit(() -> recordItemCrafted(username, itemType, count));
    }

    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        Player player = event.getEntity();
        final String username = player.getName().getString();
        DatabaseManager.submit(() -> incrementShotsFired(username));
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!(projectile instanceof AbstractArrow)) return;
        Entity owner = projectile.getOwner();
        if (!(owner instanceof Player shooter)) return;
        final String username = shooter.getName().getString();
        DatabaseManager.submit(() -> incrementShotsHit(username));
    }

    // --- DB operations (run on the writer thread) ---

    private static void upsertPlayer(String username, UUID mcUuid) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID existing = lookupPlayerId(c, username);
            if (existing == null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO player (id, username, password_hash, is_admin, created_at) VALUES (?, ?, '', FALSE, ?)")) {
                    ps.setObject(1, mcUuid);
                    ps.setString(2, username);
                    ps.setTimestamp(3, Timestamp.from(Instant.now()));
                    ps.executeUpdate();
                }
                existing = mcUuid;
                LOGGER.info("[ArCraft] Inserted new player {}", username);
            }
            PLAYER_ID_CACHE.put(username, existing);

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO player_stats (id, player_id) SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM player_stats WHERE player_id = ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, existing);
                ps.setObject(3, existing);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            LOGGER.error("[ArCraft] upsertPlayer failed for {}", username, e);
        }
    }

    private static void recordPvpKill(String killerName, String victimName, String weapon, Instant when) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID killerId = resolvePlayerId(c, killerName);
            UUID victimId = resolvePlayerId(c, victimName);
            if (killerId == null || victimId == null) return;

            incrementStat(c, killerId, "kills", 1);
            incrementStat(c, victimId, "deaths", 1);

            UUID pvpEventId = UUID.randomUUID();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO pvp_event (id, killer_id, victim_id, started_at, ended_at) VALUES (?, ?, ?, ?, ?)")) {
                ps.setObject(1, pvpEventId);
                ps.setObject(2, killerId);
                ps.setObject(3, victimId);
                ps.setTimestamp(4, Timestamp.from(when));
                ps.setTimestamp(5, Timestamp.from(when));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO pvp_hit (id, pvp_event_id, attacker_id, damage, weapon, hit_at) VALUES (?, ?, ?, 0, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, pvpEventId);
                ps.setObject(3, killerId);
                ps.setString(4, weapon);
                ps.setTimestamp(5, Timestamp.from(when));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO event_log (id, type, description, player_id, occurred_at) VALUES (?, 'PVP_KILL', ?, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, killerName + " killed " + victimName + " with " + weapon);
                ps.setObject(3, killerId);
                ps.setTimestamp(4, Timestamp.from(when));
                ps.executeUpdate();
            }
        } catch (Exception e) {
            LOGGER.error("[ArCraft] recordPvpKill failed", e);
        }
    }

    private static void recordMobKill(String killerName, String mobType) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID playerId = resolvePlayerId(c, killerName);
            if (playerId == null) return;
            incrementStat(c, playerId, "mobs_killed", 1);
            upsertMobEntry(c, playerId, mobType);
        } catch (Exception e) {
            LOGGER.error("[ArCraft] recordMobKill failed", e);
        }
    }

    private static void recordBlockBreak(String username, String blockType) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID playerId = resolvePlayerId(c, username);
            if (playerId == null) return;
            incrementStat(c, playerId, "blocks_mined", 1);
            upsertBlockEntry(c, playerId, blockType, "mined");
        } catch (Exception e) {
            LOGGER.error("[ArCraft] recordBlockBreak failed", e);
        }
    }

    private static void recordBlockPlace(String username, String blockType) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID playerId = resolvePlayerId(c, username);
            if (playerId == null) return;
            incrementStat(c, playerId, "blocks_placed", 1);
            upsertBlockEntry(c, playerId, blockType, "placed");
        } catch (Exception e) {
            LOGGER.error("[ArCraft] recordBlockPlace failed", e);
        }
    }

    private static void recordItemCrafted(String username, String itemType, long count) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID playerId = resolvePlayerId(c, username);
            if (playerId == null) return;
            incrementStat(c, playerId, "items_crafted", count);
            upsertItemEntry(c, playerId, itemType, count);
        } catch (Exception e) {
            LOGGER.error("[ArCraft] recordItemCrafted failed", e);
        }
    }

    private static void incrementShotsFired(String username) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID playerId = resolvePlayerId(c, username);
            if (playerId == null) return;
            incrementStat(c, playerId, "shots_fired", 1);
        } catch (Exception e) {
            LOGGER.error("[ArCraft] incrementShotsFired failed", e);
        }
    }

    private static void incrementShotsHit(String username) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID playerId = resolvePlayerId(c, username);
            if (playerId == null) return;
            incrementStat(c, playerId, "shots_hit", 1);
        } catch (Exception e) {
            LOGGER.error("[ArCraft] incrementShotsHit failed", e);
        }
    }

    // --- helpers ---

    private static void incrementStat(Connection c, UUID playerId, String column, long delta) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE player_stats SET " + column + " = " + column + " + ? WHERE player_id = ?")) {
            ps.setLong(1, delta);
            ps.setObject(2, playerId);
            ps.executeUpdate();
        }
    }

    private static void upsertBlockEntry(Connection c, UUID playerId, String blockType, String column) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO block_stat_entry (id, player_id, block_type, mined, placed) " +
                        "SELECT ?, ?, ?, 0, 0 WHERE NOT EXISTS (SELECT 1 FROM block_stat_entry WHERE player_id = ? AND block_type = ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, playerId);
            ps.setString(3, blockType);
            ps.setObject(4, playerId);
            ps.setString(5, blockType);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE block_stat_entry SET " + column + " = " + column + " + 1 WHERE player_id = ? AND block_type = ?")) {
            ps.setObject(1, playerId);
            ps.setString(2, blockType);
            ps.executeUpdate();
        }
    }

    private static void upsertItemEntry(Connection c, UUID playerId, String itemType, long delta) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO item_stat_entry (id, player_id, item_type, count) " +
                        "SELECT ?, ?, ?, 0 WHERE NOT EXISTS (SELECT 1 FROM item_stat_entry WHERE player_id = ? AND item_type = ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, playerId);
            ps.setString(3, itemType);
            ps.setObject(4, playerId);
            ps.setString(5, itemType);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE item_stat_entry SET count = count + ? WHERE player_id = ? AND item_type = ?")) {
            ps.setLong(1, delta);
            ps.setObject(2, playerId);
            ps.setString(3, itemType);
            ps.executeUpdate();
        }
    }

    private static void upsertMobEntry(Connection c, UUID playerId, String mobType) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO mob_stat_entry (id, player_id, mob_type, count) " +
                        "SELECT ?, ?, ?, 0 WHERE NOT EXISTS (SELECT 1 FROM mob_stat_entry WHERE player_id = ? AND mob_type = ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, playerId);
            ps.setString(3, mobType);
            ps.setObject(4, playerId);
            ps.setString(5, mobType);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE mob_stat_entry SET count = count + 1 WHERE player_id = ? AND mob_type = ?")) {
            ps.setObject(1, playerId);
            ps.setString(2, mobType);
            ps.executeUpdate();
        }
    }

    private static UUID resolvePlayerId(Connection c, String username) throws Exception {
        UUID cached = PLAYER_ID_CACHE.get(username);
        if (cached != null) return cached;
        UUID found = lookupPlayerId(c, username);
        if (found != null) PLAYER_ID_CACHE.put(username, found);
        return found;
    }

    private static UUID lookupPlayerId(Connection c, String username) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM player WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return (UUID) rs.getObject(1);
            }
        }
        return null;
    }

    private static String blockKey(BlockState state) {
        ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return rl == null ? "minecraft:air" : rl.toString();
    }

    private static String itemKey(ItemStack stack) {
        if (stack.isEmpty()) return "minecraft:air";
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return rl == null ? "minecraft:air" : rl.toString();
    }

    private static String entityKey(Entity entity) {
        ResourceLocation rl = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return rl == null ? "minecraft:unknown" : rl.toString();
    }
}
