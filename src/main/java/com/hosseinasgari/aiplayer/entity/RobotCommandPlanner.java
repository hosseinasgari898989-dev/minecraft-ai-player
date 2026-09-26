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

        if (containsAny(input, "wood", "gather wood", "collect wood", "چوب", "هیزم", "چوب جمع کن", "چوب بیار", "چوب تهیه کن")) {
            return new Plan(RobotMode.GATHER_WOOD, "gather wood");
        }

        if (containsAny(input, "stone", "cobblestone", "gather stone", "سنگ", "کابل استون", "سنگ جمع کن", "سنگ بیار")) {
            return new Plan(RobotMode.GATHER_STONE, "gather stone");
        }

        if (containsAny(input, "coal", "gather coal", "زغال", "زغال سنگ", "زغال جمع کن", "زغال بیار")) {
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

    public static String normalizeAction(String rawAction) {
        if (rawAction == null || rawAction.isBlank()) {
            return "";
        }

        String value = rawAction.trim().toLowerCase(Locale.ROOT);

        if (containsAny(value, "wood", "چوب", "هیزم")) {
            return "GATHER_WOOD";
        }
        if (containsAny(value, "stone", "cobblestone", "سنگ")) {
            return "GATHER_STONE";
        }
        if (containsAny(value, "coal", "زغال")) {
            return "GATHER_COAL";
        }
        if (containsAny(value, "house", "home", "خانه", "خونه")) {
            return "BUILD_HOUSE";
        }
        if (containsAny(value, "tower", "برج")) {
            return "BUILD_TOWER";
        }
        if (containsAny(value, "follow", "دنبال", "دنبالم")) {
            return "FOLLOW";
        }
        if (containsAny(value, "protect", "محافظت")) {
            return "PROTECT";
        }
        if (containsAny(value, "guard", "نگهبان", "نگهبانی")) {
            return "GUARD";
        }
        if (containsAny(value, "patrol", "گشت")) {
            return "PATROL";
        }
        if (containsAny(value, "explore", "کاوش", "جستجو")) {
            return "EXPLORE";
        }
        if (containsAny(value, "wander", "بگرد")) {
            return "WANDER";
        }
        if (containsAny(value, "return_home", "return home", "برگرد خونه", "برگرد خانه")) {
            return "RETURN_HOME";
        }
        if (containsAny(value, "idle", "stop", "توقف", "وایسا", "بس کن")) {
            return "IDLE";
        }

        return value
                .replaceAll("[^A-Za-z0-9_]+", "_")
                .replaceAll("^_+|_+$", "")
                .toUpperCase(Locale.ROOT);
    }

    public static Plan planAction(String action) {
        String normalized = normalizeAction(action);
        if (normalized.isBlank()) {
            return null;
        }

        return switch (normalized) {
            case "IDLE", "STOP" -> new Plan(RobotMode.IDLE, "stopped");
            case "FOLLOW" -> new Plan(RobotMode.FOLLOW, "follow owner");
            case "WANDER" -> new Plan(RobotMode.WANDER, "wander nearby");
            case "EXPLORE" -> new Plan(RobotMode.EXPLORE, "explore the area");
            case "GUARD" -> new Plan(RobotMode.GUARD, "guard home");
            case "PROTECT" -> new Plan(RobotMode.PROTECT, "protect owner");
            case "PATROL" -> new Plan(RobotMode.PATROL, "patrol around home");
            case "RETURN_HOME" -> new Plan(RobotMode.RETURN_HOME, "return home");
            case "GATHER_WOOD" -> new Plan(RobotMode.GATHER_WOOD, "gather wood");
            case "GATHER_STONE" -> new Plan(RobotMode.GATHER_STONE, "gather stone");
            case "GATHER_COAL" -> new Plan(RobotMode.GATHER_COAL, "gather coal");
            case "BUILD_HOUSE" -> new Plan(RobotMode.BUILD_HOUSE, "build a house");
            case "BUILD_TOWER" -> new Plan(RobotMode.BUILD_TOWER, "build a tower");
            default -> null;
        };
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
