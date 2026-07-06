package org.austral.ing.arcraft.web.routes;

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.austral.ing.arcraft.web.Flash;
import org.austral.ing.arcraft.web.Renderer;
import org.austral.ing.arcraft.web.SessionUser;
import org.austral.ing.arcraft.web.dao.Daos;
import org.austral.ing.arcraft.web.model.ClanView;
import org.austral.ing.arcraft.web.model.PlayerView;
import org.austral.ing.arcraft.web.model.StatsView;
import org.austral.ing.arcraft.web.service.ClanService;
import org.austral.ing.arcraft.web.service.RankingsService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Clan list/profile pages, membership actions and the clan-chat endpoints (ex ClanController). */
public final class ClanRoutes {

    private static final DateTimeFormatter CHAT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ClanRoutes() {
    }

    public static void register(Javalin app) {
        ClanService clans = ClanService.INSTANCE;

        app.get("/clans", ctx -> {
            List<Map<String, Object>> clanRows = new ArrayList<>();
            for (ClanView clan : clans.getAllClans()) {
                Map<String, Object> row = new HashMap<>();
                row.put("clan", clan);
                row.put("memberCount", clans.getMembers(clan.getId()).size());
                long[] agg = clans.getAggregateStats(clan.getId());
                row.put("totalKills", agg[0]);
                row.put("totalMobsKilled", agg[2]);
                row.put("totalBlocksMined", agg[3]);
                clanRows.add(row);
            }
            Renderer.render(ctx, "clans", Map.of("clanRows", clanRows));
        });

        app.get("/clans/{tag}", ctx -> {
            ClanView clan = clanOr404(ctx, "/clans");
            if (clan == null) return;

            List<PlayerView> members = clans.getMembers(clan.getId());
            long[] agg = clans.getAggregateStats(clan.getId());

            List<Map<String, Object>> memberRows = new ArrayList<>();
            for (PlayerView member : members) {
                Map<String, Object> row = new HashMap<>();
                row.put("player", member);
                StatsView s = Daos.statsByPlayerId(member.getId()).orElse(null);
                row.put("kills", s != null ? s.getKills() : 0L);
                row.put("deaths", s != null ? s.getDeaths() : 0L);
                row.put("kdRatio", s != null ? RankingsService.computeKdRatio(s) : 0.0);
                row.put("mobsKilled", s != null ? s.getMobsKilled() : 0L);
                memberRows.add(row);
            }

            PlayerView current = currentPlayer(ctx);
            boolean isMember = current != null && current.getClan() != null
                    && current.getClan().getId().equals(clan.getId());
            boolean hasNoClan = current != null && current.getClan() == null;
            boolean isLeader = current != null && clan.getLeader() != null
                    && clan.getLeader().getId().equals(current.getId());

            Map<String, Object> m = new HashMap<>();
            m.put("clan", clan);
            m.put("memberRows", memberRows);
            m.put("memberCount", members.size());
            m.put("totalKills", agg[0]);
            m.put("totalDeaths", agg[1]);
            m.put("totalMobsKilled", agg[2]);
            m.put("totalBlocksMined", agg[3]);
            m.put("totalBlocksPlaced", agg[4]);
            m.put("totalItemsCrafted", agg[5]);
            m.put("totalDistance", agg[6]);
            if (isMember) {
                m.put("messages", clans.getRecentMessages(clan.getId(), 50));
            }
            m.put("isMember", isMember);
            m.put("hasNoClan", hasNoClan);
            m.put("isLeader", isLeader);
            m.put("leaderId", clan.getLeader() != null ? clan.getLeader().getId() : null);
            Renderer.render(ctx, "clan-profile", m);
        });

        // Live clan-chat feed (JSON) for members — polled to show in-game messages.
        app.get("/clans/{tag}/messages", ctx -> {
            ClanView clan = clans.findByTag(ctx.pathParam("tag")).orElse(null);
            PlayerView player = currentPlayer(ctx);
            boolean isMember = clan != null && player != null && player.getClan() != null
                    && player.getClan().getId().equals(clan.getId());
            if (!isMember) {
                ctx.json(List.of());
                return;
            }
            List<Map<String, String>> out = new ArrayList<>();
            for (ClanService.Message msg : clans.getRecentMessages(clan.getId(), 50)) {
                out.add(Map.of(
                        "sender", msg.sender().getUsername(),
                        "content", msg.content(),
                        "time", msg.sentAt().atZone(ZoneId.systemDefault()).toLocalDateTime().format(CHAT_TIME)));
            }
            ctx.json(out);
        });

        app.post("/clans/{tag}/message", ctx -> {
            ClanView clan = clanOr404(ctx, "/clans");
            if (clan == null) return;
            PlayerView player = currentPlayer(ctx);
            boolean isMember = player != null && player.getClan() != null
                    && player.getClan().getId().equals(clan.getId());
            if (!isMember) {
                Flash.error(ctx, "You must be a member of this clan to send messages.");
                ctx.redirect("/clans/" + clan.getTag());
                return;
            }
            String content = ctx.formParam("content");
            if (content != null && !content.isBlank()) {
                clans.postMessage(clan.getId(), player.getId(), content.trim());
            }
            ctx.redirect("/clans/" + clan.getTag());
        });

        app.post("/clans/{tag}/join", ctx -> {
            ClanView clan = clanOr404(ctx, "/clans");
            if (clan == null) return;
            PlayerView player = currentPlayer(ctx);
            if (player == null) return;
            if (player.getClan() != null) {
                Flash.error(ctx, "You are already in a clan.");
            } else {
                clans.joinClan(player.getId(), clan.getId());
                Flash.success(ctx, "You joined " + clan.getName() + "!");
            }
            ctx.redirect("/clans/" + clan.getTag());
        });

        app.post("/clans/{tag}/leave", ctx -> {
            ClanView clan = clanOr404(ctx, "/clans");
            if (clan == null) return;
            PlayerView player = currentPlayer(ctx);
            if (player == null) return;
            String error = clans.leaveClan(player, clan);
            if (error != null) Flash.error(ctx, error);
            else Flash.success(ctx, "You left " + clan.getName() + ".");
            ctx.redirect("/clans/" + clan.getTag());
        });

        app.post("/clans/{tag}/kick", ctx -> {
            ClanView clan = clanOr404(ctx, "/clans");
            if (clan == null) return;
            PlayerView leader = currentPlayer(ctx);
            PlayerView target = Daos.playerByUsername(ctx.formParam("username")).orElse(null);
            if (leader == null || target == null) {
                Flash.error(ctx, "Player not found.");
            } else {
                String error = clans.kickMember(leader, target, clan);
                if (error != null) Flash.error(ctx, error);
                else Flash.success(ctx, "Removed " + target.getUsername() + " from the clan.");
            }
            ctx.redirect("/clans/" + clan.getTag());
        });
    }

    private static ClanView clanOr404(Context ctx, String fallback) {
        Optional<ClanView> clan = ClanService.INSTANCE.findByTag(ctx.pathParam("tag"));
        if (clan.isEmpty()) {
            Flash.error(ctx, "Clan no longer exists.");
            ctx.redirect(fallback);
            return null;
        }
        return clan.get();
    }

    static PlayerView currentPlayer(Context ctx) {
        SessionUser u = SessionUser.current(ctx);
        return u == null ? null : Daos.playerByUsername(u.username()).orElse(null);
    }
}
