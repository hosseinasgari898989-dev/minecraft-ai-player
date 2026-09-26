package com.hosseinasgari.aiplayer.entity;

import java.util.Locale;

public final class RobotCommandPlanner {
    public record Plan(RobotMode mode, String description) {}

    private RobotCommandPlanner() {
    }

    public static Plan plan(String raw) {
        String input = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);

        if (input.isBlank()) {
            return new Plan(RobotMode.IDLE, "idle");
        }

        if (containsAny(input, "stop", "stand still", "wait", "توقف", "وایسا", "صبر")) {
            return new Plan(RobotMode.IDLE, "stopped");
        }

        if (containsAny(input, "follow", "follow me", "come with me", "دنبالم", "بیا")) {
            return new Plan(RobotMode.FOLLOW, "follow owner");
        }

        if (containsAny(input, "guard", "stay here", "protect this place", "نگهبان", "اینجا بمون", "اینجا بمان")) {
            return new Plan(RobotMode.GUARD, "guard home");
        }

        if (containsAny(input, "protect", "protect me", "defend me", "محافظت", "از من محافظت")) {
            return new Plan(RobotMode.PROTECT, "protect owner");
        }

        if (containsAny(input, "wood", "gather wood", "collect wood", "چوب", "هیزم")) {
            return new Plan(RobotMode.GATHER_WOOD, "gather wood");
        }

        if (containsAny(input, "house", "build a house", "build house", "خانه", "خونه", "ساختمان")) {
            return new Plan(RobotMode.BUILD_HOUSE, "build a house");
        }

        if (containsAny(input, "wander", "explore", "roam", "بگرد", "گشت")) {
            return new Plan(RobotMode.WANDER, "wander and explore");
        }

        return null;
    }

    private static boolean containsAny(String input, String... phrases) {
        for (String phrase : phrases) {
            if (input.contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
