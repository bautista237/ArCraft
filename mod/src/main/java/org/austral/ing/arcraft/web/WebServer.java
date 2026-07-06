package org.austral.ing.arcraft.web;

import com.mojang.logging.LogUtils;
import io.javalin.Javalin;
import org.austral.ing.arcraft.ArcraftConfig;
import org.slf4j.Logger;

/**
 * The ArCraft dashboard, served from INSIDE the Minecraft server process — the same model
 * Dynmap and BlueMap use (embedded Jetty; no child JVM, no second process, no Spring).
 *
 * <p>Classloader note: Jetty discovers several services via the thread context classloader,
 * which under NeoForge points at a game-layer loader that can't see the Jar-in-Jar'd web
 * libraries. The documented fix (Javalin's "Javalin and Minecraft servers" guide) is to swap
 * the TCCL to this class's own loader while the server starts, then restore it.</p>
 */
public final class WebServer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static Javalin app;

    private WebServer() {
    }

    public static synchronized void start() {
        if (app != null) return;
        if (!ArcraftConfig.WEB_ENABLED.get()) {
            LOGGER.info("[ArCraft] Web dashboard disabled in config/arcraft-common.toml");
            return;
        }
        String host = ArcraftConfig.WEB_HOST.get();
        int port = ArcraftConfig.WEB_PORT.get();

        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(WebServer.class.getClassLoader());
        try {
            app = Javalin.create(cfg -> {
                cfg.showJavalinBanner = false;
                cfg.useVirtualThreads = true;
                cfg.staticFiles.add("/web/static");
                // SameSite=Lax session cookie = the browser won't attach it to cross-site
                // POSTs, which (with the Origin check in Auth) covers CSRF.
                cfg.jetty.modifyServletContextHandler(handler -> {
                    var sessions = handler.getSessionHandler();
                    if (sessions != null) {
                        sessions.setHttpOnly(true);
                        sessions.setSameSite(org.eclipse.jetty.http.HttpCookie.SameSite.LAX);
                    }
                });
            });
            WebRoutes.register(app);
            app.start(host, port);
            LOGGER.info("[ArCraft] Web dashboard up on http://{}:{} (public: {})",
                    host.equals("0.0.0.0") ? "localhost" : host, port, ArcraftConfig.baseUrl());
        } catch (Exception e) {
            // The dashboard failing must never take the Minecraft server down with it.
            LOGGER.error("[ArCraft] Web dashboard failed to start — the game keeps running without it", e);
            stopQuietly();
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    public static synchronized void stop() {
        if (app == null) return;
        LOGGER.info("[ArCraft] Stopping web dashboard");
        stopQuietly();
    }

    private static void stopQuietly() {
        try {
            if (app != null) app.stop();
        } catch (Exception e) {
            LOGGER.warn("[ArCraft] Error stopping web server", e);
        }
        app = null;
    }
}
