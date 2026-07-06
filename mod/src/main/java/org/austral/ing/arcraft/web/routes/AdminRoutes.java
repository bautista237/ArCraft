package org.austral.ing.arcraft.web.routes;

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.austral.ing.arcraft.web.Flash;
import org.austral.ing.arcraft.web.Renderer;
import org.austral.ing.arcraft.web.model.ClanView;
import org.austral.ing.arcraft.web.model.EventLogView;
import org.austral.ing.arcraft.web.model.PlayerView;
import org.austral.ing.arcraft.web.model.StatsView;
import org.austral.ing.arcraft.web.model.StoreItemView;
import org.austral.ing.arcraft.web.dao.Daos;
import org.austral.ing.arcraft.web.service.AdminService;
import org.austral.ing.arcraft.web.service.ClanService;
import org.austral.ing.arcraft.web.service.EventsService;
import org.austral.ing.arcraft.web.service.StoreService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The /admin panel (players, stats, coins, clans, live-feed events, banners, store items).
 * Access is enforced centrally by Auth's gate ("/admin" requires the admin flag).
 */
public final class AdminRoutes {

    private AdminRoutes() {
    }

    public static void register(Javalin app) {
        AdminService admin = AdminService.INSTANCE;
        StoreService store = StoreService.INSTANCE;
        EventsService events = EventsService.INSTANCE;

        app.get("/admin", ctx -> Renderer.render(ctx, "admin/index", Map.of()));

        // ── Players ──────────────────────────────────────────────────────────

        app.get("/admin/players", ctx -> Renderer.render(ctx, "admin/players",
                Map.of("players", admin.getAllPlayers())));

        app.post("/admin/players", ctx -> {
            String username = ctx.formParam("username");
            boolean created = admin.createPlayer(username, ctx.formParam("password"),
                    "true".equals(ctx.formParam("isAdmin")), ctx.formParam("email"));
            if (!created) Flash.error(ctx, "A player with username '" + username + "' already exists.");
            else Flash.success(ctx, "Player '" + username + "' created successfully.");
            ctx.redirect("/admin/players");
        });

        app.get("/admin/players/{id}/edit", ctx -> {
            PlayerView player = admin.findPlayer(uuid(ctx.pathParam("id"))).orElse(null);
            if (player == null) {
                Flash.error(ctx, "No se puede acceder a las estadísticas de un jugador inexistente.");
                ctx.redirect("/admin/players");
                return;
            }
            StatsView stats = Daos.statsByPlayerId(player.getId()).orElseGet(() -> new StatsView(player.getId()));
            Renderer.render(ctx, "admin/player-edit", Map.of("player", player, "stats", stats));
        });

        app.post("/admin/players/{id}/edit", ctx -> {
            admin.updatePlayerStats(uuid(ctx.pathParam("id")),
                    longParam(ctx, "kills"), longParam(ctx, "deaths"),
                    floatParam(ctx, "damageDealt"), floatParam(ctx, "damageReceived"),
                    longParam(ctx, "mobsKilled"), longParam(ctx, "blocksPlaced"), longParam(ctx, "blocksMined"),
                    longParam(ctx, "itemsCrafted"), longParam(ctx, "distanceWalked"), longParam(ctx, "distanceSwum"),
                    longParam(ctx, "distanceFlown"), longParam(ctx, "distanceSailed"),
                    longParam(ctx, "shotsFired"), longParam(ctx, "shotsHit"), longParam(ctx, "longestShotBlocks"));
            ctx.redirect("/admin/players");
        });

        app.post("/admin/players/{id}/delete", ctx -> {
            UUID id = uuid(ctx.pathParam("id"));
            String error = admin.deletePlayer(id);
            if (error != null) {
                Flash.error(ctx, error);
                ctx.redirect("/admin/players/" + id + "/edit");
            } else {
                Flash.success(ctx, "Player deleted successfully.");
                ctx.redirect("/admin/players");
            }
        });

        app.post("/admin/players/{id}/email", ctx -> {
            UUID id = uuid(ctx.pathParam("id"));
            admin.updatePlayerEmail(id, ctx.formParam("email"));
            Flash.success(ctx, "Email updated.");
            ctx.redirect("/admin/players/" + id + "/edit");
        });

        app.post("/admin/players/{id}/coins", ctx -> {
            UUID id = uuid(ctx.pathParam("id"));
            long amount = longParam(ctx, "amount");
            String error = store.grantCoins(id, amount);
            if (error != null) Flash.error(ctx, error);
            else Flash.success(ctx, (amount >= 0 ? "Granted " : "Removed ") + Math.abs(amount) + " coins.");
            ctx.redirect("/admin/players/" + id + "/edit");
        });

        // ── Live-feed events ─────────────────────────────────────────────────

        app.get("/admin/events", ctx -> Renderer.render(ctx, "admin/events", Map.of(
                "events", Daos.recentEvents(20),
                "players", admin.getAllPlayers(),
                "eventTypes", EventLogView.EventType.values())));

        app.post("/admin/events", ctx -> {
            admin.createEvent(ctx.formParam("type"), ctx.formParam("description"),
                    uuid(ctx.formParam("playerId")), ctx.formParam("imageUrl"));
            ctx.redirect("/admin/events");
        });

        // ── Clans ────────────────────────────────────────────────────────────

        app.get("/admin/clans", ctx -> {
            List<ClanView> clans = ClanService.INSTANCE.getAllClans();
            Map<UUID, Long> memberCounts = new HashMap<>();
            for (ClanView clan : clans) {
                memberCounts.put(clan.getId(), (long) ClanService.INSTANCE.getMembers(clan.getId()).size());
            }
            Renderer.render(ctx, "admin/clans", Map.of(
                    "clans", clans,
                    "players", admin.getAllPlayers(),
                    "memberCounts", memberCounts));
        });

        app.post("/admin/clans", ctx -> {
            String name = ctx.formParam("name");
            String error = admin.createClan(name, ctx.formParam("tag"),
                    uuid(ctx.formParam("leaderId")), "true".equals(ctx.formParam("friendlyFireEnabled")));
            if (error != null) Flash.error(ctx, error);
            else Flash.success(ctx, "Clan '" + name + "' created successfully.");
            ctx.redirect("/admin/clans");
        });

        app.post("/admin/clans/{id}/delete", ctx -> {
            String error = admin.deleteClan(uuid(ctx.pathParam("id")));
            if (error != null) Flash.error(ctx, error);
            else Flash.success(ctx, "Clan deleted. All members have been removed from it.");
            ctx.redirect("/admin/clans");
        });

        app.get("/admin/clans/{id}/edit", ctx -> {
            UUID id = uuid(ctx.pathParam("id"));
            ClanView clan = admin.findClan(id).orElse(null);
            if (clan == null) {
                Flash.error(ctx, "Clan not found.");
                ctx.redirect("/admin/clans");
                return;
            }
            Renderer.render(ctx, "admin/clan-edit", Map.of(
                    "clan", clan,
                    "members", ClanService.INSTANCE.getMembers(id),
                    "availablePlayers", admin.getPlayersNotInClan(id)));
        });

        app.post("/admin/clans/{id}/edit", ctx -> {
            UUID id = uuid(ctx.pathParam("id"));
            admin.updateClan(id, ctx.formParam("name"), ctx.formParam("tag"),
                    uuid(ctx.formParam("leaderId")), "true".equals(ctx.formParam("friendlyFireEnabled")));
            ctx.redirect("/admin/clans/" + id + "/edit");
        });

        app.post("/admin/clans/{id}/add-member", ctx -> {
            UUID id = uuid(ctx.pathParam("id"));
            admin.addMemberToClan(id, uuid(ctx.formParam("playerId")));
            ctx.redirect("/admin/clans/" + id + "/edit");
        });

        app.post("/admin/clans/{id}/remove-member", ctx -> {
            UUID id = uuid(ctx.pathParam("id"));
            admin.removeMemberFromClan(uuid(ctx.formParam("playerId")));
            ctx.redirect("/admin/clans/" + id + "/edit");
        });

        // ── Event banners ────────────────────────────────────────────────────

        app.get("/admin/banners", ctx -> Renderer.render(ctx, "admin/banners",
                Map.of("events", events.getAllEvents())));

        app.post("/admin/banners", ctx -> {
            String title = ctx.formParam("title");
            ZoneId zone = ZoneId.systemDefault();
            String error;
            try {
                LocalDateTime start = LocalDateTime.parse(ctx.formParam("startDate"));
                LocalDateTime end = LocalDateTime.parse(ctx.formParam("endDate"));
                String remindRaw = ctx.formParam("remindDaysBefore");
                Integer remindDays = remindRaw == null || remindRaw.isBlank() ? null : Integer.parseInt(remindRaw);
                error = events.createEvent(title, ctx.formParam("description"),
                        start.atZone(zone).toInstant(), end.atZone(zone).toInstant(),
                        remindDays, "true".equals(ctx.formParam("remindDuring")));
            } catch (Exception e) {
                error = "Start and end dates are required.";
            }
            if (error != null) Flash.error(ctx, error);
            else Flash.success(ctx, "Event '" + title + "' created.");
            ctx.redirect("/admin/banners");
        });

        app.post("/admin/banners/{id}/delete", ctx -> {
            events.deleteEvent(uuid(ctx.pathParam("id")));
            Flash.success(ctx, "Event deleted.");
            ctx.redirect("/admin/banners");
        });

        // ── Store items ──────────────────────────────────────────────────────

        app.get("/admin/store", ctx -> Renderer.render(ctx, "admin/store", Map.of(
                "items", store.getAllItems(),
                "categories", StoreItemView.Category.values())));

        app.post("/admin/store", ctx -> {
            String name = ctx.formParam("name");
            String error = store.createItem(name, ctx.formParam("description"),
                    longParam(ctx, "price"), ctx.formParam("category"), ctx.formParam("effect"));
            if (error != null) Flash.error(ctx, error);
            else Flash.success(ctx, "Item '" + name + "' added to the store.");
            ctx.redirect("/admin/store");
        });

        app.post("/admin/store/{id}/delete", ctx -> {
            store.deleteItem(uuid(ctx.pathParam("id")));
            Flash.success(ctx, "Store item deleted.");
            ctx.redirect("/admin/store");
        });
    }

    // ── param helpers (lenient like the old CustomNumberEditor binding) ──────

    private static UUID uuid(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static long longParam(Context ctx, String name) {
        String raw = ctx.formParam(name);
        if (raw == null || raw.isBlank()) return 0L;
        try {
            return Math.round(Double.parseDouble(raw.trim()));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static float floatParam(Context ctx, String name) {
        String raw = ctx.formParam(name);
        if (raw == null || raw.isBlank()) return 0f;
        try {
            return Math.round(Float.parseFloat(raw.trim()));
        } catch (NumberFormatException e) {
            return 0f;
        }
    }
}
