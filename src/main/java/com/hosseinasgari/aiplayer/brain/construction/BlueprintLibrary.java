package com.hosseinasgari.aiplayer.brain.construction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlueprintLibrary {
    private static final Map<String, StructureBlueprint> BLUEPRINTS = new LinkedHashMap<>();
    static {
        register(buildHouseVariant("shelter", "Simple Survival Shelter", 5, 4, 5, "oak_planks", "oak_log", false));
        register(buildHouseVariant("compact_wood", "Compact Wooden House", 7, 5, 7, "oak_planks", "oak_log", true));
        register(buildHouseVariant("modern_house", "Modern Stylish House", 7, 5, 7, "cobblestone", "oak_planks", true));
        register(buildHouseVariant("decorative_house", "Decorative Timber House", 9, 6, 9, "oak_planks", "cobblestone", true));
        register(buildSmallFarm()); register(buildWatchtower());
    }
    private BlueprintLibrary() {}
    private static void register(StructureBlueprint bp) { BLUEPRINTS.put(bp.id(), bp); }
    public static StructureBlueprint get(String styleId) { return styleId == null ? BLUEPRINTS.get("compact_wood") : BLUEPRINTS.getOrDefault(styleId, BLUEPRINTS.get("compact_wood")); }
    public static Map<String, StructureBlueprint> all() { return BLUEPRINTS; }

    private static StructureBlueprint buildHouseVariant(String id, String name, int width, int height, int depth, String primary, String frame, boolean windows) {
        List<StructureBlueprint.BlockEntry> list = new ArrayList<>();
        int hw = width / 2, hd = depth / 2, roofY = height - 1;
        for (int dz=-hd; dz<=hd; dz++) for (int dx=-hw; dx<=hw; dx++) {
            boolean border = Math.abs(dx)==hw || Math.abs(dz)==hd;
            list.add(new StructureBlueprint.BlockEntry(dx,0,dz,border ? frame : primary,false));
        }
        for (int dy=1; dy<roofY; dy++) for (int dz=-hd; dz<=hd; dz++) for (int dx=-hw; dx<=hw; dx++) {
            boolean border = Math.abs(dx)==hw || Math.abs(dz)==hd;
            if (!border) continue;
            if (dz==-hd && dx==0 && (dy==1 || dy==2)) { list.add(new StructureBlueprint.BlockEntry(dx,dy,dz,"air",false)); continue; }
            if (Math.abs(dx)==hw && Math.abs(dz)==hd) { list.add(new StructureBlueprint.BlockEntry(dx,dy,dz,frame,false)); continue; }
            if (windows && dy==2 && (dx==0 || dz==0)) { list.add(new StructureBlueprint.BlockEntry(dx,dy,dz,"glass_pane",true)); continue; }
            list.add(new StructureBlueprint.BlockEntry(dx,dy,dz,primary,false));
        }
        list.add(new StructureBlueprint.BlockEntry(0,1,hd-1,"torch",true));
        for (int dz=-hd; dz<=hd; dz++) for (int dx=-hw; dx<=hw; dx++) {
            boolean border = Math.abs(dx)==hw || Math.abs(dz)==hd;
            list.add(new StructureBlueprint.BlockEntry(dx,roofY,dz,border ? frame : primary,false));
        }
        list.sort(Comparator.comparingInt(StructureBlueprint.BlockEntry::dy));
        return new StructureBlueprint(id,name,width,height,depth,list);
    }
    private static StructureBlueprint buildSmallFarm() {
        List<StructureBlueprint.BlockEntry> list = new ArrayList<>();
        for (int dz=-2; dz<=2; dz++) for (int dx=-2; dx<=2; dx++) {
            boolean border = Math.abs(dx)==2 || Math.abs(dz)==2;
            if (border) list.add(new StructureBlueprint.BlockEntry(dx,0,dz,"oak_planks",false));
            else if (dx==0 && dz==0) list.add(new StructureBlueprint.BlockEntry(0,0,0,"water",true));
            else { list.add(new StructureBlueprint.BlockEntry(dx,0,dz,"farmland",false)); list.add(new StructureBlueprint.BlockEntry(dx,1,dz,"wheat_crop",true)); }
        }
        list.add(new StructureBlueprint.BlockEntry(2,1,2,"torch",true));
        list.sort(Comparator.comparingInt(StructureBlueprint.BlockEntry::dy));
        return new StructureBlueprint("small_farm","Irrigated Wheat Farm",5,2,5,list);
    }
    private static StructureBlueprint buildWatchtower() {
        List<StructureBlueprint.BlockEntry> list = new ArrayList<>();
        for (int dz=-1; dz<=1; dz++) for (int dx=-1; dx<=1; dx++) list.add(new StructureBlueprint.BlockEntry(dx,0,dz,"cobblestone",false));
        for (int dy=1; dy<=5; dy++) for (int dz=-1; dz<=1; dz++) for (int dx=-1; dx<=1; dx++) {
            if (Math.abs(dx)!=1 && Math.abs(dz)!=1) continue;
            if (dz==-1 && dx==0 && (dy==1 || dy==2)) list.add(new StructureBlueprint.BlockEntry(dx,dy,dz,"air",false));
            else list.add(new StructureBlueprint.BlockEntry(dx,dy,dz,"cobblestone",false));
        }
        for (int dz=-2; dz<=2; dz++) for (int dx=-2; dx<=2; dx++) list.add(new StructureBlueprint.BlockEntry(dx,6,dz,"oak_planks",false));
        list.add(new StructureBlueprint.BlockEntry(0,7,0,"torch",true));
        list.sort(Comparator.comparingInt(StructureBlueprint.BlockEntry::dy));
        return new StructureBlueprint("watchtower","Guard Watchtower",5,8,5,list);
    }
}
