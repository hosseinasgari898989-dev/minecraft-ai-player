package com.hosseinasgari.aiplayer;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public final class AiConfig {
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("ai-player.properties");

    private boolean enabled = true;
    private String baseUrl = "https://your-provider.example/v1";
    private String apiKey = "";
    private String model = "kimi-k2.5";
    private String triggerPrefix = "ربات";
    private int timeoutSeconds = 30;
    private boolean chatEnabled = true;
    private boolean requirePrefix = false;
    private boolean autoSpawn = true;
    private boolean singleCompanion = true;
    private int memoryMessages = 12;
    private String disabledActions = "";

    private AiConfig() {
    }

    public static AiConfig load() {
        AiConfig config = new AiConfig();

        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            if (Files.notExists(CONFIG_PATH)) {
                config.save();
                return config;
            }

            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
                properties.load(input);
            }

            config.enabled = Boolean.parseBoolean(
                    properties.getProperty("enabled", Boolean.toString(config.enabled))
            );
            config.baseUrl = properties.getProperty("base_url", config.baseUrl).trim();
            config.apiKey = properties.getProperty("api_key", "").trim();
            config.model = properties.getProperty("model", config.model).trim();
            if (config.model.equalsIgnoreCase("your-model")) {
                config.model = "kimi-k2.5";
            }
            config.triggerPrefix = properties.getProperty("trigger_prefix", config.triggerPrefix).trim();
            config.chatEnabled = Boolean.parseBoolean(
                    properties.getProperty("chat_enabled", Boolean.toString(config.chatEnabled))
            );
            config.requirePrefix = Boolean.parseBoolean(
                    properties.getProperty("require_prefix", Boolean.toString(config.requirePrefix))
            );
            config.autoSpawn = Boolean.parseBoolean(
                    properties.getProperty("auto_spawn", Boolean.toString(config.autoSpawn))
            );
            config.singleCompanion = Boolean.parseBoolean(
                    properties.getProperty("single_companion", Boolean.toString(config.singleCompanion))
            );
            config.disabledActions = properties.getProperty("disabled_actions", "").trim();

            try {
                config.memoryMessages = Math.max(
                        0,
                        Math.min(30, Integer.parseInt(
                                properties.getProperty(
                                        "memory_messages",
                                        Integer.toString(config.memoryMessages)
                                )
                        ))
                );
            } catch (NumberFormatException ignored) {
                config.memoryMessages = 12;
            }

            try {
                config.timeoutSeconds = Math.max(
                        5,
                        Math.min(120, Integer.parseInt(
                                properties.getProperty(
                                        "timeout_seconds",
                                        Integer.toString(config.timeoutSeconds)
                                )
                        ))
                );
            } catch (NumberFormatException ignored) {
                config.timeoutSeconds = 30;
            }
        } catch (IOException ignored) {
            // Keep safe in-memory defaults. The mod can still run without external AI.
        }

        return config;
    }

    public synchronized void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("base_url", baseUrl);
        properties.setProperty("api_key", apiKey);
        properties.setProperty("model", model);
        properties.setProperty("trigger_prefix", triggerPrefix);
        properties.setProperty("timeout_seconds", Integer.toString(timeoutSeconds));
        properties.setProperty("chat_enabled", Boolean.toString(chatEnabled));
        properties.setProperty("require_prefix", Boolean.toString(requirePrefix));
        properties.setProperty("auto_spawn", Boolean.toString(autoSpawn));
        properties.setProperty("single_companion", Boolean.toString(singleCompanion));
        properties.setProperty("memory_messages", Integer.toString(memoryMessages));
        properties.setProperty("disabled_actions", disabledActions);

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
                properties.store(
                        output,
                        "AI Player external AI settings. Keep api_key private and never commit this file."
                );
            }
        } catch (IOException ignored) {
            // Configuration is optional; keep runtime values in memory.
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isConfigured() {
        if (!enabled
                || baseUrl.isBlank()
                || model.isBlank()
                || baseUrl.contains("your-provider.example")
                || model.equalsIgnoreCase("your-model")) {
            return false;
        }

        // Local llama.cpp/Ollama-style servers do not need an API key.
        boolean local = baseUrl.startsWith("http://127.0.0.1:")
                || baseUrl.startsWith("http://localhost:")
                || baseUrl.startsWith("http://0.0.0.0:");

        return local || !apiKey.isBlank();
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String apiKey() {
        return apiKey;
    }

    public String model() {
        return model;
    }

    public String triggerPrefix() {
        return triggerPrefix.isBlank() ? "ربات" : triggerPrefix;
    }

    public int timeoutSeconds() {
        return timeoutSeconds;
    }

    public boolean chatEnabled() {
        return chatEnabled;
    }

    public boolean requirePrefix() {
        return requirePrefix;
    }

    public boolean autoSpawn() {
        return autoSpawn;
    }

    public boolean singleCompanion() {
        return singleCompanion;
    }

    public int memoryMessages() {
        return memoryMessages;
    }

    public Set<String> disabledActions() {
        if (disabledActions.isBlank()) {
            return Set.of();
        }

        Set<String> result = new HashSet<>();
        for (String raw : disabledActions.split(",")) {
            String action = raw.trim().toUpperCase(Locale.ROOT);
            if (!action.isBlank()) {
                result.add(action);
            }
        }
        return result;
    }

    public boolean isActionEnabled(String action) {
        if (action == null || action.isBlank()) {
            return false;
        }
        return !disabledActions().contains(action.trim().toUpperCase(Locale.ROOT));
    }

    public void disableAction(String action) {
        Set<String> actions = new HashSet<>(disabledActions());
        actions.add(action.trim().toUpperCase(Locale.ROOT));
        disabledActions = actions.stream().sorted().collect(Collectors.joining(","));
    }

    public void enableAction(String action) {
        Set<String> actions = new HashSet<>(disabledActions());
        actions.remove(action.trim().toUpperCase(Locale.ROOT));
        disabledActions = actions.stream().sorted().collect(Collectors.joining(","));
    }

    public String disabledActionsRaw() {
        return disabledActions;
    }

    public static Path getConfigPath() {
        return CONFIG_PATH;
    }
}
