package org.austral.ing.arcraft;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.ThreadLocalRandom;

/**
 * In-game email registration with verification codes. The mod records the email + code in the
 * shared DB; the backend's EmailService actually sends the verification mail (it owns the SMTP
 * sender). Players are prompted to register on join if they have no verified email.
 *
 * <ul>
 *   <li>{@code /email <address>} — register an email; a 6-digit code is emailed to it</li>
 *   <li>{@code /email verify <code>} — confirm the code to verify your email</li>
 * </ul>
 */
@EventBusSubscriber(modid = ArcraftMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class EmailCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    private EmailCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("email")
                        .then(Commands.literal("verify")
                                .then(Commands.argument("code", StringArgumentType.word())
                                        .executes(EmailCommands::verify)))
                        .then(Commands.argument("address", StringArgumentType.greedyString())
                                .executes(EmailCommands::register))
        );
    }

    private static int register(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        final String username = player.getName().getString();
        final String address = StringArgumentType.getString(ctx, "address").trim();
        final CommandSourceStack src = ctx.getSource();
        if (!address.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            src.sendFailure(Component.literal("That doesn't look like a valid email address."));
            return 0;
        }
        final String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        DatabaseManager.submit(() -> {
            Connection c = DatabaseManager.getConnection();
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE player SET email=?, email_verified=FALSE, verification_code=?, verification_sent=FALSE WHERE username=?")) {
                ps.setString(1, address);
                ps.setString(2, code);
                ps.setString(3, username);
                ps.executeUpdate();
            } catch (Exception e) {
                LOGGER.error("[ArCraft] /email register failed for {}", username, e);
            }
        });
        src.sendSuccess(() -> Component.literal("A verification code is being sent to " + address
                + ". Run /email verify <code> once you receive it.")
                .withStyle(ChatFormatting.GREEN), false);
        src.sendSuccess(() -> Component.literal("(If email isn't configured on this server, ask an admin.)")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int verify(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        final String username = player.getName().getString();
        final String code = StringArgumentType.getString(ctx, "code").trim();
        final CommandSourceStack src = ctx.getSource();
        DatabaseManager.submit(() -> {
            Connection c = DatabaseManager.getConnection();
            try {
                String stored = null;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT verification_code FROM player WHERE username=?")) {
                    ps.setString(1, username);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) stored = rs.getString(1);
                    }
                }
                final boolean ok = stored != null && stored.equals(code);
                if (ok) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE player SET email_verified=TRUE, verification_code=NULL WHERE username=?")) {
                        ps.setString(1, username);
                        ps.executeUpdate();
                    }
                }
                MinecraftServer server = ArcraftMod.SERVER;
                if (server != null) server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayerByName(username);
                    if (p != null) {
                        p.sendSystemMessage(ok
                                ? Component.literal("✔ Email verified! You'll now get event reminders.").withStyle(ChatFormatting.GREEN)
                                : Component.literal("✘ Wrong or expired code. Run /email <address> to get a new one.").withStyle(ChatFormatting.RED));
                    }
                });
            } catch (Exception e) {
                LOGGER.error("[ArCraft] /email verify failed for {}", username, e);
            }
        });
        return 1;
    }

    /** On join, nudge players who don't have a verified email yet. */
    public static void promptEmailIfNeeded(String username) {
        Connection c = DatabaseManager.getConnection();
        try {
            boolean verified = false;
            try (PreparedStatement ps = c.prepareStatement("SELECT email_verified FROM player WHERE username=?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) verified = rs.getBoolean(1);
                }
            }
            if (verified) return;
            MinecraftServer server = ArcraftMod.SERVER;
            if (server == null) return;
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayerByName(username);
                if (p != null) {
                    p.sendSystemMessage(Component.literal("📧 You haven't registered an email. Use ")
                            .withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal("/email <address>").withStyle(ChatFormatting.AQUA))
                            .append(Component.literal(" to get event reminders.").withStyle(ChatFormatting.YELLOW)));
                }
            });
        } catch (Exception e) {
            LOGGER.error("[ArCraft] promptEmailIfNeeded failed for {}", username, e);
        }
    }
}
