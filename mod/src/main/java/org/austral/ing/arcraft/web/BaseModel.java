package org.austral.ing.arcraft.web;

import io.javalin.http.Context;
import org.austral.ing.arcraft.ArcraftConfig;
import org.austral.ing.arcraft.db.Database;
import org.austral.ing.arcraft.web.service.IconService;
import org.austral.ing.arcraft.web.service.SkinService;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-request model values shared by every page (the old Spring {@code GlobalModelAttributes}):
 * navbar identity/coins/clan, admin flag, feature-gating flags, and the {@code skinService}
 * helper the templates call for avatar URLs.
 */
final class BaseModel {

    private BaseModel() {
    }

    static Map<String, Object> build(Context ctx) {
        Map<String, Object> m = new HashMap<>();
        SessionUser user = SessionUser.current(ctx);
        m.put("navUsername", user == null ? null : user.username());
        m.put("isAdmin", user != null && user.admin());
        m.put("navCoins", null);
        m.put("navClanTag", null);
        if (user != null) {
            Database.jdbi().useHandle(h -> h
                    .createQuery("""
                            SELECT p.coins, c.tag FROM player p
                            LEFT JOIN clan c ON p.clan_id = c.id
                            WHERE p.username = :u
                            """)
                    .bind("u", user.username())
                    .map((rs, c) -> {
                        m.put("navCoins", rs.getLong("coins"));
                        m.put("navClanTag", rs.getString("tag"));
                        return null;
                    })
                    .findFirst());
        }
        // Feature gating: blank credentials ⇒ the section/page disappears from the UI.
        m.put("mpEnabled", ArcraftConfig.mercadoPagoConfigured());
        m.put("geminiEnabled", ArcraftConfig.geminiConfigured());
        m.put("mailEnabled", ArcraftConfig.mailConfigured());
        m.put("minecraftLoginEnabled", false); // OAuth2 login not wired in the single-jar build
        m.put("skinService", SkinService.INSTANCE);
        m.put("iconService", IconService.INSTANCE);
        // Flash messages from the previous redirect (old RedirectAttributes behavior).
        String err = Flash.pop(ctx, Flash.ERROR);
        String ok = Flash.pop(ctx, Flash.SUCCESS);
        if (err != null) m.put("error", err);
        if (ok != null) m.put("success", ok);
        return m;
    }
}
