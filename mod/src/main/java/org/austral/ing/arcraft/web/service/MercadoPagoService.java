package org.austral.ing.arcraft.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.austral.ing.arcraft.ArcraftConfig;
import org.austral.ing.arcraft.db.Database;
import org.austral.ing.arcraft.web.dao.Daos;
import org.austral.ing.arcraft.web.model.PlayerView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * MercadoPago Checkout Pro integration for buying coins.
 *
 * <p>Flow: {@link #startCheckout} inserts a coin_order row and creates a MercadoPago
 * "preference" via the REST API, then returns the hosted checkout URL we redirect the player
 * to. When MercadoPago confirms payment it both redirects the player back to our return URL
 * and POSTs a webhook; {@link #confirmByPayment} / {@link #confirmByMerchantOrder} resolve the
 * payment and {@link #creditOrder} credits coins exactly once (idempotent).</p>
 *
 * <p>Credentials come from the {@code [mercadopago]} section of config/arcraft-common.toml.
 * With TEST credentials no real money moves.</p>
 */
public final class MercadoPagoService {

    public static final MercadoPagoService INSTANCE = new MercadoPagoService();

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoService.class);
    private static final String API = "https://api.mercadopago.com";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private MercadoPagoService() {
    }

    public boolean isConfigured() {
        return ArcraftConfig.mercadoPagoConfigured();
    }

    public String getCurrency() {
        return ArcraftConfig.MP_CURRENCY.get();
    }

    public static class MercadoPagoException extends RuntimeException {
        public MercadoPagoException(String message) { super(message); }
    }

    /** Order status + coins, read back when confirming payments. */
    public record Order(UUID id, UUID playerId, String username, long coins, String status) {}

    /** Create the order + MercadoPago preference and return the checkout URL to redirect to. */
    public String startCheckout(PlayerView player, CoinPackage pack) {
        if (!isConfigured()) {
            throw new MercadoPagoException("Coin purchases are not configured on this server.");
        }
        String baseUrl = ArcraftConfig.baseUrl();
        String currency = getCurrency();
        UUID orderId = UUID.randomUUID();
        Database.jdbi().useHandle(h -> h
                .createUpdate("""
                        INSERT INTO coin_order (id, player_id, username, coins, amount, status, created_at)
                        VALUES (:id, :pid, :user, :coins, :amount, 'PENDING', :created)
                        """)
                .bind("id", orderId)
                .bind("pid", player.getId())
                .bind("user", player.getUsername())
                .bind("coins", pack.coins)
                .bind("amount", pack.price)
                .bind("created", Instant.now())
                .execute());

        try {
            ObjectNode body = mapper.createObjectNode();
            ArrayNode items = body.putArray("items");
            ObjectNode item = items.addObject();
            item.put("title", pack.coins + " ArCraft coins (" + pack.slug + ")");
            item.put("description", "In-game coins for the ArCraft store");
            item.put("quantity", 1);
            item.put("currency_id", currency);
            item.put("unit_price", pack.price);

            body.put("external_reference", orderId.toString());
            body.put("statement_descriptor", "ARCRAFT");

            ObjectNode backUrls = body.putObject("back_urls");
            String ret = baseUrl + "/store/coins/return";
            backUrls.put("success", ret);
            backUrls.put("failure", ret);
            backUrls.put("pending", ret);
            // MercadoPago rejects auto_return when back_urls point at localhost, so only enable
            // it for a real deployed URL (where it auto-redirects the buyer back after approval).
            boolean localBase = baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1");
            if (!localBase) {
                body.put("auto_return", "approved");
            }
            // MercadoPago must reach this publicly; on localhost it simply won't fire, which is
            // fine because the return URL also confirms the payment.
            body.put("notification_url", baseUrl + "/store/coins/webhook");

            JsonNode res = post("/checkout/preferences", body);
            String preferenceId = res.path("id").asText(null);
            Database.jdbi().useHandle(h -> h
                    .createUpdate("UPDATE coin_order SET preference_id = :pref WHERE id = :id")
                    .bind("pref", preferenceId).bind("id", orderId).execute());

            String initPoint = res.path("init_point").asText(null);
            if (initPoint == null || initPoint.isBlank()) {
                initPoint = res.path("sandbox_init_point").asText(null);
            }
            if (initPoint == null || initPoint.isBlank()) {
                throw new MercadoPagoException("MercadoPago did not return a checkout URL.");
            }
            log.info("[ArCraft] MercadoPago checkout started: order={} pref={} {} coins for {} {}",
                    orderId, preferenceId, pack.coins, pack.price, currency);
            return initPoint;
        } catch (MercadoPagoException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ArCraft] MercadoPago preference creation failed", e);
            throw new MercadoPagoException("Could not start checkout: " + e.getMessage());
        }
    }

    /**
     * Resolve and credit a payment by its id (used by both the browser return and the webhook).
     * Returns the credited order, or null if the payment isn't approved / can't be resolved.
     */
    public Order confirmByPayment(String paymentId) {
        if (paymentId == null || paymentId.isBlank() || !isConfigured()) return null;
        try {
            JsonNode pay = get("/v1/payments/" + paymentId);
            String status = pay.path("status").asText("");
            String externalRef = pay.path("external_reference").asText(null);
            if (!"approved".equals(status)) {
                log.info("[ArCraft] Payment {} not approved (status={})", paymentId, status);
                return null;
            }
            if (externalRef == null) return null;
            return creditOrder(UUID.fromString(externalRef), paymentId);
        } catch (Exception e) {
            log.warn("[ArCraft] confirmByPayment({}) failed: {}", paymentId, e.toString());
            return null;
        }
    }

    /** Resolve a merchant order (the webhook/return sometimes gives this) into its payment(s). */
    public Order confirmByMerchantOrder(String merchantOrderId) {
        if (merchantOrderId == null || merchantOrderId.isBlank() || !isConfigured()) return null;
        try {
            JsonNode mo = get("/merchant_orders/" + merchantOrderId);
            for (JsonNode p : mo.path("payments")) {
                if ("approved".equals(p.path("status").asText(""))) {
                    return confirmByPayment(p.path("id").asText());
                }
            }
        } catch (Exception e) {
            log.warn("[ArCraft] confirmByMerchantOrder({}) failed: {}", merchantOrderId, e.toString());
        }
        return null;
    }

    /** Credit coins for an order exactly once. Safe to call repeatedly (return + webhook). */
    public Order creditOrder(UUID orderId, String paymentId) {
        return Database.jdbi().inTransaction(h -> {
            Order order = h.createQuery(
                            "SELECT id, player_id, username, coins, status FROM coin_order WHERE id = :id")
                    .bind("id", orderId)
                    .map((rs, c) -> new Order(Daos.uuid(rs, "id"), Daos.uuid(rs, "player_id"),
                            rs.getString("username"), rs.getLong("coins"), rs.getString("status")))
                    .findFirst().orElse(null);
            if (order == null) return null;
            if ("PAID".equals(order.status())) {
                return order; // already credited
            }
            int updated = h.createUpdate("UPDATE player SET coins = coins + :c WHERE id = :pid")
                    .bind("c", order.coins()).bind("pid", order.playerId()).execute();
            if (updated == 0) {
                log.warn("[ArCraft] Cannot credit order {} — player {} gone", orderId, order.playerId());
                return null;
            }
            h.createUpdate("UPDATE coin_order SET status = 'PAID', payment_id = :pay, paid_at = :at WHERE id = :id")
                    .bind("pay", paymentId).bind("at", Instant.now()).bind("id", orderId).execute();
            log.info("[ArCraft] Credited {} coins to {} (order {}, payment {})",
                    order.coins(), order.username(), orderId, paymentId);
            return order;
        });
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    private JsonNode post(String path, ObjectNode body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API + path))
                .header("Authorization", "Bearer " + ArcraftConfig.MP_ACCESS_TOKEN.get())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            log.warn("[ArCraft] MP POST {} -> {} {}", path, res.statusCode(), res.body());
            throw new MercadoPagoException("MercadoPago error (HTTP " + res.statusCode() + ").");
        }
        return mapper.readTree(res.body());
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API + path))
                .header("Authorization", "Bearer " + ArcraftConfig.MP_ACCESS_TOKEN.get())
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            log.warn("[ArCraft] MP GET {} -> {} {}", path, res.statusCode(), res.body());
            throw new MercadoPagoException("MercadoPago error (HTTP " + res.statusCode() + ").");
        }
        return mapper.readTree(res.body());
    }
}
