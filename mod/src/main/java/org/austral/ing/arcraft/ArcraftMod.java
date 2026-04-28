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

    private Thread springThread;

    public ArcraftMod(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[ArCraft] Server starting — initializing database and Spring context");
        DatabaseManager.init();

        springThread = new Thread(() -> {
            try {
                ArcraftSpringApp.start();
            } catch (Throwable t) {
                LOGGER.error("[ArCraft] Spring Boot failed to start", t);
            }
        }, "ArCraft-Spring");
        springThread.setDaemon(true);
        springThread.start();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[ArCraft] Server stopping — shutting down Spring and database");
        ArcraftSpringApp.stop();
        DatabaseManager.close();
    }
}
