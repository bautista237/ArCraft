package org.austral.ing.arcraft;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@Mod(ArcraftMod.MODID)
public class ArcraftMod {
    public static final String MODID = "arcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArcraftMod(IEventBus modEventBus, ModContainer modContainer) {
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
        DatabaseManager.init();
        LOGGER.info("[ArCraft] Ready — all event handlers active");
        LOGGER.info("========================================");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[ArCraft] Server stopping — flushing DB writes and closing connection");
        DatabaseManager.close();
        LOGGER.info("[ArCraft] Shutdown complete");
    }
}
