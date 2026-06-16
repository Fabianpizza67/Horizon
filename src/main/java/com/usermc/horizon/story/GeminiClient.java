package com.usermc.horizon.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal async wrapper around the Gemini "generateContent" REST API.
 *
 * Uses java.net.http.HttpClient (built into the JDK since 11 — no extra
 * dependency needed) with sendAsync, so calls never block the calling thread.
 *
 * Requests ask for responseMimeType=application/json so the model's reply
 * is itself a JSON string we can parse directly with Gson.
 *
 * All failures (network, non-200, malformed response) surface as an
 * exceptionally-completed future — callers (StoryManager) fall back to
 * pre-written generic text rather than ever blocking or crashing gameplay.
 */
public class GeminiClient {

    private static final String ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final String apiKey;
    private final String model;
    private final HttpClient http;

    public GeminiClient(String apiKey, String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model  = (model == null || model.isBlank()) ? "gemini-2.0-flash" : model.trim();
        this.http   = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /**
     * Sends a request with a system instruction + user prompt, requesting a
     * JSON-formatted response.
     *
     * @return a future resolving to the raw text of the model's reply
     *         (expected to itself be a JSON object as a string).
     */
    public CompletableFuture<String> generateJson(String systemInstruction, String userPrompt) {
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Gemini API key is not configured"));
        }

        JsonObject body = new JsonObject();

        JsonObject sysInstruction = new JsonObject();
        JsonArray  sysParts = new JsonArray();
        JsonObject sysPart  = new JsonObject();
        sysPart.addProperty("text", systemInstruction);
        sysParts.add(sysPart);
        sysInstruction.add("parts", sysParts);
        body.add("systemInstruction", sysInstruction);

        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", userPrompt);
        parts.add(part);
        content.add("parts", parts);
        contents.add(content);
        body.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("responseMimeType", "application/json");
        generationConfig.addProperty("temperature", 1.0);
        generationConfig.addProperty("maxOutputTokens", 1024);
        body.add("generationConfig", generationConfig);

        String url = String.format(ENDPOINT_TEMPLATE, model, apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::extractText);
    }

    private String extractText(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API returned HTTP " + response.statusCode()
                    + ": " + truncate(response.body(), 300));
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray candidates = root.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("Gemini returned no candidates: " + truncate(response.body(), 300));
        }

        JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
        JsonArray  parts    = content.getAsJsonArray("parts");
        if (parts == null || parts.isEmpty()) {
            throw new RuntimeException("Gemini candidate had no content parts");
        }

        return parts.get(0).getAsJsonObject().get("text").getAsString();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}