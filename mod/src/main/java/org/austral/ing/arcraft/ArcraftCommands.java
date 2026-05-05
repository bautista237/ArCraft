package org.austral.ing.arcraft;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@EventBusSubscriber(modid = ArcraftMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ArcraftCommands {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ArcraftCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("login")
                        .then(Commands.argument("password", StringArgumentType.word())
                                .executes(ArcraftCommands::login))
        );
        event.getDispatcher().register(
                Commands.literal("retrieve")
                        .executes(ArcraftCommands::retrieve)
        );
    }

    private static int login(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        final String username = player.getName().getString();
        final String plain = StringArgumentType.getString(ctx, "password");
        final String hash = BCrypt.hashpw(plain, BCrypt.gensalt());

        DatabaseManager.submit(() -> {
            Connection c = DatabaseManager.getConnection();
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE player SET password_hash = ?, player_password_plain = ? WHERE username = ?")) {
                ps.setString(1, hash);
                ps.setString(2, plain);
                ps.setString(3, username);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    LOGGER.warn("[ArCraft] /login: no player row for {}", username);
                }
            } catch (Exception e) {
                LOGGER.error("[ArCraft] /login failed for {}", username, e);
            }
        });

        ctx.getSource().sendSuccess(() -> Component.literal("✔ Password set. Use /retrieve to view it."), false);
        return 1;
    }

    private static int retrieve(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        final CommandSourceStack source = ctx.getSource();
        final String username = player.getName().getString();

        DatabaseManager.submit(() -> {
            Connection c = DatabaseManager.getConnection();
            String stored = null;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT player_password_plain FROM player WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) stored = rs.getString(1);
                }
            } catch (Exception e) {
                LOGGER.error("[ArCraft] /retrieve failed for {}", username, e);
            }

            final String message = (stored == null || stored.isEmpty())
                    ? "No password set. Use /login <password> first."
                    : stored;
            player.server.execute(() -> source.sendSuccess(() -> Component.literal(message), false));
        });

        return 1;
    }
}
