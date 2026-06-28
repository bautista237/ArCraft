package org.austral.ing.arcraft.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin client for Google's Gemini API. Answers natural-language questions about the ArCraft
 * database by grounding the model with a snapshot from {@link AiContextService}. The API key
 * comes from {@code arcraft.gemini.api-key} (env / gitignored secret file).
 */
@Service
@RequiredArgsConstructor
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final AiContextService aiContextService;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${arcraft.gemini.enabled:false}")
    private boolean enabled;
    @Value("${arcraft.gemini.api-key:}")
    private String apiKey;
    // Comma-separated fallback chain. The first model is tried first; on transient errors
    // (429 rate-limit / 503 overloaded) we retry and then fall back to the next model, each of
    // which has its own quota/load. Free-tier keys vary a lot in which models they can use.
    @Value("${arcraft.gemini.model:gemini-2.5-flash,gemini-2.5-flash-lite,gemini-flash-latest}")
    private String model;

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    private List<String> modelChain() {
        List<String> chain = new ArrayList<>();
        for (String m : model.split(",")) {
            String t = m.trim();
            if (!t.isEmpty() && !chain.contains(t)) chain.add(t);
        }
        if (chain.isEmpty()) chain.add("gemini-2.5-flash");
        return chain;
    }

    /** Answer a question about the server's data. Never throws — returns a friendly message. */
    public String ask(String question) {
        if (!isConfigured()) {
            return "The AI assistant isn't configured on this server yet (missing Gemini API key).";
        }
        if (question == null || question.isBlank()) {
            return "Ask me something about the server — players, kills, clans, events…";
        }
        try {
            String context = aiContextService.buildContext();
            String prompt = """
                You are ArCraft Assistant, a friendly analyst for a private Minecraft server's stats dashboard.
                Answer the user's question using ONLY the data snapshot below. The data is the source of truth.
                Be concise and conversational. Use numbers from the data. If the answer isn't in the data, say so.
                Do not invent players, clans, or stats. You may do simple math (totals, ratios, comparisons).

                ===== DATA SNAPSHOT =====
                %s
                ===== END DATA =====

                User question: %s
                """.formatted(context, question);

            ObjectNode body = mapper.createObjectNode();
            ArrayNode contents = body.putArray("contents");
            ObjectNode content = contents.addObject();
            ArrayNode parts = content.putArray("parts");
            parts.addObject().put("text", prompt);
            ObjectNode genConfig = body.putObject("generationConfig");
            genConfig.put("temperature", 0.4);
            genConfig.put("maxOutputTokens", 800);
            String payload = mapper.writeValueAsString(body);

            // Try each model; retry transient (429/503) errors with a short backoff before
            // moving to the next model. Return the first good answer.
            int lastStatus = 0;
            for (String m : modelChain()) {
                for (int attempt = 0; attempt < 2; attempt++) {
                    HttpResponse<String> res = call(m, payload);
                    int status = res.statusCode();
                    lastStatus = status;
                    if (status / 100 == 2) {
                        JsonNode json = mapper.readTree(res.body());
                        JsonNode textNode = json.path("candidates").path(0).path("content")
                                .path("parts").path(0).path("text");
                        if (!textNode.isMissingNode() && !textNode.asText().isBlank()) {
                            return textNode.asText().trim();
                        }
                        String finish = json.path("candidates").path(0).path("finishReason").asText("");
                        return finish.isBlank() ? "I couldn't generate an answer for that." : "No answer (" + finish + ").";
                    }
                    log.warn("[ArCraft] Gemini {} HTTP {}: {}", m, status, res.body());
                    boolean transientErr = status == 429 || status == 503 || status >= 500;
                    if (!transientErr) break;             // e.g. 400/403 — next model won't help much, but try it
                    try { Thread.sleep(700L * (attempt + 1)); } catch (InterruptedException ignored) {}
                }
            }
            if (lastStatus == 429) {
                return "The AI is rate-limited right now (free tier). Please wait a few seconds and ask again.";
            }
            if (lastStatus == 503) {
                return "The AI model is briefly overloaded. Please try again in a moment.";
            }
            return "Sorry, the AI service is unavailable right now (HTTP " + lastStatus + "). Please try again.";
        } catch (Exception e) {
            log.error("[ArCraft] Gemini request failed", e);
            return "Sorry, I couldn't reach the AI service right now.";
        }
    }

    private HttpResponse<String> call(String model, String payload) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent?key="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
