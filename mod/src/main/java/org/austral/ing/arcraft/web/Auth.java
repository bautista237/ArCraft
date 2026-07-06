package org.austral.ing.arcraft.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.austral.ing.arcraft.web.dao.Daos;
import org.mindrot.jbcrypt.BCrypt;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * Session auth for the dashboard (replaces Spring Security):
 * <ul>
 *   <li>every page requires login except the public paths below (same policy as before);</li>
 *   <li>credentials = the player's in-game account (BCrypt hash written by the mod);</li>
 *   <li>{@code /admin/**} requires the admin flag;</li>
 *   <li>CSRF: the session cookie is SameSite=Lax AND mutating requests must come from our own
 *       origin (Origin/Referer check) — with the payment webhook exempted, like before.</li>
 * </ul>
 */
final class Auth {

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/login", "/css", "/js", "/images", "/health", "/favicon", "/store/coins/webhook");

    private Auth() {
    }

    static void register(Javalin app) {
        app.before(Auth::gate);

        app.get("/login", ctx -> {
            if (SessionUser.current(ctx) != null) {
                ctx.redirect("/dashboard");
                return;
            }
            Renderer.render(ctx, "login", Map.of());
        });

        app.post("/login", ctx -> {
            String username = ctx.formParam("username");
            String password = ctx.formParam("password");
            var row = username == null ? java.util.Optional.<Daos.AuthRow>empty() : Daos.auth(username);
            if (password != null && row.isPresent()
                    && !row.get().passwordHash().isBlank()
                    && BCrypt.checkpw(password, row.get().passwordHash())) {
                SessionUser.login(ctx, new SessionUser(row.get().id(), row.get().username(), row.get().admin()));
                ctx.redirect("/dashboard");
            } else {
                ctx.redirect("/login?error");
            }
        });

        // POST is the real logout (the navbar form); GET kept as a convenience.
        app.post("/logout", Auth::logout);
        app.get("/logout", Auth::logout);
    }

    private static void logout(Context ctx) {
        SessionUser.logout(ctx);
        ctx.redirect("/login?logout");
    }

    private static void gate(Context ctx) {
        String path = ctx.path();
        boolean isPublic = PUBLIC_PREFIXES.stream()
                .anyMatch(p -> path.equals(p) || path.startsWith(p + "/") || path.startsWith(p + "."));

        // Cross-origin write protection (except the MercadoPago webhook, which is server-to-server).
        if (!"GET".equals(ctx.method().name()) && !"HEAD".equals(ctx.method().name())
                && !path.startsWith("/store/coins/webhook") && !sameOrigin(ctx)) {
            ctx.status(403).result("Cross-origin request rejected");
            ctx.skipRemainingHandlers();
            return;
        }

        if (isPublic || SessionUser.current(ctx) != null) {
            if (path.startsWith("/admin")) {
                SessionUser u = SessionUser.current(ctx);
                if (u == null || !u.admin()) {
                    ctx.status(403).result("Admins only");
                    ctx.skipRemainingHandlers();
                }
            }
            return;
        }
        ctx.redirect("/login");
        ctx.skipRemainingHandlers();
    }

    /** True when Origin (or Referer) is absent or its host equals the Host header's host. */
    private static boolean sameOrigin(Context ctx) {
        String origin = ctx.header("Origin");
        String source = origin != null ? origin : ctx.header("Referer");
        if (source == null || source.isBlank() || "null".equals(source)) {
            return true; // non-browser clients (curl, server-to-server) carry the session anyway
        }
        try {
            String srcHost = URI.create(source).getHost();
            String host = ctx.header("Host");
            if (host == null) return false;
            int colon = host.indexOf(':');
            String reqHost = colon >= 0 ? host.substring(0, colon) : host;
            return reqHost.equalsIgnoreCase(srcHost);
        } catch (Exception e) {
            return false;
        }
    }
}
