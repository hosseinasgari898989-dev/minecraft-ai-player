package com.hosseinasgari.aiplayer.brain.memory;

import com.hosseinasgari.aiplayer.brain.model.OperatingMode;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class CompanionMemory {
    private static final int MIN_WORLD_Y = -64, MAX_WORLD_Y = 320, MAX_WORLD_XZ = 30_000_000;
    public record SavedPos(int x, int y, int z, String dimension) {
        public boolean isValid() {
            return y >= MIN_WORLD_Y && y <= MAX_WORLD_Y && Math.abs(x) <= MAX_WORLD_XZ && Math.abs(z) <= MAX_WORLD_XZ && dimension != null && !dimension.isBlank();
        }
    }
    private UUID ownerUuid;
    private String ownerName = "";
    private String preferredLanguage = "en";
    private OperatingMode operatingMode = OperatingMode.COMPANION;
    private SavedPos homePos, guardPos, activeBuildOrigin;
    private String activeBlueprintId = "";
    private int activeBuildIndex = 0;
    private double followDistance = 4.0, guardRadius = 16.0;
    private boolean protectPlayerStructures = true, allowEmergencyTeleport = false;
    private String lastSavedTaskSummary = "";
    private final Set<String> authorizedPvpPlayers = new LinkedHashSet<>();

    public UUID ownerUuid() { return ownerUuid; }
    public void setOwner(UUID uuid, String name) { ownerUuid = uuid; ownerName = name == null ? "" : name; }
    public String ownerName() { return ownerName; }
    public String preferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String lang) { if ("fa".equalsIgnoreCase(lang) || "en".equalsIgnoreCase(lang)) preferredLanguage = lang.toLowerCase(Locale.ROOT); }
    public OperatingMode operatingMode() { return operatingMode; }
    public void setOperatingMode(OperatingMode mode) { if (mode != null) operatingMode = mode; }
    public SavedPos homePos() { return homePos; }
    public void setHomePos(SavedPos pos) { homePos = pos != null && pos.isValid() ? pos : null; }
    public SavedPos guardPos() { return guardPos; }
    public void setGuardPos(SavedPos pos) { guardPos = pos != null && pos.isValid() ? pos : null; }
    public SavedPos activeBuildOrigin() { return activeBuildOrigin; }
    public void setActiveBuildProgress(String id, SavedPos origin, int index) {
        if (id == null || id.isBlank() || origin == null || !origin.isValid()) { clearActiveBuildProgress(); return; }
        activeBlueprintId = id; activeBuildOrigin = origin; activeBuildIndex = Math.max(0, index);
    }
    public void clearActiveBuildProgress() { activeBlueprintId = ""; activeBuildOrigin = null; activeBuildIndex = 0; }
    public String activeBlueprintId() { return activeBlueprintId; }
    public int activeBuildIndex() { return activeBuildIndex; }
    public double followDistance() { return followDistance; }
    public void setFollowDistance(double distance) { if (Double.isFinite(distance)) followDistance = Math.max(2.0, Math.min(24.0, distance)); }
    public double guardRadius() { return guardRadius; }
    public void setGuardRadius(double radius) { if (Double.isFinite(radius)) guardRadius = Math.max(4.0, Math.min(48.0, radius)); }
    public boolean protectPlayerStructures() { return protectPlayerStructures; }
    public void setProtectPlayerStructures(boolean protect) { protectPlayerStructures = protect; }
    public boolean allowEmergencyTeleport() { return allowEmergencyTeleport; }
    public void setAllowEmergencyTeleport(boolean allow) { allowEmergencyTeleport = allow; }
    public String lastSavedTaskSummary() { return lastSavedTaskSummary; }
    public void setLastSavedTaskSummary(String summary) { lastSavedTaskSummary = summary == null ? "" : summary; }
    public Set<String> authorizedPvpPlayers() { return Collections.unmodifiableSet(authorizedPvpPlayers); }
    public void authorizePvpTarget(String name) { if (name != null && !name.isBlank()) authorizedPvpPlayers.add(name.trim().toLowerCase(Locale.ROOT)); }
    public void clearPvpAuthorizations() { authorizedPvpPlayers.clear(); }
    public boolean isPvpTargetAuthorized(String name) { return name != null && authorizedPvpPlayers.contains(name.trim().toLowerCase(Locale.ROOT)); }
    public void validateAfterReload(String activeDimensionId) {
        if (homePos != null && !homePos.isValid()) homePos = null;
        if (guardPos != null && (!guardPos.isValid() || !guardPos.dimension().equals(activeDimensionId))) guardPos = null;
        if (activeBuildOrigin != null && (!activeBuildOrigin.isValid() || !activeBuildOrigin.dimension().equals(activeDimensionId))) clearActiveBuildProgress();
        authorizedPvpPlayers.clear();
        if (operatingMode == null) operatingMode = OperatingMode.COMPANION;
    }
}
