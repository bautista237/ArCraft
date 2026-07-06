package org.austral.ing.arcraft;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.austral.ing.arcraft.web.WebServer;
import org.slf4j.Logger;

@Mod(ArcraftMod.MODID)
public class ArcraftMod {
    public static final String MODID = "arcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Live server handle — used to push clan-chat messages to online players. */
    public static volatile MinecraftServer SERVER;

    public ArcraftMod(IEventBus modEventBus, ModContainer modContainer) {
        // Materialises config/arcraft-common.toml (created on first launch) and backs the
        // in-game Config screen registered by ArcraftClient.
        modContainer.registerConfig(ModConfig.Type.COMMON, ArcraftConfig.SPEC);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("========================================");
        LOGGER.info("[ArCraft] Mod starting up");
        LOGGER.info("[ArCraft] Mod ID    : {}", MODID);
        LOGGER.info("[ArCraft] MC version: 1.21.1 (NeoForge)");
        LOGGER.info("[ArCraft] Tracking  : joins, PvP, blocks, mobs, items, arrows, chunks");
        LOGGER.info("[ArCraft] Initializing H2 database connection...");
        SERVER = event.getServer();
        LegacyImport.run(); // one-time upgrade from the old two-process layout (secrets → TOML)
        DatabaseManager.init();
        final boolean onlineMode = event.getServer().usesAuthentication();
        DatabaseManager.submit(() -> DatabaseManager.recordOnlineMode(onlineMode));
        LOGGER.info("[ArCraft] Server online-mode: {}", onlineMode);
        LOGGER.info("[ArCraft] Starting embedded web dashboard...");
        WebServer.start();
        LOGGER.info("[ArCraft] Ready — all event handlers active");
        LOGGER.info("========================================");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[ArCraft] Server stopping — stopping web dashboard, flushing DB writes and closing connection");
        WebServer.stop();
        DatabaseManager.close();
        SERVER = null;
        LOGGER.info("[ArCraft] Shutdown complete");
    }
}
