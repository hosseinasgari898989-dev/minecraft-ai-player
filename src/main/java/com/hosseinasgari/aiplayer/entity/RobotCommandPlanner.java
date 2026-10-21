package com.hosseinasgari.aiplayer.entity;

import com.hosseinasgari.aiplayer.brain.model.CommandIntent;
import com.hosseinasgari.aiplayer.brain.model.IntentType;
import com.hosseinasgari.aiplayer.brain.nlp.RuleBasedCommandInterpreter;

public final class RobotCommandPlanner {
    public record Plan(RobotMode mode, String description, CommandIntent sourceIntent) {
        public Plan(RobotMode mode, String description) {
            this(mode, description, CommandIntent.simple(IntentType.FOLLOW_OWNER, "en", description));
        }
    }

    private RobotCommandPlanner() {}

    public static Plan plan(String raw) {
        CommandIntent intent = RuleBasedCommandInterpreter.interpret(raw);
        if (intent.ambiguous() || intent.intent() == IntentType.UNSUPPORTED) return null;
        return fromIntent(intent);
    }

    public static Plan fromIntent(CommandIntent intent) {
        return switch (intent.intent()) {
            case STOP, CANCEL_TASK -> new Plan(RobotMode.IDLE, "stopped and cleared task", intent);
            case COME_HERE, RETURN_OWNER -> new Plan(RobotMode.RETURN_OWNER, "returning to owner", intent);
            case FOLLOW_OWNER, STAY_BESIDE -> new Plan(RobotMode.FOLLOW, "following owner", intent);
            case HOLD_POSITION -> new Plan(RobotMode.STAY, "holding position", intent);
            case PROTECT_OWNER, PROTECT_NIGHT -> new Plan(RobotMode.PROTECT, "protecting owner", intent);
            case GUARD_AREA -> new Plan(RobotMode.GUARD, "guarding area", intent);
            case PATROL_AREA -> new Plan(RobotMode.PATROL, "patrolling area", intent);
            case RETURN_HOME -> new Plan(RobotMode.RETURN_HOME, "returning home", intent);
            case SET_HOME -> new Plan(RobotMode.COMPANION, "updated home anchor", intent);
            case GATHER_RESOURCE -> {
                String res = intent.resource() == null ? "wood" : intent.resource();
                int qty = intent.quantity() == null ? 8 : intent.quantity();
                RobotMode m = switch (res) {
                    case "stone" -> RobotMode.GATHER_STONE;
                    case "coal" -> RobotMode.GATHER_COAL;
                    case "iron" -> RobotMode.MINE_ORES;
                    default -> RobotMode.GATHER_WOOD;
                };
                yield new Plan(m, "gather " + qty + " " + res, intent);
            }
            case GO_MINING -> new Plan(RobotMode.MINE_ORES, "mining stone, coal, and iron", intent);
            case FIND_FOOD -> new Plan(RobotMode.FIND_FOOD, "finding food", intent);
            case UPGRADE_TOOLS -> new Plan(RobotMode.UPGRADE_TOOLS, "upgrading survival tools", intent);
            case ORGANIZE_INVENTORY -> new Plan(RobotMode.ORGANIZE_INVENTORY, "organizing inventory", intent);
            case STORE_ITEMS -> new Plan(RobotMode.STORE_ITEMS, "storing surplus in nearby chest", intent);
            case PLANT_CROPS -> new Plan(RobotMode.PLANT_CROPS, "planting wheat crops", intent);
            case HARVEST_CROPS -> new Plan(RobotMode.HARVEST_CROPS, "harvesting mature crops", intent);
            case BUILD_STRUCTURE -> {
                String style = intent.structureStyle() == null ? "compact_wood" : intent.structureStyle();
                RobotMode m = "watchtower".equals(style) ? RobotMode.BUILD_TOWER : RobotMode.BUILD_HOUSE;
                yield new Plan(m, "building " + style, intent);
            }
            case SCAN_THREATS -> new Plan(RobotMode.SCAN_THREATS, "scanning nearby threats", intent);
            case FIND_PLAYER -> new Plan(RobotMode.FIND_PLAYER, "locating player " + intent.targetPlayer(), intent);
            case FOLLOW_PLAYER -> new Plan(RobotMode.FOLLOW_PLAYER, "following player " + intent.targetPlayer(), intent);
            case ATTACK_PLAYER -> new Plan(RobotMode.ENGAGE_PVP, "evaluating PvP target " + intent.targetPlayer(), intent);
            case EXPLORE -> new Plan(RobotMode.EXPLORE, "exploring nearby terrain", intent);
            default -> new Plan(RobotMode.COMPANION, "companion active", intent);
        };
    }
}
