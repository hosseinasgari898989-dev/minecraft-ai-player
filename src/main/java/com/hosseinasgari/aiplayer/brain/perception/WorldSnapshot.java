package com.hosseinasgari.aiplayer.brain.perception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded, platform-decoupled world-perception snapshot used by GoalManager and Skill Planners.
 * Never assumes stale world state: rebuilt on every perception tick before decisions execute.
 */
public record WorldSnapshot(
        long worldTick,
        String dimensionId,
        int x,
        int y,
        int z,
        float health,
        float maxHealth,
        boolean inLavaOrFire,
        boolean nightTime,
        int lightLevel,
        boolean ownerPresent,
        String ownerName,
        double ownerDistance,
        float ownerHealth,
        int nearbyHostileCount,
        String nearestHostileName,
        double nearestHostileDistance,
        int inventoryUsedSlots,
        int inventoryTotalSlots,
        Map<String, Integer> itemCounts,
        boolean hasWoodenPickaxeOrBetter,
        boolean hasStonePickaxeOrBetter,
        boolean hasCraftingTableNearby,
        List<String> nearbyPlayers
) {
    public WorldSnapshot {
        itemCounts = Collections.unmodifiableMap(new LinkedHashMap<>(itemCounts == null ? Map.of() : itemCounts));
        nearbyPlayers = List.copyOf(nearbyPlayers == null ? List.of() : nearbyPlayers);
    }

    public int countItem(String canonicalId) {
        return itemCounts.getOrDefault(canonicalId, 0);
    }

    public boolean inventoryFull() {
        return inventoryUsedSlots >= inventoryTotalSlots;
    }
}
