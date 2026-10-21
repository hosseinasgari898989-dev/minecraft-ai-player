package com.hosseinasgari.aiplayer.brain.construction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StructureBlueprint {
    public record BlockEntry(int dx, int dy, int dz, String blockId, boolean optional) {}
    private final String id;
    private final String displayName;
    private final int width;
    private final int height;
    private final int depth;
    private final List<BlockEntry> blocks;
    private final Map<String, Integer> requiredMaterials;

    public StructureBlueprint(String id, String displayName, int width, int height, int depth, List<BlockEntry> blocks) {
        this.id = id; this.displayName = displayName; this.width = width; this.height = height; this.depth = depth;
        this.blocks = List.copyOf(blocks);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BlockEntry entry : blocks) if (!entry.optional() && !entry.blockId().equals("air")) counts.merge(entry.blockId(), 1, Integer::sum);
        this.requiredMaterials = Collections.unmodifiableMap(counts);
    }
    public String id() { return id; }
    public String displayName() { return displayName; }
    public int width() { return width; }
    public int height() { return height; }
    public int depth() { return depth; }
    public List<BlockEntry> blocks() { return blocks; }
    public Map<String, Integer> requiredMaterials() { return requiredMaterials; }
}
