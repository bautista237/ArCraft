package org.austral.ing.arcraft.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.CoinOrder;
import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.repository.CoinOrderRepository;
import org.austral.ing.arcraft.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * <p>Flow: {@link #startCheckout} creates a {@link CoinOrder} and a MercadoPago "preference"
 * (the order on MP's side) via the REST API, then returns the hosted checkout URL we redirect
 * the player to. When MercadoPago confirms payment it both redirects the player back to our
 * return URL and POSTs a webhook; {@link #confirmByPayment} / {@link #confirmByMerchantOrder}
 * resolve the payment, and {@link #creditOrder} credits coins exactly once.</p>
 *
 * <p>Credentials come from {@code arcraft.mercadopago.*} (env / gitignored secret file). With
 * TEST credentials no real money moves; pay with MercadoPago's test cards or a test user.</p>
 */
@Service
@RequiredArgsConstructor
public class MercadoPagoService {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoService.class);
    private static final String API = "https://api.mercadopago.com";

    private final CoinOrderRepository coinOrderRepository;
    private final PlayerRepository playerRepository;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${arcraft.mercadopago.enabled:false}")
    private boolean enabled;
    @Value("${arcraft.mercadopago.access-token:}")
    private String accessToken;
    @Value("${arcraft.mercadopago.public-key:}")
    private String publicKey;
    @Value("${arcraft.mercadopago.currency:ARS}")
    private String currency;
    @Value("${arcraft.app.base-url:http://localhost:8080}")
    private String baseUrl;

    /** Whether checkout is usable (feature enabled and an access token configured). */
    public boolean isConfigured() {
        return enabled && accessToken != null && !accessToken.isBlank();
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getCurrency() {
        return currency;
    }

    public static class MercadoPagoException extends RuntimeException {
        public MercadoPagoException(String message) { super(message); }
    }

    /**
     * Create the order + MercadoPago preference and return the checkout URL to redirect to.
     */
    @Transactional
    public String startCheckout(Player player, CoinPackage pack) {
        if (!isConfigured()) {
            throw new MercadoPagoException("Coin purchases are not configured on this server.");
        }
        CoinOrder order = new CoinOrder(player, pack.coins, pack.price);
        order = coinOrderRepository.save(order); // get an id for external_reference

        try {
            ObjectNode body = mapper.createObjectNode();
            ArrayNode items = body.putArray("items");
            ObjectNode item = items.addObject();
            item.put("title", pack.coins + " ArCraft coins (" + pack.slug + ")");
            item.put("description", "In-game coins for the ArCraft store");
            item.put("quantity", 1);
            item.put("currency_id", currency);
            item.put("unit_price", pack.price);

            body.put("external_reference", order.getId().toString());
            body.put("statement_descriptor", "ARCRAFT");

            ObjectNode backUrls = body.putObject("back_urls");
            String ret = baseUrl + "/store/coins/return";
            backUrls.put("success", ret);
            backUrls.put("failure", ret);
            backUrls.put("pending", ret);
            // MercadoPago rejects auto_return when back_urls point at localhost, so only enable it
            // for a real deployed URL (where it auto-redirects the buyer back after approval).
            boolean localBase = baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1");
            if (!localBase) {
                body.put("auto_return", "approved");
            }

            // MercadoPago must reach this publicly; on localhost it simply won't fire, which is
            // fine because the return URL also confirms the payment.
            body.put("notification_url", baseUrl + "/store/coins/webhook");

            JsonNode res = post("/checkout/preferences", body);
            String preferenceId = res.path("id").asText(null);
            order.setPreferenceId(preferenceId);
            coinOrderRepository.save(order);

            // init_point is the live checkout; sandbox_init_point is the older sandbox URL.
            String initPoint = res.path("init_point").asText(null);
            if (initPoint == null || initPoint.isBlank()) {
                initPoint = res.path("sandbox_init_point").asText(null);
            }
            if (initPoint == null || initPoint.isBlank()) {
                throw new MercadoPagoException("MercadoPago did not return a checkout URL.");
            }
            log.info("[ArCraft] MercadoPago checkout started: order={} pref={} {} coins for {} {}",
                    order.getId(), preferenceId, pack.coins, pack.price, currency);
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
    @Transactional
    public CoinOrder confirmByPayment(String paymentId) {
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
    @Transactional
    public CoinOrder confirmByMerchantOrder(String merchantOrderId) {
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
    @Transactional
    public CoinOrder creditOrder(UUID orderId, String paymentId) {
        CoinOrder order = coinOrderRepository.findById(orderId).orElse(null);
        if (order == null) return null;
        if (order.getStatus() == CoinOrder.Status.PAID) {
            return order; // already credited
        }
        Player player = playerRepository.findById(order.getPlayerId()).orElse(null);
        if (player == null) {
            log.warn("[ArCraft] Cannot credit order {} — player {} gone", orderId, order.getPlayerId());
            return null;
        }
        player.setCoins(player.getCoins() + order.getCoins());
        playerRepository.save(player);

        order.setStatus(CoinOrder.Status.PAID);
        order.setPaymentId(paymentId);
        order.setPaidAt(Instant.now());
        coinOrderRepository.save(order);
        log.info("[ArCraft] Credited {} coins to {} (order {}, payment {})",
                order.getCoins(), player.getUsername(), orderId, paymentId);
        return order;
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    private JsonNode post(String path, ObjectNode body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API + path))
                .header("Authorization", "Bearer " + accessToken)
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
                .header("Authorization", "Bearer " + accessToken)
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
