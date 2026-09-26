package com.hosseinasgari.aiplayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hosseinasgari.aiplayer.entity.RobotCommandPlanner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

public final class AiIntentClient {
    private static final String SYSTEM_PROMPT =
            "You are the command planner for a Minecraft companion robot.\n"
                    + "Convert the player natural-language request into exactly ONE allowed action.\n"
                    + "Do not execute code, Minecraft commands, coordinates, or arbitrary actions.\n\n"
                    + "Allowed actions:\n"
                    + "IDLE\nFOLLOW\nWANDER\nEXPLORE\nGUARD\nPROTECT\nPATROL\n"
                    + "RETURN_HOME\nGATHER_WOOD\nGATHER_STONE\nGATHER_COAL\nBUILD_HOUSE\nBUILD_TOWER\n\n"
                    + "Return ONLY JSON in this exact shape: {\"action\":\"ACTION_NAME\"}\n\n"
                    + "Examples:\n"
                    + "برو چوب جمع کن -> {\"action\":\"GATHER_WOOD\"}\n"
                    + "یه خونه بساز -> {\"action\":\"BUILD_HOUSE\"}\n"
                    + "دنبالم بیا -> {\"action\":\"FOLLOW\"}\n"
                    + "برگرد خونه -> {\"action\":\"RETURN_HOME\"}";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private volatile AiConfig config;

    public AiIntentClient(AiConfig config) {
        this.config = config;
    }

    public void reload(AiConfig config) {
        this.config = config;
    }

    public boolean isConfigured() {
        return config.isConfigured();
    }

    public String model() {
        return config.model();
    }

    public Optional<RobotCommandPlanner.Plan> classify(String instruction) throws Exception {
        AiConfig current = config;

        String endpoint = current.baseUrl().replaceAll("/+$", "");
        if (!endpoint.endsWith("/chat/completions")) {
            endpoint += "/chat/completions";
        }

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", instruction);

        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", SYSTEM_PROMPT);

        JsonArray messages = new JsonArray();
        messages.add(systemMessage);
        messages.add(userMessage);

        JsonObject body = new JsonObject();
        body.addProperty("model", current.model());
        body.add("messages", messages);
        body.addProperty("temperature", 0);
        body.addProperty("max_tokens", 40);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(current.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));

        if (!current.apiKey().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + current.apiKey());
        }

        HttpResponse<String> response = httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("AI HTTP " + response.statusCode());
        }

        String content = extractAssistantContent(response.body());
        return Optional.ofNullable(RobotCommandPlanner.planAction(extractAction(content)));
    }

    private static String extractAssistantContent(String responseBody) {
        var root = JsonParser.parseString(responseBody).getAsJsonObject();
        var choices = root.getAsJsonArray("choices");

        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("AI response has no choices");
        }

        var choice = choices.get(0).getAsJsonObject();
        var message = choice.getAsJsonObject("message");

        if (message == null || !message.has("content")) {
            throw new IllegalStateException("AI response has no message content");
        }

        return message.get("content").getAsString().trim();
    }

    private static String extractAction(String content) {
        String cleaned = content.replace("```json", "").replace("```", "").trim();

        try {
            var json = JsonParser.parseString(cleaned).getAsJsonObject();
            if (json.has("action")) {
                return json.get("action").getAsString();
            }
        } catch (Exception ignored) {
        }

        return cleaned
                .replaceAll("[^A-Za-z0-9_]+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}
