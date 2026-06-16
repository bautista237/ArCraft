package org.austral.ing.arcraft;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
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

    // Owned store cosmetic effects per player, and a refresh counter, for in-game perks.
    private static final ConcurrentHashMap<String, java.util.Set<String>> EFFECTS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> EFFECT_REFRESH = new ConcurrentHashMap<>();

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
    public static void onServerChat(net.neoforged.neoforge.event.ServerChatEvent event) {
        final String username = event.getPlayer().getName().getString();
        final String text = event.getRawText();
        if (text == null || text.isBlank()) return;
        DatabaseManager.submit(() -> recordChat(username, text.trim()));
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String dim = event.getTo().location().getPath();
        final String label;
        if (dim.equals("the_nether")) label = "The Nether";
        else if (dim.equals("the_end")) label = "The End";
        else return;
        final String username = player.getName().getString();
        DatabaseManager.submit(() -> recordDimensionFirst(username, label));
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        Instant when = Instant.now();

        if (victim instanceof Player victimPlayer) {
            final String victimName = victimPlayer.getName().getString();
            if (attacker instanceof Player killerPlayer) {
                final String killerName = killerPlayer.getName().getString();
                final String weapon = itemKey(killerPlayer.getMainHandItem());
                LOGGER.info("[ArCraft] PvP kill: {} killed {} with {}", killerName, victimName, weapon);
                DatabaseManager.submit(() -> recordPvpKill(killerName, victimName, weapon, when));
            } else {
                // Non-PvP death (mob, fall, lava…): log it with the vanilla death message.
                String msg;
                try {
                    msg = event.getSource().getLocalizedDeathMessage(victimPlayer).getString();
                } catch (Exception e) {
                    msg = victimName + " died";
                }
                final String deathMsg = msg;
                LOGGER.info("[ArCraft] PlayerDeath: {}", deathMsg);
                DatabaseManager.submit(() -> recordPlayerDeath(victimName, deathMsg));
            }
            return;
        }

        if (attacker instanceof Player killerPlayer) { // victim is a non-player
            final String killerName = killerPlayer.getName().getString();
            final String mobType = entityKey(victim);
            LOGGER.info("[ArCraft] MobKill: {} killed {}", killerName, mobType);
            DatabaseManager.submit(() -> recordMobKill(killerName, mobType));
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (!(event.getEntity() instanceof Player victim) || !(attacker instanceof Player attackerPlayer)) return;
        final float amount = event.getAmount();
        if (amount <= 0) return;
        final String victimName = victim.getName().getString();
        final String attackerName = attackerPlayer.getName().getString();
        DatabaseManager.submit(() -> recordPvpDamage(attackerName, victimName, amount));
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        final String username = player.getName().getString();
        final String blockType = blockKey(event.getState());
        final BlockPos pos = event.getPos();
        final int chunkX = pos.getX() >> 4;
        final int chunkZ = pos.getZ() >> 4;
        final String dimension = player.level().dimension().location().toString();
        LOGGER.info("[ArCraft] BlockBreak: {} mined {}", username, blockType);
        DatabaseManager.submit(() -> {
            recordBlockBreak(username, blockType);
            recordChunkActivity(username, chunkX, chunkZ, dimension, "blocks_mined");
        });
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        final String username = player.getName().getString();
        final String blockType = blockKey(event.getPlacedBlock());
        final BlockPos pos = event.getPos();
        final int chunkX = pos.getX() >> 4;
        final int chunkZ = pos.getZ() >> 4;
        final String dimension = player.level().dimension().location().toString();
        LOGGER.info("[ArCraft] BlockPlace: {} placed {}", username, blockType);
        DatabaseManager.submit(() -> {
            recordBlockPlace(username, blockType);
            recordChunkActivity(username, chunkX, chunkZ, dimension, "blocks_placed");
        });
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

    // Arrow UUIDs already counted as a hit, so a single arrow can't register many hits
    // (ProjectileImpactEvent can fire repeatedly while an arrow is embedded/grazing).
    private static final java.util.Set<UUID> COUNTED_ARROW_HITS = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof Player shooter)) return;
        // A "hit" only counts when the arrow strikes a living entity (not blocks), once per arrow.
        if (event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity hit
                && hit != shooter) {
            if (COUNTED_ARROW_HITS.add(arrow.getUUID())) {
                if (COUNTED_ARROW_HITS.size() > 20000) COUNTED_ARROW_HITS.clear();
                final String username = shooter.getName().getString();
                LOGGER.info("[ArCraft] ARROW_HIT (entity): username={}", username);
                DatabaseManager.submit(() -> incrementShotsHit(username));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        final String username = player.getName().getString();

        // Cosmetic particle trails: spawn every tick for a smooth trail (reads the cache only).
        java.util.Set<String> owned = EFFECTS_CACHE.get(username);
        if (owned != null && !owned.isEmpty()) spawnTrails(player, owned);

        int ticks = TICK_COUNTER.merge(username, 1, Integer::sum);
        if (ticks < 20) return;
        TICK_COUNTER.put(username, 0);

        // Skip if player not yet in cache (login event hasn't fired yet)
        if (!PLAYER_ID_CACHE.containsKey(username)) return;

        // Mirror vanilla stats (distance, total deaths, total damage) into player_stats.
        flushVanillaStats(player, username);

        // Refresh owned cosmetics from the DB and re-apply the glow aura.
        refreshAndApplyAura(player, username);

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
        final int surfaceY;
        try {
            int cx = chunkX * 16 + 8;
            int cz = chunkZ * 16 + 8;
            int y = player.level().getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz);
            if (y <= 0) return;
            surfaceY = y;
            BlockState topState = player.level().getBlockState(new BlockPos(cx, y - 1, cz));
            ResourceLocation blockRl = BuiltInRegistries.BLOCK.getKey(topState.getBlock());
            topBlockName = blockRl != null ? blockRl.toString() : "minecraft:air";
            color = blockColor(topBlockName);
        } catch (Exception e) {
            return;
        }

        DatabaseManager.submit(() ->
                recordChunkVisit(username, chunkX, chunkZ, dimension, biome, topBlockName,
                        color[0], color[1], color[2], surfaceY));
    }

    // Mirror authoritative vanilla statistics (distances in cm → blocks; damage in 1/10 HP → HP;
    // total deaths) into player_stats. These are accurate and cover every cause, unlike events.
    private static void flushVanillaStats(ServerPlayer player, String username) {
        try {
            long walkedCm = stat(player, Stats.WALK_ONE_CM) + stat(player, Stats.SPRINT_ONE_CM)
                    + stat(player, Stats.CROUCH_ONE_CM) + stat(player, Stats.WALK_ON_WATER_ONE_CM);
            long swumCm   = stat(player, Stats.SWIM_ONE_CM) + stat(player, Stats.WALK_UNDER_WATER_ONE_CM);
            long flownCm  = stat(player, Stats.FLY_ONE_CM) + stat(player, Stats.AVIATE_ONE_CM);
            long sailedCm = stat(player, Stats.BOAT_ONE_CM);
            final long walked = walkedCm / 100, swum = swumCm / 100, flown = flownCm / 100, sailed = sailedCm / 100;
            final long deaths = stat(player, Stats.DEATHS);
            final float dmgDealt = stat(player, Stats.DAMAGE_DEALT) / 10f;
            final float dmgTaken = stat(player, Stats.DAMAGE_TAKEN) / 10f;
            DatabaseManager.submit(() ->
                    setVanillaStats(username, walked, swum, flown, sailed, deaths, dmgDealt, dmgTaken));
        } catch (Exception e) {
            LOGGER.error("[ArCraft] flushVanillaStats failed for {}", username, e);
        }
    }

    private static long stat(ServerPlayer player, net.minecraft.resources.ResourceLocation key) {
        return player.getStats().getValue(Stats.CUSTOM.get(key));
    }

    private static void setVanillaStats(String username, long walked, long swum, long flown, long sailed,
                                        long deaths, float dmgDealt, float dmgTaken) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID playerId = resolvePlayerId(c, username);
            if (playerId == null) return;
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE player_stats SET distance_walked=?, distance_swum=?, distance_flown=?, distance_sailed=?, " +
                    "deaths=?, damage_dealt=?, damage_received=? WHERE player_id=?")) {
                ps.setLong(1, walked);
                ps.setLong(2, swum);
                ps.setLong(3, flown);
                ps.setLong(4, sailed);
                ps.setLong(5, deaths);
                ps.setFloat(6, dmgDealt);
                ps.setFloat(7, dmgTaken);
                ps.setObject(8, playerId);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            LOGGER.error("[ArCraft] setVanillaStats failed for {}", username, e);
        }
    }

    private static void incrementStatF(Connection c, UUID playerId, String column, float delta) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE player_stats SET " + column + " = " + column + " + ? WHERE player_id = ?")) {
            ps.setFloat(1, delta);
            ps.setObject(2, playerId);
            ps.executeUpdate();
        }
    }

    // --- Store cosmetic effects ---

    private static void refreshAndApplyAura(ServerPlayer player, String username) {
        // Refresh the owned-cosmetics cache from the DB every ~10s (off the main thread).
        int n = EFFECT_REFRESH.merge(username, 1, Integer::sum);
        if (n >= 10 || !EFFECTS_CACHE.containsKey(username)) {
            EFFECT_REFRESH.put(username, 0);
            DatabaseManager.submit(() -> loadEffects(username));
        }
        java.util.Set<String> effects = EFFECTS_CACHE.getOrDefault(username, java.util.Set.of());
        if (effects.contains("GLOW")) {
            try {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.GLOWING, 80, 0, false, true));
            } catch (Exception ignored) {}
        }
    }

    private static void loadEffects(String username) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID playerId = resolvePlayerId(c, username);
            if (playerId == null) return;
            java.util.Set<String> effects = java.util.concurrent.ConcurrentHashMap.newKeySet();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT si.effect FROM player_purchase pp JOIN store_item si ON pp.item_id = si.id " +
                    "WHERE pp.player_id = ? AND pp.equipped = TRUE AND si.effect IS NOT NULL AND si.effect <> ''")) {
                ps.setObject(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) effects.add(rs.getString(1).toUpperCase());
                }
            }
            EFFECTS_CACHE.put(username, effects);
        } catch (Exception e) {
            // store_item/player_purchase may not exist until the backend has started — ignore.
            EFFECTS_CACHE.putIfAbsent(username, java.util.Set.of());
        }
    }

    // Spawn the cosmetic particle trail(s) the player owns, at their feet (purely visual).
    private static void spawnTrails(ServerPlayer player, java.util.Set<String> effects) {
        try {
            net.minecraft.server.level.ServerLevel level = player.serverLevel();
            for (String e : effects) {
                net.minecraft.core.particles.ParticleOptions p = particleFor(e);
                if (p != null) {
                    level.sendParticles(p, player.getX(), player.getY() + 0.15, player.getZ(),
                            2, 0.25, 0.05, 0.25, 0.01);
                }
            }
        } catch (Exception ignored) {
            // never let cosmetics break the tick
        }
    }

    private static net.minecraft.core.particles.ParticleOptions particleFor(String effect) {
        return switch (effect) {
            case "TRAIL_SPARKLE"   -> net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER;
            case "TRAIL_FLAME"     -> net.minecraft.core.particles.ParticleTypes.FLAME;
            case "TRAIL_SOUL"      -> net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME;
            case "TRAIL_HEART"     -> net.minecraft.core.particles.ParticleTypes.HEART;
            case "TRAIL_ENCHANT"   -> net.minecraft.core.particles.ParticleTypes.ENCHANT;
            case "TRAIL_STARLIGHT" -> net.minecraft.core.particles.ParticleTypes.END_ROD;
            case "TRAIL_PORTAL"    -> net.minecraft.core.particles.ParticleTypes.PORTAL;
            case "TRAIL_SNOW"      -> net.minecraft.core.particles.ParticleTypes.SNOWFLAKE;
            case "TRAIL_NOTE"      -> net.minecraft.core.particles.ParticleTypes.NOTE;
            case "TRAIL_CHERRY"    -> net.minecraft.core.particles.ParticleTypes.CHERRY_LEAVES;
            case "TRAIL_TOTEM"     -> net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING;
            case "TRAIL_SMOKE"     -> net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE;
            case "TRAIL_CRIT"      -> net.minecraft.core.particles.ParticleTypes.CRIT;
            default -> null;
        };
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

            bumpStatWithMilestone(c, killerId, killerName, "kills", 1, "reached", "PvP kills");
            // Total deaths come from the vanilla DEATHS stat flush; here we only count PvP deaths.
            incrementStat(c, victimId, "pvp_deaths", 1);

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
                ps.setString(2, killerName + " killed " + victimName + " with " + weapon.replace("minecraft:", ""));
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

    // Public in-game chat → recent-activity feed.
    private static void recordChat(String username, String text) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID playerId = resolvePlayerId(c, username);
            String msg = text.length() > 400 ? text.substring(0, 400) : text;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO event_log (id, type, description, player_id, occurred_at) VALUES (?, 'CHAT', ?, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, username + ": " + msg);
                ps.setObject(3, playerId);
                ps.setTimestamp(4, Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        } catch (Exception e) {
            LOGGER.error("[ArCraft] CHAT log failed for {}", username, e);
        }
    }

    // First-ever entry into the Nether or the End — logged once per player.
    private static void recordDimensionFirst(String username, String label) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID playerId = resolvePlayerId(c, username);
            if (playerId == null) return;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM event_log WHERE player_id = ? AND description LIKE ?")) {
                ps.setObject(1, playerId);
                ps.setString(2, "%entered " + label + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getLong(1) > 0) return; // already celebrated
                }
            }
            awardCoins(c, playerId, 20);
            logAchievement(c, playerId, username + " entered " + label + " for the first time! (+20 coins)");
            LOGGER.info("[ArCraft] DIMENSION_FIRST: username={}, dim={}", username, label);
        } catch (Exception e) {
            LOGGER.error("[ArCraft] DIMENSION_FIRST failed for {}", username, e);
        }
    }

    // A non-PvP player death: just log it to the feed (total deaths come from the vanilla stat).
    private static void recordPlayerDeath(String victimName, String deathMessage) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID victimId = resolvePlayerId(c, victimName);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO event_log (id, type, description, player_id, occurred_at) VALUES (?, 'PLAYER_DEATH', ?, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, deathMessage);
                ps.setObject(3, victimId);
                ps.setTimestamp(4, Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        } catch (Exception e) {
            LOGGER.error("[ArCraft] PLAYER_DEATH: victim={}, result=error", victimName, e);
        }
    }

    // PvP-only damage accumulation (totals come from vanilla DAMAGE_DEALT/TAKEN).
    private static void recordPvpDamage(String attackerName, String victimName, float amount) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID attackerId = resolvePlayerId(c, attackerName);
            UUID victimId = resolvePlayerId(c, victimName);
            if (attackerId != null) incrementStatF(c, attackerId, "pvp_damage_dealt", amount);
            if (victimId != null) incrementStatF(c, victimId, "pvp_damage_received", amount);
        } catch (Exception e) {
            LOGGER.error("[ArCraft] PVP_DAMAGE: attacker={}, victim={}, result=error", attackerName, victimName, e);
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
            bumpStatWithMilestone(c, playerId, killerName, "mobs_killed", 1, "killed", "mobs");
            upsertMobEntry(c, playerId, mobType);

            // Achievement coins: reward the player's FIRST kill of each notable boss.
            int reward = coinsForBoss(mobType);
            if (reward > 0 && mobKillCount(c, playerId, mobType) == 1) {
                awardCoins(c, playerId, reward);
                logAchievement(c, playerId,
                        killerName + " earned \"" + bossLabel(mobType) + "\" (+" + reward + " coins)");
                LOGGER.info("[ArCraft] ACHIEVEMENT: username={}, boss={}, coins=+{}", killerName, mobType, reward);
            }
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
            bumpStatWithMilestone(c, playerId, username, "blocks_mined", 1, "mined", "blocks");
            upsertBlockEntry(c, playerId, blockType, "mined");

            // Mining diamonds is a coin-worthy event: +10 coins each, achievement on the first.
            if (blockType.contains("diamond_ore")) {
                awardCoins(c, playerId, 10);
                if (blockTypeMined(c, playerId, blockType) == 1) {
                    logAchievement(c, playerId, username + " struck diamonds! (+10 coins)");
                }
                LOGGER.info("[ArCraft] DIAMOND: username={}, coins=+10", username);
            }
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
            bumpStatWithMilestone(c, playerId, username, "blocks_placed", 1, "placed", "blocks");
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
            bumpStatWithMilestone(c, playerId, username, "items_crafted", count, "crafted", "items");
            upsertItemEntry(c, playerId, itemType, count);

            // First time crafting a notable item → an achievement.
            if (isNotableItem(itemType) && itemTypeCount(c, playerId, itemType) <= count) {
                int reward = 25;
                awardCoins(c, playerId, reward);
                logAchievement(c, playerId, username + " crafted their first " + prettyName(itemType) + "! (+" + reward + " coins)");
            }
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

    // --- coins / achievements ---

    /** Coin reward for a first-time kill of a notable boss, or 0 for ordinary mobs. */
    private static int coinsForBoss(String mobType) {
        if (mobType == null) return 0;
        if (mobType.contains("ender_dragon"))   return 100;
        if (mobType.contains("wither"))          return 50;
        if (mobType.contains("warden"))          return 75;
        if (mobType.contains("elder_guardian"))  return 30;
        return 0;
    }

    private static String bossLabel(String mobType) {
        if (mobType.contains("ender_dragon"))   return "Dragon Slayer";
        if (mobType.contains("wither"))          return "Wither Slayer";
        if (mobType.contains("warden"))          return "Warden Slayer";
        if (mobType.contains("elder_guardian"))  return "Guardian Slayer";
        return "Boss Slayer";
    }

    private static long mobKillCount(Connection c, UUID playerId, String mobType) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT count FROM mob_stat_entry WHERE player_id = ? AND mob_type = ?")) {
            ps.setObject(1, playerId);
            ps.setString(2, mobType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        return 0;
    }

    private static void awardCoins(Connection c, UUID playerId, int amount) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE player SET coins = coins + ? WHERE id = ?")) {
            ps.setInt(1, amount);
            ps.setObject(2, playerId);
            ps.executeUpdate();
        }
    }

    private static void logAchievement(Connection c, UUID playerId, String description) throws Exception {
        // No image_url: the dashboard renders the player's skin face via SkinService.
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO event_log (id, type, description, player_id, occurred_at) " +
                "VALUES (?, 'ACHIEVEMENT', ?, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, description);
            ps.setObject(3, playerId);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    private static long readStat(Connection c, UUID playerId, String column) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + column + " FROM player_stats WHERE player_id = ?")) {
            ps.setObject(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        return 0;
    }

    // A curated milestone ladder so achievements fire at reachable counts, not only at 1000+.
    private static final long[] MILESTONES = {
            10, 50, 100, 250, 500, 1000, 2500, 5000, 10000, 25000,
            50000, 100000, 250000, 500000, 1000000
    };

    /** Largest milestone in the ladder crossed in (oldVal, newVal], or 0. */
    private static long milestoneCrossed(long oldVal, long newVal) {
        long crossed = 0;
        for (long m : MILESTONES) {
            if (m > oldVal && m <= newVal) crossed = m;
        }
        return crossed;
    }

    /**
     * Increments a PlayerStats column and, if the new total crosses a 10^n (n>2) milestone,
     * logs an achievement event (with the player's face) and awards scaled coins.
     */
    private static void bumpStatWithMilestone(Connection c, UUID playerId, String username,
                                              String column, long delta, String verb, String noun) throws Exception {
        incrementStat(c, playerId, column, delta);
        long newVal = readStat(c, playerId, column);
        long crossed = milestoneCrossed(newVal - delta, newVal);
        if (crossed > 0) {
            int reward = (int) Math.max(5, Math.min(1000, crossed / 50));
            awardCoins(c, playerId, reward);
            String desc = username + " " + verb + " " + String.format("%,d", crossed) + " " + noun
                    + " (+" + reward + " coins)";
            logAchievement(c, playerId, desc);
            LOGGER.info("[ArCraft] MILESTONE: username={}, {}={}, coins=+{}", username, column, crossed, reward);
        }
    }

    private static long blockTypeMined(Connection c, UUID playerId, String blockType) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT mined FROM block_stat_entry WHERE player_id = ? AND block_type = ?")) {
            ps.setObject(1, playerId);
            ps.setString(2, blockType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        return 0;
    }

    private static long itemTypeCount(Connection c, UUID playerId, String itemType) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT count FROM item_stat_entry WHERE player_id = ? AND item_type = ?")) {
            ps.setObject(1, playerId);
            ps.setString(2, itemType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        return 0;
    }

    /** Valuable / milestone-y items worth celebrating the first craft of. */
    private static boolean isNotableItem(String itemType) {
        if (itemType == null) return false;
        if (itemType.contains("netherite")) return true;
        if (itemType.contains("diamond_pickaxe") || itemType.contains("diamond_sword")
                || itemType.contains("diamond_axe") || itemType.contains("diamond_helmet")
                || itemType.contains("diamond_chestplate") || itemType.contains("diamond_leggings")
                || itemType.contains("diamond_boots")) return true;
        return switch (stripPrefix(itemType)) {
            case "enchanting_table", "anvil", "beacon", "conduit", "end_crystal", "shield",
                 "bow", "crossbow", "ender_chest", "shulker_box", "jukebox", "lodestone",
                 "respawn_anchor", "brewing_stand", "smithing_table", "grindstone", "loom",
                 "lectern", "bell", "spyglass", "cake", "clock", "compass", "fishing_rod",
                 "flint_and_steel", "golden_apple", "tnt", "bookshelf" -> true;
            default -> false;
        };
    }

    private static String stripPrefix(String id) {
        int i = id.indexOf(':');
        return i >= 0 ? id.substring(i + 1) : id;
    }

    private static String prettyName(String id) {
        String name = stripPrefix(id).replace('_', ' ').trim();
        StringBuilder sb = new StringBuilder();
        for (String w : name.split(" ")) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
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
                                         int r, int g, int b, int surfaceY) {
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
                    "map_color_r, map_color_g, map_color_b, surface_y, first_visited, last_visited) " +
                    "SELECT ?,?,?,?,?,?,?,?,?,?,?,?,? WHERE NOT EXISTS (" +
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
                ps.setInt(11, surfaceY);
                ps.setTimestamp(12, now);
                ps.setTimestamp(13, now);
                ps.setString(14, playerId.toString());
                ps.setInt(15, chunkX);
                ps.setInt(16, chunkZ);
                ps.setString(17, dimension);
                inserted = ps.executeUpdate();
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE chunk_visit SET last_visited=?, biome=?, top_block=?, " +
                    "map_color_r=?, map_color_g=?, map_color_b=?, surface_y=?, stay_ticks=stay_ticks+1 " +
                    "WHERE player_id=? AND chunk_x=? AND chunk_z=? AND dimension=?")) {
                ps.setTimestamp(1, now);
                ps.setString(2, biome);
                ps.setString(3, topBlock);
                ps.setInt(4, r);
                ps.setInt(5, g);
                ps.setInt(6, b);
                ps.setInt(7, surfaceY);
                ps.setString(8, playerId.toString());
                ps.setInt(9, chunkX);
                ps.setInt(10, chunkZ);
                ps.setString(11, dimension);
                ps.executeUpdate();
            }
            LOGGER.info("[ArCraft] CHUNK_VISIT: username={}, chunk=({},{}), dim={}, biome={}, top={}, result={}",
                    username, chunkX, chunkZ, dimension, biome, topBlock,
                    inserted > 0 ? "created" : "updated");
        } catch (Exception e) {
            LOGGER.error("[ArCraft] CHUNK_VISIT: username={}, chunk=({},{}), result=error", username, chunkX, chunkZ, e);
        }
    }

    /**
     * Increments a per-chunk heatmap counter (blocks_mined or blocks_placed), creating the
     * chunk_visit row if the player has acted in a chunk they haven't "visited" via the tick
     * tracker yet. Mirrors the upsert pattern used elsewhere for shared-DB safety.
     */
    private static void recordChunkActivity(String username, int chunkX, int chunkZ,
                                            String dimension, String column) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID playerId = resolvePlayerId(c, username);
            if (playerId == null) return;

            java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO chunk_visit (id, player_id, chunk_x, chunk_z, dimension, " +
                    "map_color_r, map_color_g, map_color_b, blocks_mined, blocks_placed, stay_ticks, " +
                    "first_visited, last_visited) " +
                    "SELECT ?,?,?,?,?,100,140,100,0,0,0,?,? WHERE NOT EXISTS (" +
                    "SELECT 1 FROM chunk_visit WHERE player_id=? AND chunk_x=? AND chunk_z=? AND dimension=?)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, playerId.toString());
                ps.setInt(3, chunkX);
                ps.setInt(4, chunkZ);
                ps.setString(5, dimension);
                ps.setTimestamp(6, now);
                ps.setTimestamp(7, now);
                ps.setString(8, playerId.toString());
                ps.setInt(9, chunkX);
                ps.setInt(10, chunkZ);
                ps.setString(11, dimension);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE chunk_visit SET " + column + " = " + column + " + 1, last_visited=? " +
                    "WHERE player_id=? AND chunk_x=? AND chunk_z=? AND dimension=?")) {
                ps.setTimestamp(1, now);
                ps.setString(2, playerId.toString());
                ps.setInt(3, chunkX);
                ps.setInt(4, chunkZ);
                ps.setString(5, dimension);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            LOGGER.error("[ArCraft] CHUNK_ACTIVITY: username={}, chunk=({},{}), col={}, result=error",
                    username, chunkX, chunkZ, column, e);
        }
    }

    private static int[] blockColor(String blockName) {
        if (blockName == null) return new int[]{100, 100, 100};
        // Order matters: more specific matches first.
        if (blockName.contains("grass_block"))   return new int[]{106, 142,  72};
        if (blockName.contains("podzol"))         return new int[]{ 90,  68,  38};
        if (blockName.contains("mycelium"))       return new int[]{111,  98, 110};
        if (blockName.contains("moss"))           return new int[]{ 89, 109,  45};
        if (blockName.contains("lava"))           return new int[]{217, 100,  30};
        if (blockName.contains("water"))          return new int[]{ 58, 110, 222};
        if (blockName.contains("red_sand"))       return new int[]{190, 102,  51};
        if (blockName.contains("sandstone"))      return new int[]{216, 203, 156};
        if (blockName.contains("sand"))           return new int[]{223, 214, 170};
        if (blockName.contains("clay"))           return new int[]{160, 166, 179};
        if (blockName.contains("gravel"))         return new int[]{136, 126, 126};
        if (blockName.contains("powder_snow"))    return new int[]{248, 250, 255};
        if (blockName.contains("snow"))           return new int[]{240, 244, 250};
        if (blockName.contains("packed_ice") || blockName.contains("blue_ice")) return new int[]{140, 190, 250};
        if (blockName.contains("ice"))            return new int[]{160, 205, 255};
        if (blockName.contains("_log") || blockName.contains("_wood")) return new int[]{102,  76,  51};
        if (blockName.contains("_leaves"))        return new int[]{ 70, 104,  46};
        if (blockName.contains("_planks"))        return new int[]{160, 130,  86};
        if (blockName.contains("dirt") || blockName.contains("coarse") || blockName.contains("rooted")) return new int[]{134, 96, 67};
        if (blockName.contains("farmland"))       return new int[]{ 92,  62,  38};
        if (blockName.contains("netherrack"))     return new int[]{110,  50,  50};
        if (blockName.contains("soul"))           return new int[]{ 78,  62,  50};
        if (blockName.contains("basalt"))         return new int[]{ 73,  73,  80};
        if (blockName.contains("blackstone"))     return new int[]{ 42,  38,  44};
        if (blockName.contains("warped"))         return new int[]{ 22, 119, 109};
        if (blockName.contains("crimson"))        return new int[]{148,  62,  90};
        if (blockName.contains("end_stone") || blockName.contains("purpur")) return new int[]{219, 222, 158};
        if (blockName.contains("deepslate"))      return new int[]{ 77,  77,  82};
        if (blockName.contains("diamond_ore"))    return new int[]{ 95, 199, 197};
        if (blockName.contains("gold_ore"))       return new int[]{222, 190,  90};
        if (blockName.contains("iron_ore"))       return new int[]{197, 170, 142};
        if (blockName.contains("coal_ore"))       return new int[]{ 64,  64,  64};
        if (blockName.contains("ore"))            return new int[]{120, 124, 130};
        if (blockName.contains("cobblestone"))    return new int[]{120, 120, 120};
        if (blockName.contains("stone") || blockName.contains("andesite")
                || blockName.contains("granite") || blockName.contains("diorite")
                || blockName.contains("tuff")) return new int[]{128, 128, 128};
        if (blockName.contains("bedrock"))        return new int[]{ 60,  60,  60};
        return new int[]{120, 120, 120};
    }
}
