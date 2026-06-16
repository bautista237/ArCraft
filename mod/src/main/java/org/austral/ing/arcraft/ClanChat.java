package org.austral.ing.arcraft;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Bridges clan chat between the game and the web through the shared {@code clan_message} table.
 * <ul>
 *   <li>{@code /clan <message>} or {@code /c <message>} — send a message to your clan (spaces allowed).</li>
 *   <li>{@code /clan coords} — share your current coordinates with the clan.</li>
 * </ul>
 * A 1-second poll pushes every new clan message (whether sent in-game or on the website) to all
 * online members of that clan, so both sides stay in sync.
 */
@EventBusSubscriber(modid = ArcraftMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ClanChat {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static int tickCounter = 0;
    private static volatile Timestamp lastSeen = null;

    private ClanChat() {}

    // ── Commands ─────────────────────────────────────────────

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("clan")
                        .then(Commands.literal("coords").executes(ClanChat::shareCoords))
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ClanChat::sendMessage))
        );
        event.getDispatcher().register(
                Commands.literal("c")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ClanChat::sendMessage))
        );
    }

    private static int sendMessage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        final String username = player.getName().getString();
        final String message = StringArgumentType.getString(ctx, "message").trim();
        if (!message.isEmpty()) {
            DatabaseManager.submit(() -> insertClanMessage(username, message));
        }
        return 1;
    }

    private static int shareCoords(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        final String username = player.getName().getString();
        BlockPos pos = player.blockPosition();
        String dim = player.level().dimension().location().getPath();
        final String content = "📍 shared coords: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " (" + dim + ")";
        DatabaseManager.submit(() -> insertClanMessage(username, content));
        return 1;
    }

    private static void insertClanMessage(String username, String content) {
        Connection c = DatabaseManager.getConnection();
        try {
            UUID[] ids = lookupPlayerAndClan(c, username);
            if (ids == null) return;
            UUID playerId = ids[0], clanId = ids[1];
            if (clanId == null) {
                sendToPlayer(username, Component.literal("You are not in a clan.").withStyle(ChatFormatting.RED));
                return;
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO clan_message (id, clan_id, sender_id, content, sent_at) VALUES (?, ?, ?, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, clanId);
                ps.setObject(3, playerId);
                ps.setString(4, content);
                ps.setTimestamp(5, Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
            // The poll (within ~1s) broadcasts it to all online members, including the sender.
        } catch (Exception e) {
            LOGGER.error("[ArCraft] clan message insert failed for {}", username, e);
        }
    }

    // ── Poll & broadcast ─────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < 20) return; // ~1 second
        tickCounter = 0;
        DatabaseManager.submit(ClanChat::pollMessages);
    }

    private static void pollMessages() {
        Connection c = DatabaseManager.getConnection();
        try {
            if (lastSeen == null) {
                // First poll: start from "now" so we don't replay the whole history on startup.
                lastSeen = Timestamp.from(Instant.now());
                return;
            }
            record Msg(UUID clanId, String tag, String sender, String content) {}
            List<Msg> batch = new ArrayList<>();
            Set<UUID> clans = new HashSet<>();
            Timestamp newest = lastSeen;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT cm.clan_id, cl.tag, p.username, cm.content, cm.sent_at " +
                    "FROM clan_message cm JOIN player p ON cm.sender_id = p.id JOIN clan cl ON cm.clan_id = cl.id " +
                    "WHERE cm.sent_at > ? ORDER BY cm.sent_at ASC")) {
                ps.setTimestamp(1, lastSeen);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID clanId = (UUID) rs.getObject(1);
                        batch.add(new Msg(clanId, rs.getString(2), rs.getString(3), rs.getString(4)));
                        clans.add(clanId);
                        Timestamp t = rs.getTimestamp(5);
                        if (t.after(newest)) newest = t;
                    }
                }
            }
            lastSeen = newest;
            if (batch.isEmpty()) return;

            // Member usernames per clan in this batch.
            Map<UUID, Set<String>> membersByClan = new HashMap<>();
            for (UUID clanId : clans) {
                Set<String> members = new HashSet<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT username FROM player WHERE clan_id = ?")) {
                    ps.setObject(1, clanId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) members.add(rs.getString(1));
                    }
                }
                membersByClan.put(clanId, members);
            }

            MinecraftServer server = ArcraftMod.SERVER;
            if (server == null) return;
            server.execute(() -> {
                for (Msg m : batch) {
                    Set<String> members = membersByClan.getOrDefault(m.clanId(), Set.of());
                    if (members.isEmpty()) continue;
                    Component line = Component.literal("[" + m.tag() + "] ").withStyle(ChatFormatting.GREEN)
                            .append(Component.literal(m.sender() + ": ").withStyle(ChatFormatting.AQUA))
                            .append(Component.literal(m.content()).withStyle(ChatFormatting.WHITE));
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        if (members.contains(p.getName().getString())) p.sendSystemMessage(line);
                    }
                }
            });
        } catch (Exception e) {
            LOGGER.error("[ArCraft] clan message poll failed", e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    /** Returns {playerId, clanId(or null)} for a username, or null if the player row is missing. */
    private static UUID[] lookupPlayerAndClan(Connection c, String username) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT id, clan_id FROM player WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new UUID[]{(UUID) rs.getObject(1), (UUID) rs.getObject(2)};
                }
            }
        }
        return null;
    }

    private static void sendToPlayer(String username, Component message) {
        MinecraftServer server = ArcraftMod.SERVER;
        if (server == null) return;
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayerByName(username);
            if (p != null) p.sendSystemMessage(message);
        });
    }
}
