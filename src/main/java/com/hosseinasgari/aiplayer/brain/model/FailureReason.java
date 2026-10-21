package com.hosseinasgari.aiplayer.brain.model;

public enum FailureReason {
    NONE("No failure."),
    ROUTE_BLOCKED("The route is blocked and rerouting attempts were exhausted."),
    RESOURCE_UNAVAILABLE("I could not find the required resource nearby."),
    INVENTORY_FULL("I cannot continue because my inventory is full."),
    CRAFTING_PREREQUISITE_MISSING("Required crafting ingredients or station are missing."),
    TARGET_LOST("The target moved out of range or disappeared."),
    SITE_UNSUITABLE("Could not find a safe, level build site without colliding with structures."),
    PVP_NOT_AUTHORIZED("Player combat is disabled or explicit PvP authorization was not granted."),
    ACTION_RETRIES_EXCEEDED("The action failed repeatedly and was safely aborted."),
    TASK_TIMEOUT("The task exceeded its safety time budget."),
    SAVED_TASK_INVALID("The saved task state after reload was no longer valid.");

    private final String defaultMessage;

    FailureReason(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
