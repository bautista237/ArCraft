package org.austral.ing.arcraft.web.routes;

import io.javalin.Javalin;
import org.austral.ing.arcraft.web.Flash;
import org.austral.ing.arcraft.web.Renderer;
import org.austral.ing.arcraft.web.dao.Daos;
import org.austral.ing.arcraft.web.model.PlayerView;
import org.austral.ing.arcraft.web.service.GeminiService;
import org.austral.ing.arcraft.web.service.MapService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Assistant (Gemini chat) + world map pages (ex Assistant/Map controllers). */
public final class MiscRoutes {

    private static final String DEFAULT_DIM = "minecraft:overworld";

    private MiscRoutes() {
    }

    public static void register(Javalin app) {
        // ── AI assistant ─────────────────────────────────────────────────────
        app.get("/assistant", ctx -> Renderer.render(ctx, "assistant",
                Map.of("aiEnabled", GeminiService.INSTANCE.isConfigured())));

        app.post("/assistant/ask", ctx -> {
            String question = ctx.formParam("question");
            ctx.json(Map.of("answer", GeminiService.INSTANCE.ask(question)));
        });

        // ── World map ────────────────────────────────────────────────────────
        MapService maps = MapService.INSTANCE;

        app.get("/map", ctx -> {
            String dimension = ctx.queryParamAsClass("dimension", String.class).getOrDefault(DEFAULT_DIM);
            Map<String, Object> m = new HashMap<>();
            m.put("currentDimension", dimension);
            m.put("isPersonal", false);
            m.put("player", null);
            Renderer.render(ctx, "map", m);
        });

        app.get("/map/player/{username}", ctx -> {
            String username = ctx.pathParam("username");
            String dimension = ctx.queryParamAsClass("dimension", String.class).getOrDefault(DEFAULT_DIM);
            Optional<PlayerView> playerOpt = Daos.playerByUsername(username);
            if (playerOpt.isEmpty()) {
                Flash.error(ctx, "Player '" + username + "' not found.");
                ctx.redirect("/map");
                return;
            }
            PlayerView player = playerOpt.get();
            Map<String, Object> m = new HashMap<>();
            m.put("player", player);
            m.put("currentDimension", dimension);
            m.put("isPersonal", true);
            m.put("chunksExplored", maps.countChunks(player.getId(), dimension));
            Renderer.render(ctx, "map", m);
        });

        app.get("/map/data", ctx -> {
            String username = ctx.queryParam("username");
            String dimension = ctx.queryParamAsClass("dimension", String.class).getOrDefault(DEFAULT_DIM);
            if (username != null && !username.isBlank()) {
                Optional<PlayerView> playerOpt = Daos.playerByUsername(username);
                ctx.json(playerOpt.isEmpty() ? List.of()
                        : maps.getChunksAsDTO(playerOpt.get().getId(), dimension));
            } else {
                ctx.json(maps.getGlobalChunks(dimension));
            }
        });
    }
}
