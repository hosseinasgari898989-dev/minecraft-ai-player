package com.hosseinasgari.aiplayer;

import com.hosseinasgari.aiplayer.entity.RobotCommandPlanner;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class AgentSkillRegistry {
    private static final Map<String, String> SKILLS = new LinkedHashMap<>();

    static {
        register("stop", "Stop and wait.");
        register("follow_player", "Follow the owner.");
        register("wander", "Wander nearby.");
        register("explore", "Explore the surrounding area.");
        register("guard_home", "Guard the home area.");
        register("protect_player", "Protect the owner.");
        register("patrol_home", "Patrol around home.");
        register("go_home", "Return to the saved home.");
        register("gather_wood", "Gather nearby wood.");
        register("gather_stone", "Gather nearby stone.");
        register("gather_coal", "Gather nearby coal.");
        register("build_house", "Build the built-in house.");
        register("build_tower", "Build the built-in tower.");
    }

    private AgentSkillRegistry() {
    }

    private static void register(String id, String description) {
        SKILLS.put(id, description);
    }

    public static Optional<RobotCommandPlanner.Plan> resolve(String rawSkill) {
        String skill = normalize(rawSkill);
        if (!SKILLS.containsKey(skill)) {
            return Optional.empty();
        }

        String action = switch (skill) {
            case "stop" -> "IDLE";
            case "follow_player" -> "FOLLOW";
            case "wander" -> "WANDER";
            case "explore" -> "EXPLORE";
            case "guard_home" -> "GUARD";
            case "protect_player" -> "PROTECT";
            case "patrol_home" -> "PATROL";
            case "go_home" -> "RETURN_HOME";
            case "gather_wood" -> "GATHER_WOOD";
            case "gather_stone" -> "GATHER_STONE";
            case "gather_coal" -> "GATHER_COAL";
            case "build_house" -> "BUILD_HOUSE";
            case "build_tower" -> "BUILD_TOWER";
            default -> "";
        };

        return Optional.ofNullable(RobotCommandPlanner.planAction(action));
    }

    public static String catalog() {
        return String.join(", ", SKILLS.keySet());
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_');
    }
}
