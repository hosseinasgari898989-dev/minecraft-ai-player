package com.hosseinasgari.aiplayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hosseinasgari.aiplayer.entity.RobotCommandPlanner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AiIntentClient {
    public record MemorySuggestion(String type, String content) {}

    public record LearnedCommandSuggestion(String phrase, String intent) {}

    public record TaskSuggestion(
            String title,
            String details,
            List<String> steps,
            int priority
    ) {}

    public record Decision(
            String reply,
            RobotCommandPlanner.Plan plan,
            List<MemorySuggestion> memories,
            List<LearnedCommandSuggestion> commands,
            List<TaskSuggestion> tasks
    ) {}

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
            String persistentMemoryContext
    ) throws Exception {
        AiConfig current = config;

        JsonArray messages = new JsonArray();

        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", buildSystemPrompt());
        messages.add(systemMessage);

        String combinedUserMessage = instruction
                + "\n\nCURRENT_ROBOT_CONTEXT:\n"
                + context
                + "\n\nAGENT_MEMORY:\n"
                + persistentMemoryContext;

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", combinedUserMessage);
        messages.add(userMessage);

        JsonObject body = new JsonObject();
        body.addProperty("model", current.model());
        body.add("messages", messages);
        body.addProperty("temperature", 0.2);
        body.addProperty("max_tokens", 900);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(normalizeEndpoint(current.baseUrl())))
                .timeout(Duration.ofSeconds(current.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        body.toString(),
                        StandardCharsets.UTF_8
                ));

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
        String cleaned = clean(content);

        String reply = "باشه، متوجه شدم.";
        List<MemorySuggestion> memories = new ArrayList<>();
        List<LearnedCommandSuggestion> commands = new ArrayList<>();
        List<TaskSuggestion> tasks = new ArrayList<>();
        RobotCommandPlanner.Plan plan = null;

        try {
            JsonObject json = JsonParser.parseString(cleaned).getAsJsonObject();

            if (json.has("reply")) {
                reply = json.get("reply").getAsString().trim();
            }

            if (json.has("action")) {
                String action = json.get("action").getAsString();
                plan = RobotCommandPlanner.planAction(action);
                if (plan != null && !current.isActionEnabled(
                        RobotCommandPlanner.normalizeAction(action)
                )) {
                    plan = null;
                }
            }

            parseMemories(json.getAsJsonArray("memories"), memories);
            parseCommands(json.getAsJsonArray("learned_commands"), commands);
            parseTasks(json.getAsJsonArray("tasks"), tasks);
        } catch (Exception ignored) {
            reply = cleaned.isBlank() ? reply : cleaned;
        }

        return Optional.of(new Decision(
                reply,
                plan,
                memories,
                commands,
                tasks
        ));
    }

    private static String normalizeEndpoint(String baseUrl) {
        String endpoint = baseUrl.replaceAll("/+$", "");
        if (!endpoint.endsWith("/chat/completions")) {
            endpoint += "/chat/completions";
        }
        return endpoint;
    }

    private static String buildSystemPrompt() {
        return "You are the conversational brain of a Minecraft companion.\n"
                + "Speak naturally in the player's language. Persian and English are supported.\n"
                + "Do not behave like a rigid command list. Understand the player's intent, casual phrasing, context, and multi-step requests.\n"
                + "Keep persistent memory useful: store durable facts, preferences, named places, important agreements, and learned command phrases.\n"
                + "Turn meaningful multi-step requests into a small task plan. A task is a plan description, not executable Java code.\n"
                + "Use the existing legacy action only when one of these actions is clearly appropriate: "
                + "IDLE, FOLLOW, WANDER, EXPLORE, GUARD, PROTECT, PATROL, RETURN_HOME, "
                + "GATHER_WOOD, GATHER_STONE, GATHER_COAL, BUILD_HOUSE, BUILD_TOWER.\n"
                + "For anything more complex, keep the natural-language reply and store the task plan instead of claiming that it is executed.\n"
                + "Only store information that is likely to remain useful later. Do not store secrets, API keys, passwords, or private credentials.\n"
                + "Output ONLY valid JSON with fields reply, action, memories, learned_commands, and tasks.\n"
                + "memories items use type and content. learned_commands items use phrase and intent. tasks items use title, details, steps, and priority.\n"
                + "Use empty arrays when there is nothing durable to remember or plan.";
    }

    private static String extractAssistantContent(String responseBody) {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");

        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("AI response has no choices");
        }

        JsonObject choice = choices.get(0).getAsJsonObject();
        JsonObject message = choice.getAsJsonObject("message");

        if (message == null || !message.has("content")) {
            throw new IllegalStateException("AI response has no message content");
        }

        return message.get("content").getAsString().trim();
    }

    private static void parseMemories(JsonArray values, List<MemorySuggestion> output) {
        if (values == null) return;
        for (JsonElement value : values) {
            if (!value.isJsonObject()) continue;
            JsonObject object = value.getAsJsonObject();
            String memory = stringValue(object, "content");
            if (memory.isBlank()) continue;
            output.add(new MemorySuggestion(stringValue(object, "type", "fact"), memory));
            if (output.size() >= 8) break;
        }
    }

    private static void parseCommands(JsonArray values, List<LearnedCommandSuggestion> output) {
        if (values == null) return;
        for (JsonElement value : values) {
            if (!value.isJsonObject()) continue;
            JsonObject object = value.getAsJsonObject();
            String phrase = stringValue(object, "phrase");
            String intent = stringValue(object, "intent");
            if (phrase.isBlank() || intent.isBlank()) continue;
            output.add(new LearnedCommandSuggestion(phrase, intent));
            if (output.size() >= 8) break;
        }
    }

    private static void parseTasks(JsonArray values, List<TaskSuggestion> output) {
        if (values == null) return;
        for (JsonElement value : values) {
            if (!value.isJsonObject()) continue;
            JsonObject object = value.getAsJsonObject();
            String title = stringValue(object, "title");
            if (title.isBlank()) continue;
            String details = stringValue(object, "details");
            List<String> steps = new ArrayList<>();
            JsonArray rawSteps = object.getAsJsonArray("steps");
            if (rawSteps != null) {
                for (JsonElement rawStep : rawSteps) {
                    if (!rawStep.isJsonPrimitive()) continue;
                    String step = rawStep.getAsString().trim();
                    if (!step.isBlank()) steps.add(step);
                    if (steps.size() >= 20) break;
                }
            }
            int priority = 0;
            if (object.has("priority")) {
                try { priority = object.get("priority").getAsInt(); } catch (Exception ignored) { }
            }
            output.add(new TaskSuggestion(title, details, steps, Math.max(0, Math.min(10, priority))));
            if (output.size() >= 8) break;
        }
    }

    private static String stringValue(JsonObject object, String key) {
        return stringValue(object, key, "");
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key)) return fallback;
        try { return object.get(key).getAsString().trim(); } catch (Exception ignored) { return fallback; }
    }

    private static String clean(String content) {
        String fence = String.valueOf((char) 96) + String.valueOf((char) 96) + String.valueOf((char) 96);
        return content.replace(fence + "json", "").replace(fence, "").trim();
    }
}