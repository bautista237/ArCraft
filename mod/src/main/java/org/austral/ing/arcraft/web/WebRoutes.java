package org.austral.ing.arcraft.web;

import io.javalin.Javalin;

/**
 * Central route table for the dashboard. Filled in feature by feature as the old Spring
 * controllers are ported (dashboard, rankings, players, PvP, clans, store, assistant, admin,
 * map, auth).
 */
final class WebRoutes {

    private WebRoutes() {
    }

    static void register(Javalin app) {
        app.get("/health", ctx -> ctx.result("ok"));
        Auth.register(app);
        org.austral.ing.arcraft.web.routes.PageRoutes.register(app);
        org.austral.ing.arcraft.web.routes.ClanRoutes.register(app);
        org.austral.ing.arcraft.web.routes.PvPRoutes.register(app);
    }
}
