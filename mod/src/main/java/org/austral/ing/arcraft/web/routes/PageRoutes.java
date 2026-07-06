package org.austral.ing.arcraft.web.routes;

import io.javalin.Javalin;
import org.austral.ing.arcraft.web.Flash;
import org.austral.ing.arcraft.web.Renderer;
import org.austral.ing.arcraft.web.dao.Daos;
import org.austral.ing.arcraft.web.model.PlayerView;
import org.austral.ing.arcraft.web.model.StatsView;
import org.austral.ing.arcraft.web.service.DashboardService;
import org.austral.ing.arcraft.web.service.EventsService;
import org.austral.ing.arcraft.web.service.PlayerProfileService;
import org.austral.ing.arcraft.web.service.RankingsService;
import org.austral.ing.arcraft.web.service.TitleService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Dashboard, rankings, player profiles and the info page (ex Dashboard/Rankings/Player/Info controllers). */
public final class PageRoutes {

    private PageRoutes() {
    }

    public static void register(Javalin app) {
        DashboardService dash = DashboardService.INSTANCE;

        app.get("/", ctx -> ctx.redirect("/dashboard"));

        app.get("/dashboard", ctx -> {
            Map<String, Object> m = new HashMap<>();
            m.put("totalPlayers", dash.getTotalPlayers());
            m.put("totalKills", dash.getTotalKills());
            m.put("totalMobsKilled", dash.getTotalMobsKilled());
            m.put("topKillers", dash.getTop5Killers());
            m.put("recentEvents", dash.getRecentEvents());
            m.put("banners", EventsService.INSTANCE.getActiveBanners());
            m.put("topItemsCrafted", dash.getTopItemsCraftedGlobal(8));
            m.put("topBlocksMined", dash.getTopBlocksMinedGlobal(8));
            m.put("topBlocksPlaced", dash.getTopBlocksPlacedGlobal(8));
            m.put("topMobsKilled", dash.getTopMobsKilledGlobal(8));
            Renderer.render(ctx, "dashboard", m);
        });

        app.get("/rankings", ctx -> {
            String sort = ctx.queryParamAsClass("sort", String.class).getOrDefault("kills");
            Renderer.render(ctx, "rankings", Map.of(
                    "players", RankingsService.INSTANCE.getSorted(sort),
                    "currentSort", sort,
                    "titles", TitleService.INSTANCE.getAllTitles()));
        });

        app.get("/info", ctx -> Renderer.render(ctx, "info", Map.of()));

        app.get("/players/{username}", ctx -> {
            String username = ctx.pathParam("username");
            Optional<PlayerView> playerOpt = Daos.playerByUsername(username);
            if (playerOpt.isEmpty()) {
                Flash.error(ctx, "Player '" + username + "' no longer exists. They may have been removed by an admin.");
                ctx.redirect("/rankings");
                return;
            }
            PlayerView player = playerOpt.get();
            StatsView stats = Daos.statsByPlayerId(player.getId()).orElse(null);
            PlayerProfileService profile = PlayerProfileService.INSTANCE;

            Map<String, Object> m = new HashMap<>();
            m.put("player", player);
            m.put("stats", stats);
            m.put("titles", TitleService.INSTANCE.getTitles(player.getUsername()));
            m.put("kdRatio", stats != null ? RankingsService.computeKdRatio(stats) : 0);
            m.put("bowAccuracy", stats != null ? RankingsService.computeBowAccuracy(stats) : 0);
            m.put("totalDistance", stats != null ? RankingsService.computeTotalDistance(stats) : 0);
            m.put("topBlocksMined", profile.getTopBlocksMined(player.getId(), 5));
            m.put("topBlocksPlaced", profile.getTopBlocksPlaced(player.getId(), 5));
            m.put("topItemsCrafted", profile.getTopItemsCrafted(player.getId(), 5));
            m.put("topMobsKilled", profile.getTopMobsKilled(player.getId(), 5));
            m.put("pvpHistory", profile.getRecentPvP(player.getId()));
            m.put("pvp", profile.getPvpAnalytics(player.getId()));
            Renderer.render(ctx, "player", m);
        });
    }
}
