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

/**
 * Local deterministic configuration for AI Player.
 * Requires zero API keys, zero hosted models, and zero internet connectivity.
 */
public final class AiConfig {
    private boolean enabled = true;
    private String triggerPrefix = "ربات";
    private boolean chatEnabled = true;
    private boolean requirePrefix = false;
    private boolean autoSpawn = true;
    private boolean singleCompanion = true;
    private int scanRadius = 18;
    private double followDistance = 4.0;
    private double guardRadius = 16.0;
    private boolean protectPlayerStructures = true;
    private boolean allowEmergencyTeleport = false;
    private String disabledActions = "";

    private AiConfig() {}

    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("ai-player.properties");
    }

    public static AiConfig defaults() { return new AiConfig(); }

    public static AiConfig load() {
        AiConfig config = new AiConfig();
        Path path;
        try { path = getConfigPath(); } catch (Throwable ignored) { return config; }

        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) { config.save(); return config; }
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(path)) { properties.load(input); }
            config.enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
            config.triggerPrefix = properties.getProperty("trigger_prefix", config.triggerPrefix).trim();
            config.chatEnabled = Boolean.parseBoolean(properties.getProperty("chat_enabled", "true"));
            config.requirePrefix = Boolean.parseBoolean(properties.getProperty("require_prefix", "false"));
            config.autoSpawn = Boolean.parseBoolean(properties.getProperty("auto_spawn", "true"));
            config.singleCompanion = Boolean.parseBoolean(properties.getProperty("single_companion", "true"));
            config.protectPlayerStructures = Boolean.parseBoolean(properties.getProperty("protect_player_structures", "true"));
            config.allowEmergencyTeleport = Boolean.parseBoolean(properties.getProperty("allow_emergency_teleport", "false"));
            config.disabledActions = properties.getProperty("disabled_actions", "").trim();
            config.scanRadius = clampInt(properties.getProperty("scan_radius"), 18, 8, 32);
            config.followDistance = clampDouble(properties.getProperty("follow_distance"), 4.0, 2.0, 24.0);
            config.guardRadius = clampDouble(properties.getProperty("guard_radius"), 16.0, 4.0, 48.0);
        } catch (IOException ignored) {
            // Safe local in-memory defaults.
        }
        return config;
    }

    public synchronized void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("brain_mode", "offline-deterministic-v2");
        properties.setProperty("trigger_prefix", triggerPrefix);
        properties.setProperty("chat_enabled", Boolean.toString(chatEnabled));
        properties.setProperty("require_prefix", Boolean.toString(requirePrefix));
        properties.setProperty("auto_spawn", Boolean.toString(autoSpawn));
        properties.setProperty("single_companion", Boolean.toString(singleCompanion));
        properties.setProperty("scan_radius", Integer.toString(scanRadius));
        properties.setProperty("follow_distance", Double.toString(followDistance));
        properties.setProperty("guard_radius", Double.toString(guardRadius));
        properties.setProperty("protect_player_structures", Boolean.toString(protectPlayerStructures));
        properties.setProperty("allow_emergency_teleport", Boolean.toString(allowEmergencyTeleport));
        properties.setProperty("disabled_actions", disabledActions);
        try {
            Path path = getConfigPath();
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "AI Player local deterministic brain settings (100% offline, no external LLM required).");
            }
        } catch (Throwable ignored) {}
    }

    public boolean isEnabled() { return enabled; }
    public boolean isConfigured() { return enabled; }
    public String model() { return "local-deterministic-brain-v2 (offline)"; }
    public String triggerPrefix() { return triggerPrefix.isBlank() ? "ربات" : triggerPrefix; }
    public boolean chatEnabled() { return chatEnabled; }
    public boolean requirePrefix() { return requirePrefix; }
    public boolean autoSpawn() { return autoSpawn; }
    public boolean singleCompanion() { return singleCompanion; }
    public int scanRadius() { return scanRadius; }
    public double followDistance() { return followDistance; }
    public double guardRadius() { return guardRadius; }
    public boolean protectPlayerStructures() { return protectPlayerStructures; }
    public boolean allowEmergencyTeleport() { return allowEmergencyTeleport; }
    public void setAllowEmergencyTeleport(boolean value) { this.allowEmergencyTeleport = value; }
    public String disabledActionsRaw() { return disabledActions.isBlank() ? "none" : disabledActions; }

    public Set<String> disabledActions() {
        if (disabledActions.isBlank()) return Set.of();
        Set<String> result = new HashSet<>();
        for (String raw : disabledActions.split(",")) {
            String action = raw.trim().toUpperCase(Locale.ROOT);
            if (!action.isBlank()) result.add(action);
        }
        return result;
    }

    public boolean isActionEnabled(String action) {
        return action != null && !action.isBlank() && !disabledActions().contains(action.trim().toUpperCase(Locale.ROOT));
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

    private static int clampInt(String value, int fallback, int min, int max) {
        if (value == null) return fallback;
        try { return Math.max(min, Math.min(max, Integer.parseInt(value.trim()))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static double clampDouble(String value, double fallback, double min, double max) {
        if (value == null) return fallback;
        try { return Math.max(min, Math.min(max, Double.parseDouble(value.trim()))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
