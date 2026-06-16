package org.austral.ing.arcraft.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Exchanges a Microsoft OAuth2 access token (obtained with the {@code XboxLive.signin} scope)
 * for the player's Minecraft profile, following the documented chain:
 *
 * <pre>
 *   MS access token → Xbox Live (user.auth.xboxlive.com)
 *                   → XSTS      (xsts.auth.xboxlive.com)
 *                   → Minecraft (api.minecraftservices.com/authentication/login_with_xbox)
 *                   → Profile   (api.minecraftservices.com/minecraft/profile)
 * </pre>
 *
 * Reference: <a href="https://wiki.vg/Microsoft_Authentication_Scheme">wiki.vg Microsoft Authentication Scheme</a>.
 */
@Service
public class MinecraftAuthService {

    private static final Logger log = LoggerFactory.getLogger(MinecraftAuthService.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public record MinecraftProfile(UUID uuid, String username) {}

    /** Runs the full chain. Throws {@link MinecraftAuthException} on any failure. */
    public MinecraftProfile resolveProfile(String microsoftAccessToken) {
        try {
            String xblToken = authenticateWithXboxLive(microsoftAccessToken);
            XstsResult xsts = authenticateWithXsts(xblToken);
            String mcToken = loginWithXbox(xsts.userHash(), xsts.token());
            return fetchProfile(mcToken);
        } catch (MinecraftAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new MinecraftAuthException("Minecraft authentication failed: " + e.getMessage(), e);
        }
    }

    private String authenticateWithXboxLive(String msAccessToken) throws Exception {
        String body = """
            {
              "Properties": {
                "AuthMethod": "RPS",
                "SiteName": "user.auth.xboxlive.com",
                "RpsTicket": "d=%s"
              },
              "RelyingParty": "http://auth.xboxlive.com",
              "TokenType": "JWT"
            }""".formatted(msAccessToken);
        JsonNode json = postJson("https://user.auth.xboxlive.com/user/authenticate", body, null);
        return json.path("Token").asText();
    }

    private record XstsResult(String token, String userHash) {}

    private XstsResult authenticateWithXsts(String xblToken) throws Exception {
        String body = """
            {
              "Properties": {
                "SandboxId": "RETAIL",
                "UserTokens": ["%s"]
              },
              "RelyingParty": "rp://api.minecraftservices.com/",
              "TokenType": "JWT"
            }""".formatted(xblToken);
        JsonNode json = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", body, null);
        String token = json.path("Token").asText();
        String userHash = json.path("DisplayClaims").path("xui").path(0).path("uhs").asText();
        return new XstsResult(token, userHash);
    }

    private String loginWithXbox(String userHash, String xstsToken) throws Exception {
        String body = "{\"identityToken\":\"XBL3.0 x=%s;%s\"}".formatted(userHash, xstsToken);
        JsonNode json = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", body, null);
        return json.path("access_token").asText();
    }

    private MinecraftProfile fetchProfile(String minecraftToken) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .header("Authorization", "Bearer " + minecraftToken)
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404) {
            throw new MinecraftAuthException("This Microsoft account does not own Minecraft.");
        }
        if (res.statusCode() / 100 != 2) {
            throw new MinecraftAuthException("Could not load Minecraft profile (HTTP " + res.statusCode() + ").");
        }
        JsonNode json = mapper.readTree(res.body());
        String id = json.path("id").asText();          // 32-char undashed UUID
        String name = json.path("name").asText();
        return new MinecraftProfile(undashedToUuid(id), name);
    }

    private JsonNode postJson(String url, String body, String bearer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            log.warn("[ArCraft] Auth step {} returned HTTP {}: {}", url, res.statusCode(), res.body());
            throw new MinecraftAuthException("Xbox/Minecraft auth step failed (HTTP " + res.statusCode() + ").");
        }
        return mapper.readTree(res.body());
    }

    static UUID undashedToUuid(String id) {
        if (id == null || id.length() != 32) {
            return UUID.randomUUID();
        }
        String dashed = id.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5");
        return UUID.fromString(dashed);
    }

    public static class MinecraftAuthException extends RuntimeException {
        public MinecraftAuthException(String message) { super(message); }
        public MinecraftAuthException(String message, Throwable cause) { super(message, cause); }
    }
}
