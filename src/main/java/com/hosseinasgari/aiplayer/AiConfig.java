package com.hosseinasgari.aiplayer;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class AiConfig {
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("ai-player.properties");

    private boolean enabled = true;
    private String baseUrl = "https://your-provider.example/v1";
    private String apiKey = "";
    private String model = "your-model";
    private String triggerPrefix = "ربات";
    private int timeoutSeconds = 30;

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
            config.triggerPrefix = properties.getProperty("trigger_prefix", config.triggerPrefix).trim();

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
        return enabled
                && !apiKey.isBlank()
                && !baseUrl.isBlank()
                && !model.isBlank()
                && !baseUrl.contains("your-provider.example")
                && !model.equalsIgnoreCase("your-model");
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

    public static Path getConfigPath() {
        return CONFIG_PATH;
    }
}
