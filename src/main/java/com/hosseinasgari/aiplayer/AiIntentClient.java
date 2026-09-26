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
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class AiIntentClient {
    public record ChatTurn(String role, String content) {}
    public record Decision(String reply, RobotCommandPlanner.Plan plan) {}

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

    public Optional<Decision> chat(
            String instruction,
            String context,
            List<ChatTurn> memory
    ) throws Exception {
        AiConfig current = config;

        JsonArray messages = new JsonArray();

        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", buildSystemPrompt(current));
        messages.add(systemMessage);

        if (memory != null) {
            int start = Math.max(0, memory.size() - current.memoryMessages());
            for (int i = start; i < memory.size(); i++) {
                ChatTurn turn = memory.get(i);
                JsonObject memoryMessage = new JsonObject();
                memoryMessage.addProperty("role", turn.role());
                memoryMessage.addProperty("content", turn.content());
                messages.add(memoryMessage);
            }
        }

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty(
                "content",
                instruction + "\n\nCURRENT_ROBOT_CONTEXT:\n" + context
        );
        messages.add(userMessage);

        JsonObject body = new JsonObject();
        body.addProperty("model", current.model());
        body.add("messages", messages);
        body.addProperty("temperature", 0.2);
        body.addProperty("max_tokens", 160);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(normalizeEndpoint(current.baseUrl())))
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
        String reply = extractReply(content);
        String action = extractAction(content);

        RobotCommandPlanner.Plan plan = RobotCommandPlanner.planAction(action);
        if (plan != null && !current.isActionEnabled(action)) {
            plan = null;
        }

        return Optional.of(new Decision(
                reply.isBlank() ? defaultReply(plan) : reply,
                plan
        ));
    }

    private static String normalizeEndpoint(String baseUrl) {
        String endpoint = baseUrl.replaceAll("/+$", "");
        if (!endpoint.endsWith("/chat/completions")) {
            endpoint += "/chat/completions";
        }
        return endpoint;
    }

    private static String buildSystemPrompt(AiConfig config) {
        StringBuilder allowed = new StringBuilder();
        String[] actions = {
                "IDLE", "FOLLOW", "WANDER", "EXPLORE", "GUARD", "PROTECT",
                "PATROL", "RETURN_HOME", "GATHER_WOOD", "GATHER_STONE",
                "GATHER_COAL", "BUILD_HOUSE", "BUILD_TOWER"
        };

        for (String action : actions) {
            if (config.isActionEnabled(action)) {
                if (allowed.length() > 0) {
                    allowed.append(", ");
                }
                allowed.append(action);
            }
        }

        return "You are the brain and companion of a Minecraft robot.\n"
                + "Speak naturally and helpfully in the player language.\n"
                + "Understand casual Persian and English.\n"
                + "You have short conversational memory.\n"
                + "Never invent Minecraft commands or execute arbitrary code.\n"
                + "Select at most ONE action from the allowed actions.\n"
                + "If the player is only talking, use IDLE.\n"
                + "If unsupported, explain that it is not implemented yet.\n"
                + "Allowed actions: " + allowed + "\n"
                + "Return ONLY valid JSON: {\"reply\":\"brief response\",\"action\":\"ACTION_NAME\"}";
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

    private static String extractReply(String content) {
        String cleaned = clean(content);
        try {
            var json = JsonParser.parseString(cleaned).getAsJsonObject();
            if (json.has("reply")) {
                return json.get("reply").getAsString().trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String extractAction(String content) {
        String cleaned = clean(content);
        try {
            var json = JsonParser.parseString(cleaned).getAsJsonObject();
            if (json.has("action")) {
                return json.get("action").getAsString();
            }
        } catch (Exception ignored) {
        }
        return cleaned.replaceAll("[^A-Za-z0-9_]+", " ").trim().toUpperCase(Locale.ROOT);
    }

    private static String clean(String content) {
        return content.replace("```json", "").replace("```", "").trim();
    }

    private static String defaultReply(RobotCommandPlanner.Plan plan) {
        return plan == null ? "باشه، متوجه شدم." : "باشه، انجامش می‌دم.";
    }
}
