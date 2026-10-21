package com.hosseinasgari.aiplayer.brain.planner;

import java.util.*;

public final class SkillRegistry {
    public record SkillDefinition(String id, String category, List<String> prerequisites, String completionCondition) {}
    private static final Map<String, SkillDefinition> SKILLS = new LinkedHashMap<>();
    static {
        register("FOLLOW_OWNER","navigation",List.of("owner_online"),"Within configured follow distance");
        register("COME_HERE","navigation",List.of("owner_online"),"Within 2.5 blocks of owner");
        register("STAY_POSITION","navigation",List.of(),"Hold assigned coordinates");
        register("GUARD_AREA","combat",List.of(),"Defend a bounded guard area");
        register("PROTECT_OWNER","combat",List.of("owner_online"),"Intercept nearby hostiles");
        register("HARVEST_WOOD","gathering",List.of("inventory_space"),"Required logs collected");
        register("MINE_STONE","gathering",List.of("inventory_space","wooden_pickaxe"),"Required stone collected");
        register("MINE_COAL","gathering",List.of("inventory_space","wooden_pickaxe"),"Required coal collected");
        register("MINE_IRON","gathering",List.of("inventory_space","stone_pickaxe"),"Required iron collected");
        register("CRAFT_PLANKS","crafting",List.of("wood_logs"),"Recipe output verified");
        register("CRAFT_STICKS","crafting",List.of("planks"),"Recipe output verified");
        register("CRAFT_PICKAXE","crafting",List.of("planks_or_cobblestone","sticks"),"Pickaxe crafted");
        register("CRAFT_SWORD_AXE","crafting",List.of("planks_or_cobblestone","sticks"),"Gear crafted");
        register("FIND_FOOD","survival",List.of("inventory_space"),"Food collected");
        register("EAT_FOOD","survival",List.of("edible_food"),"Health restored");
        register("ORGANIZE_INVENTORY","inventory",List.of(),"Stacks sorted");
        register("DEPOSIT_CHEST","storage",List.of("nearby_chest"),"Items deposited");
        register("PLANT_WHEAT","farming",List.of("farmland_or_dirt","wheat_seeds"),"Seeds planted");
        register("HARVEST_CROPS","farming",List.of("mature_crops"),"Crops harvested");
        register("SELECT_BUILD_SITE","construction",List.of("level_terrain"),"Build site selected");
        register("BUILD_BLUEPRINT","construction",List.of("blueprint_materials","valid_site"),"Blueprint verified");
        register("SCAN_THREATS","perception",List.of(),"Nearby threats reported");
        register("LOCATE_PLAYER","social",List.of("target_player_online"),"Player located");
        register("ENGAGE_PVP","combat",List.of("server_pvp_enabled","explicit_authorization"),"Authorized target engaged");
    }
    private SkillRegistry() {}
    private static void register(String id,String category,List<String> prereqs,String done) {
        SKILLS.put(id,new SkillDefinition(id,category,List.copyOf(prereqs),done));
    }
    public static Map<String,SkillDefinition> all() { return Collections.unmodifiableMap(SKILLS); }
    public static List<String> skillIds() { return List.copyOf(SKILLS.keySet()); }
}
