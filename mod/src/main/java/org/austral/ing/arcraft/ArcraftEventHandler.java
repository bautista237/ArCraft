package org.austral.ing.arcraft;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
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

    // username → tick count; fires chunk tracking every 20 ticks (1 second)
    private static final ConcurrentHashMap<String, Integer> TICK_COUNTER = new ConcurrentHashMap<>();

    private ArcraftEventHandler() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        final String username = player.getName().getString();
        final UUID mcUuid = player.getUUID();
        LOGGER.info("[ArCraft] PlayerLogin: {} ({})", username, mcUuid);
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
            LOGGER.info("[ArCraft] PvP kill: {} killed {} with {}", killerName, victimName, weapon);
            DatabaseManager.submit(() -> recordPvpKill(killerName, victimName, weapon, when));
            return;
        }

        if (attacker instanceof Player killerPlayer && !(victim instanceof Player)) {
            final String killerName = killerPlayer.getName().getString();
            final String mobType = entityKey(victim);
            LOGGER.info("[ArCraft] MobKill: {} killed {}", killerName, mobType);
            DatabaseManager.submit(() -> recordMobKill(killerName, mobType));
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        final String username = player.getName().getString();
        final String blockType = blockKey(event.getState());
        LOGGER.info("[ArCraft] BlockBreak: {} mined {}", username, blockType);
        DatabaseManager.submit(() -> recordBlockBreak(username, blockType));
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        final String username = player.getName().getString();
        final String blockType = blockKey(event.getPlacedBlock());
        LOGGER.info("[ArCraft] BlockPlace: {} placed {}", username, blockType);
        DatabaseManager.submit(() -> recordBlockPlace(username, blockType));
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        final String username = player.getName().getString();
        ItemStack stack = event.getCrafting();
        final long count = stack.getCount();
        final String itemType = itemKey(stack);
        LOGGER.info("[ArCraft] ItemCrafted: {} crafted {}x {}", username, count, itemType);
        DatabaseManager.submit(() -> recordItemCrafted(username, itemType, count));
    }

    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        Player player = event.getEntity();
        final String username = player.getName().getString();
        LOGGER.info("[ArCraft] ArrowLoose: {} fired a shot", username);
        DatabaseManager.submit(() -> incrementShotsFired(username));
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!(projectile instanceof AbstractArrow)) return;
        Entity owner = projectile.getOwner();
        if (!(owner instanceof Player shooter)) return;
        final String username = shooter.getName().getString();
        LOGGER.info("[ArCraft] ProjectileImpact: {}'s arrow hit {}", username,
                event.getRayTraceResult().getType());
        DatabaseManager.submit(() -> incrementShotsHit(username));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        final String username = player.getName().getString();
        int ticks = TICK_COUNTER.merge(username, 1, Integer::sum);
        if (ticks < 20) return;
        TICK_COUNTER.put(username, 0);

        // Skip if player not yet in cache (login event hasn't fired yet)
        if (!PLAYER_ID_CACHE.containsKey(username)) return;

        final BlockPos pos = player.blockPosition();
        final int chunkX = pos.getX() >> 4;
        final int chunkZ = pos.getZ() >> 4;
        final String dimension = player.level().dimension().location().toString();

        final String biome;
        try {
            biome = player.level().getBiome(pos)
                    .unwrapKey()
                    .map(k -> k.location().toString())
                    .orElse("unknown");
        } catch (Exception e) {
            return;
        }

        final String topBlockName;
        final int[] color;
        try {
            int cx = chunkX * 16 + 8;
            int cz = chunkZ * 16 + 8;
            int y = player.level().getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz);
            if (y <= 0) return;
            BlockState topState = player.level().getBlockState(new BlockPos(cx, y - 1, cz));
            ResourceLocation blockRl = BuiltInRegistries.BLOCK.getKey(topState.getBlock());
            topBlockName = blockRl != null ? blockRl.toString() : "minecraft:air";
            color = blockColor(topBlockName);
        } catch (Exception e) {
            return;
        }

        LOGGER.info("[ArCraft] ChunkVisit: {} at chunk ({},{}) in {} biome={} top={}",
                username, chunkX, chunkZ, dimension, biome, topBlockName);
        DatabaseManager.submit(() ->
                recordChunkVisit(username, chunkX, chunkZ, dimension, biome, topBlockName,
                        color[0], color[1], color[2]));
    }

    // --- DB operations (run on the writer thread) ---

    private static void upsertPlayer(String username, UUID mcUuid) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID existing = lookupPlayerId(c, username);
            String dbResult;
            if (existing == null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO player (id, username, password_hash, is_admin, created_at) VALUES (?, ?, '', FALSE, ?)")) {
                    ps.setObject(1, mcUuid);
                    ps.setString(2, username);
                    ps.setTimestamp(3, Timestamp.from(Instant.now()));
                    ps.executeUpdate();
                }
                existing = mcUuid;
                dbResult = "created";
            } else {
                dbResult = "cached";
            }
            PLAYER_ID_CACHE.put(username, existing);
            LOGGER.info("[ArCraft] PLAYER_JOIN: username={}, result={}", username, dbResult);

            // H2 does not apply column DEFAULTs in INSERT...SELECT for unspecified NOT NULL
            // columns, so we must list every column explicitly with its default value.
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO player_stats (" +
                    "  id, player_id, kills, deaths, damage_dealt, damage_received," +
                    "  mobs_killed, blocks_placed, blocks_mined, items_crafted," +
                    "  distance_walked, distance_swum, distance_flown, distance_sailed," +
                    "  shots_fired, shots_hit, longest_shot_blocks" +
                    ") SELECT ?,?,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0" +
                    "  WHERE NOT EXISTS (SELECT 1 FROM player_stats WHERE player_id = ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, existing);
                ps.setObject(3, existing);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            LOGGER.error("[ArCraft] PLAYER_JOIN: username={}, result=error", username, e);
        }
    }

    private static void recordPvpKill(String killerName, String victimName, String weapon, Instant when) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID killerId = resolvePlayerId(c, killerName);
            UUID victimId = resolvePlayerId(c, victimName);
            if (killerId == null || victimId == null) {
                LOGGER.error("[ArCraft] PVP_KILL: username={}, victim={}, result=error (player not found in DB)",
                        killerName, victimName);
                return;
            }

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
            LOGGER.info("[ArCraft] PVP_KILL: username={}, victim={}, weapon={}, result=recorded",
                    killerName, victimName, weapon);
        } catch (Exception e) {
            LOGGER.error("[ArCraft] PVP_KILL: username={}, victim={}, result=error", killerName, victimName, e);
        }
    }

    private static void recordMobKill(String killerName, String mobType) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID playerId = resolvePlayerId(c, killerName);
            if (playerId == null) {
                LOGGER.error("[ArCraft] MOB_KILL: username={}, mob={}, result=error (player not in DB)", killerName, mobType);
                return;
            }
            incrementStat(c, playerId, "mobs_killed", 1);
            upsertMobEntry(c, playerId, mobType);
            LOGGER.info("[ArCraft] MOB_KILL: username={}, mob={}, result=updated", killerName, mobType);
        } catch (Exception e) {
            LOGGER.error("[ArCraft] MOB_KILL: username={}, mob={}, result=error", killerName, mobType, e);
        }
    }

    private static void recordBlockBreak(String username, String blockType) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID playerId = resolvePlayerId(c, username);
            if (playerId == null) {
                LOGGER.error("[ArCraft] BLOCK_BREAK: username={}, block={}, result=error (player not in DB)", username, blockType);
                return;
            }
            incrementStat(c, playerId, "blocks_mined", 1);
            upsertBlockEntry(c, playerId, blockType, "mined");
            LOGGER.info("[ArCraft] BLOCK_BREAK: username={}, block={}, result=updated", username, blockType);
        } catch (Exception e) {
            LOGGER.error("[ArCraft] BLOCK_BREAK: username={}, block={}, result=error", username, blockType, e);
        }
    }

    private static void recordBlockPlace(String username, String blockType) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID playerId = resolvePlayerId(c, username);
            if (playerId == null) {
                LOGGER.error("[ArCraft] BLOCK_PLACE: username={}, block={}, result=error (player not in DB)", username, blockType);
                return;
            }
            incrementStat(c, playerId, "blocks_placed", 1);
            upsertBlockEntry(c, playerId, blockType, "placed");
            LOGGER.info("[ArCraft] BLOCK_PLACE: username={}, block={}, result=updated", username, blockType);
        } catch (Exception e) {
            LOGGER.error("[ArCraft] BLOCK_PLACE: username={}, block={}, result=error", username, blockType, e);
        }
    }

    private static void recordItemCrafted(String username, String itemType, long count) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID playerId = resolvePlayerId(c, username);
            if (playerId == null) {
                LOGGER.error("[ArCraft] ITEM_CRAFTED: username={}, item={}, result=error (player not in DB)", username, itemType);
                return;
            }
            incrementStat(c, playerId, "items_crafted", count);
            upsertItemEntry(c, playerId, itemType, count);
            LOGGER.info("[ArCraft] ITEM_CRAFTED: username={}, item={}, count={}, result=updated", username, itemType, count);
        } catch (Exception e) {
            LOGGER.error("[ArCraft] ITEM_CRAFTED: username={}, item={}, result=error", username, itemType, e);
        }
    }

    private static void incrementShotsFired(String username) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID playerId = resolvePlayerId(c, username);
            if (playerId == null) {
                LOGGER.error("[ArCraft] ARROW_FIRED: username={}, result=error (player not in DB)", username);
                return;
            }
            incrementStat(c, playerId, "shots_fired", 1);
            LOGGER.info("[ArCraft] ARROW_FIRED: username={}, result=updated", username);
        } catch (Exception e) {
            LOGGER.error("[ArCraft] ARROW_FIRED: username={}, result=error", username, e);
        }
    }

    private static void incrementShotsHit(String username) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID playerId = resolvePlayerId(c, username);
            if (playerId == null) {
                LOGGER.error("[ArCraft] ARROW_HIT: username={}, result=error (player not in DB)", username);
                return;
            }
            incrementStat(c, playerId, "shots_hit", 1);
            LOGGER.info("[ArCraft] ARROW_HIT: username={}, result=updated", username);
        } catch (Exception e) {
            LOGGER.error("[ArCraft] ARROW_HIT: username={}, result=error", username, e);
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

    private static void recordChunkVisit(String username, int chunkX, int chunkZ,
                                         String dimension, String biome, String topBlock,
                                         int r, int g, int b) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID playerId = resolvePlayerId(c, username);
            if (playerId == null) {
                LOGGER.error("[ArCraft] CHUNK_VISIT: username={}, chunk=({},{}), result=error (player not in DB)",
                        username, chunkX, chunkZ);
                return;
            }

            java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());
            int inserted;

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO chunk_visit (id, player_id, chunk_x, chunk_z, dimension, biome, top_block, " +
                    "map_color_r, map_color_g, map_color_b, first_visited, last_visited) " +
                    "SELECT ?,?,?,?,?,?,?,?,?,?,?,? WHERE NOT EXISTS (" +
                    "SELECT 1 FROM chunk_visit WHERE player_id=? AND chunk_x=? AND chunk_z=? AND dimension=?)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, playerId.toString());
                ps.setInt(3, chunkX);
                ps.setInt(4, chunkZ);
                ps.setString(5, dimension);
                ps.setString(6, biome);
                ps.setString(7, topBlock);
                ps.setInt(8, r);
                ps.setInt(9, g);
                ps.setInt(10, b);
                ps.setTimestamp(11, now);
                ps.setTimestamp(12, now);
                ps.setString(13, playerId.toString());
                ps.setInt(14, chunkX);
                ps.setInt(15, chunkZ);
                ps.setString(16, dimension);
                inserted = ps.executeUpdate();
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE chunk_visit SET last_visited=?, biome=?, top_block=?, " +
                    "map_color_r=?, map_color_g=?, map_color_b=? " +
                    "WHERE player_id=? AND chunk_x=? AND chunk_z=? AND dimension=?")) {
                ps.setTimestamp(1, now);
                ps.setString(2, biome);
                ps.setString(3, topBlock);
                ps.setInt(4, r);
                ps.setInt(5, g);
                ps.setInt(6, b);
                ps.setString(7, playerId.toString());
                ps.setInt(8, chunkX);
                ps.setInt(9, chunkZ);
                ps.setString(10, dimension);
                ps.executeUpdate();
            }
            LOGGER.info("[ArCraft] CHUNK_VISIT: username={}, chunk=({},{}), dim={}, biome={}, top={}, result={}",
                    username, chunkX, chunkZ, dimension, biome, topBlock,
                    inserted > 0 ? "created" : "updated");
        } catch (Exception e) {
            LOGGER.error("[ArCraft] CHUNK_VISIT: username={}, chunk=({},{}), result=error", username, chunkX, chunkZ, e);
        }
    }

    private static int[] blockColor(String blockName) {
        if (blockName == null) return new int[]{100, 100, 100};
        if (blockName.contains("grass_block"))  return new int[]{106, 127,  75};
        if (blockName.contains("water"))         return new int[]{ 63, 118, 228};
        if (blockName.contains("sand"))          return new int[]{219, 207, 163};
        if (blockName.contains("gravel"))        return new int[]{136, 126, 126};
        if (blockName.contains("snow"))          return new int[]{240, 240, 240};
        if (blockName.contains("ice"))           return new int[]{160, 205, 255};
        if (blockName.contains("_log"))          return new int[]{102,  76,  51};
        if (blockName.contains("_leaves"))       return new int[]{ 84, 109,  54};
        if (blockName.contains("netherrack"))    return new int[]{110,  50,  50};
        if (blockName.contains("soul_sand"))     return new int[]{ 78,  62,  50};
        if (blockName.contains("end_stone"))     return new int[]{219, 222, 158};
        if (blockName.contains("stone"))         return new int[]{125, 125, 125};
        return new int[]{100, 100, 100};
    }
}
