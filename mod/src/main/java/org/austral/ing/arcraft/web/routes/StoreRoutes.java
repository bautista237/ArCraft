package org.austral.ing.arcraft.web.routes;

import io.javalin.Javalin;
import org.austral.ing.arcraft.web.Flash;
import org.austral.ing.arcraft.web.Renderer;
import org.austral.ing.arcraft.web.model.PlayerView;
import org.austral.ing.arcraft.web.service.CoinPackage;
import org.austral.ing.arcraft.web.service.MercadoPagoService;
import org.austral.ing.arcraft.web.service.StoreService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Store page, coin purchases (MercadoPago) and item buy/equip/toggle (ex StoreController). */
public final class StoreRoutes {

    private StoreRoutes() {
    }

    public static void register(Javalin app) {
        StoreService store = StoreService.INSTANCE;
        MercadoPagoService mp = MercadoPagoService.INSTANCE;

        app.get("/store", ctx -> {
            PlayerView player = ClanRoutes.currentPlayer(ctx);
            Map<String, Object> m = new HashMap<>();
            m.put("items", store.getAllItems());
            m.put("ownedItemIds", player != null ? store.getOwnedItemIds(player.getId()) : Set.of());
            m.put("coins", player != null ? player.getCoins() : 0L);
            m.put("purchases", player != null ? store.getPurchases(player.getId()) : List.of());
            m.put("equippedTitle", player != null ? store.getEquippedTitle(player.getId()) : null);
            m.put("coinPackages", CoinPackage.all());
            m.put("mpEnabled", mp.isConfigured());
            m.put("currency", mp.getCurrency());
            Renderer.render(ctx, "store", m);
        });

        // ── Buy coins with real (test) money via MercadoPago ────────────────

        app.post("/store/coins/checkout", ctx -> {
            PlayerView player = ClanRoutes.currentPlayer(ctx);
            CoinPackage selected = CoinPackage.bySlug(ctx.formParam("pack"));
            if (player == null || selected == null) {
                Flash.error(ctx, "Invalid coin package.");
                ctx.redirect("/store");
                return;
            }
            try {
                ctx.redirect(mp.startCheckout(player, selected));
            } catch (MercadoPagoService.MercadoPagoException e) {
                Flash.error(ctx, e.getMessage());
                ctx.redirect("/store");
            }
        });

        // MercadoPago redirects the player's browser back here after payment.
        app.get("/store/coins/return", ctx -> {
            String paymentId = ctx.queryParam("payment_id");
            String status = ctx.queryParam("status");
            String merchantOrderId = ctx.queryParam("merchant_order_id");
            MercadoPagoService.Order credited = null;
            if (paymentId != null) credited = mp.confirmByPayment(paymentId);
            if (credited == null && merchantOrderId != null) {
                credited = mp.confirmByMerchantOrder(merchantOrderId);
            }
            if (credited != null) {
                Flash.success(ctx, "Payment confirmed! " + credited.coins() + " coins added to your balance.");
            } else if ("approved".equalsIgnoreCase(status)) {
                Flash.success(ctx, "Payment received — your coins will appear shortly.");
            } else if ("pending".equalsIgnoreCase(status)) {
                Flash.error(ctx, "Your payment is pending. Coins will be added once it's approved.");
            } else {
                Flash.error(ctx, "Payment was not completed.");
            }
            ctx.redirect("/store");
        });

        // MercadoPago server-to-server notification (no browser/session). Always answer 200.
        app.post("/store/coins/webhook", ctx -> {
            String kind = ctx.queryParam("type") != null ? ctx.queryParam("type") : ctx.queryParam("topic");
            String resourceId = ctx.queryParam("data.id") != null ? ctx.queryParam("data.id") : ctx.queryParam("id");
            try {
                if ("payment".equals(kind)) {
                    mp.confirmByPayment(resourceId);
                } else if ("merchant_order".equals(kind)) {
                    mp.confirmByMerchantOrder(resourceId);
                }
            } catch (Exception ignored) {
                // Never fail the webhook — MercadoPago retries on non-200.
            }
            ctx.result("ok");
        });

        // ── Coin-store items ─────────────────────────────────────────────────

        app.post("/store/buy", ctx -> {
            PlayerView player = ClanRoutes.currentPlayer(ctx);
            if (player == null) {
                Flash.error(ctx, "You must be logged in to buy.");
            } else {
                String error = store.purchase(player, uuidParam(ctx.formParam("itemId")));
                if (error != null) Flash.error(ctx, error);
                else Flash.success(ctx, "Purchase complete!");
            }
            ctx.redirect("/store");
        });

        app.post("/store/equip", ctx -> {
            PlayerView player = ClanRoutes.currentPlayer(ctx);
            if (player == null) {
                Flash.error(ctx, "You must be logged in.");
            } else {
                String error = store.equipTitle(player.getId(), uuidParam(ctx.formParam("purchaseId")));
                if (error != null) Flash.error(ctx, error);
                else Flash.success(ctx, "Title equipped.");
            }
            ctx.redirect("/store");
        });

        app.post("/store/toggle", ctx -> {
            PlayerView player = ClanRoutes.currentPlayer(ctx);
            if (player == null) {
                Flash.error(ctx, "You must be logged in.");
            } else {
                Boolean nowEquipped = store.toggleCosmetic(player.getId(), uuidParam(ctx.formParam("purchaseId")));
                if (nowEquipped == null) Flash.error(ctx, "That cosmetic could not be toggled.");
                else Flash.success(ctx, nowEquipped ? "Cosmetic equipped." : "Cosmetic unequipped.");
            }
            ctx.redirect("/store");
        });
    }

    static UUID uuidParam(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
