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

        if (containsAny(input, "stop", "stand still", "wait", "توقف", "وایسا", "صبر", "بیخیال", "بس کن")) {
            return new Plan(RobotMode.IDLE, "stopped");
        }

        if (containsAny(input, "follow", "follow me", "come with me", "دنبالم", "دنبال من", "بیا")) {
            return new Plan(RobotMode.FOLLOW, "follow owner");
        }

        if (containsAny(input, "protect", "protect me", "defend me", "bodyguard", "محافظت", "از من محافظت", "محافظم باش")) {
            return new Plan(RobotMode.PROTECT, "protect owner");
        }

        if (containsAny(input, "guard", "stay here", "protect this place", "نگهبان", "نگهبانی", "اینجا بمون", "اینجا بمان")) {
            return new Plan(RobotMode.GUARD, "guard home");
        }

        if (containsAny(input, "patrol", "patrol around", "گشت بزن", "گشت بده", "دور بزن")) {
            return new Plan(RobotMode.PATROL, "patrol around home");
        }

        if (containsAny(input, "return home", "go home", "come home", "برگرد خونه", "برگرد خانه", "برو خونه", "برو خانه")) {
            return new Plan(RobotMode.RETURN_HOME, "return home");
        }

        if (containsAny(input, "wood", "gather wood", "collect wood", "چوب", "هیزم", "چوب جمع کن")) {
            return new Plan(RobotMode.GATHER_WOOD, "gather wood");
        }

        if (containsAny(input, "stone", "cobblestone", "gather stone", "سنگ", "کابل استون", "سنگ جمع کن")) {
            return new Plan(RobotMode.GATHER_STONE, "gather stone");
        }

        if (containsAny(input, "coal", "gather coal", "زغال", "زغال سنگ", "زغال جمع کن")) {
            return new Plan(RobotMode.GATHER_COAL, "gather coal");
        }

        if (containsAny(input, "house", "build a house", "build house", "خانه", "خونه", "ساختمان", "خانه بساز", "خونه بساز")) {
            return new Plan(RobotMode.BUILD_HOUSE, "build a house");
        }

        if (containsAny(input, "tower", "build tower", "برج", "برج بساز")) {
            return new Plan(RobotMode.BUILD_TOWER, "build a tower");
        }

        if (containsAny(input, "wander", "roam", "گشت", "بگرد", "دور و بر را بگرد")) {
            return new Plan(RobotMode.WANDER, "wander nearby");
        }

        if (containsAny(input, "explore", "explore area", "کاوش", "جستجو", "اطراف را بگرد", "منطقه را بگرد")) {
            return new Plan(RobotMode.EXPLORE, "explore the area");
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
