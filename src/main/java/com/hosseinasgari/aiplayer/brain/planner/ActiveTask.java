package com.hosseinasgari.aiplayer.brain.planner;

import com.hosseinasgari.aiplayer.brain.model.FailureReason;
import com.hosseinasgari.aiplayer.brain.model.IntentType;
import java.util.*;

public final class ActiveTask {
    public enum Status { PENDING, ACTIVE, SUSPENDED, COMPLETED, CANCELLED, FAILED }

    public static final class Subtask {
        private final String skillId;
        private final String parameter;
        private final int targetCount;
        private int completedCount;
        private Status status = Status.PENDING;

        public Subtask(String skillId, String parameter, int targetCount) {
            this.skillId = Objects.requireNonNull(skillId);
            this.parameter = parameter == null ? "" : parameter;
            this.targetCount = Math.max(1, targetCount);
        }
        public String skillId() { return skillId; }
        public String parameter() { return parameter; }
        public int targetCount() { return targetCount; }
        public int completedCount() { return completedCount; }
        public Status status() { return status; }
        public void setCompletedCount(int value) {
            completedCount = Math.max(0, Math.min(targetCount, value));
            if (completedCount >= targetCount) status = Status.COMPLETED;
        }
        public void markActive() {
            if (status == Status.PENDING || status == Status.SUSPENDED) status = Status.ACTIVE;
        }
        public void markCompleted() { completedCount = targetCount; status = Status.COMPLETED; }
        public void suspend() { if (status == Status.ACTIVE || status == Status.PENDING) status = Status.SUSPENDED; }
    }

    private final String id = "task-" + UUID.randomUUID().toString().substring(0, 8);
    private final IntentType intent;
    private final int priority;
    private final String language;
    private final String summary;
    private final Map<String,Integer> requiredResources;
    private final List<Subtask> subtasks;
    private final int maxRetries;
    private final long maxDurationTicks;
    private Status status = Status.ACTIVE;
    private FailureReason failureReason = FailureReason.NONE;
    private String statusDetail = "";
    private int retryCount;
    private long elapsedTicks;

    public ActiveTask(IntentType intent, int priority, String language, String summary,
                      Map<String,Integer> requiredResources, List<Subtask> subtasks,
                      int maxRetries, long maxDurationTicks) {
        this.intent = Objects.requireNonNull(intent);
        this.priority = priority;
        this.language = language == null ? "en" : language;
        this.summary = summary == null ? intent.name() : summary;
        this.requiredResources = Collections.unmodifiableMap(new LinkedHashMap<>(requiredResources == null ? Map.of() : requiredResources));
        this.subtasks = new ArrayList<>(subtasks == null ? List.of() : subtasks);
        this.maxRetries = Math.max(1, maxRetries);
        this.maxDurationTicks = Math.max(200L, maxDurationTicks);
        if (!this.subtasks.isEmpty()) this.subtasks.get(0).markActive();
    }
    public String id() { return id; }
    public IntentType intent() { return intent; }
    public int priority() { return priority; }
    public String language() { return language; }
    public String summary() { return summary; }
    public Map<String,Integer> requiredResources() { return requiredResources; }
    public List<Subtask> subtasks() { return Collections.unmodifiableList(subtasks); }
    public Subtask currentSubtask() {
        for (Subtask s : subtasks) {
            if (s.status() != Status.COMPLETED && s.status() != Status.CANCELLED && s.status() != Status.FAILED) {
                s.markActive();
                return s;
            }
        }
        return null;
    }
    public Status status() { return status; }
    public FailureReason failureReason() { return failureReason; }
    public String statusDetail() { return statusDetail; }
    public int retryCount() { return retryCount; }
    public int maxRetries() { return maxRetries; }
    public void tickElapsed(long ticks) {
        elapsedTicks += Math.max(1L, ticks);
        if (elapsedTicks > maxDurationTicks && status == Status.ACTIVE) fail(FailureReason.TASK_TIMEOUT, FailureReason.TASK_TIMEOUT.defaultMessage());
    }
    public boolean recordFailureAndShouldAbort(FailureReason reason, String detail) {
        retryCount++;
        failureReason = reason;
        statusDetail = detail == null ? reason.defaultMessage() : detail;
        if (retryCount > maxRetries) { status = Status.FAILED; return true; }
        return false;
    }
    public void suspend(String reason) {
        if (status == Status.ACTIVE || status == Status.PENDING) {
            status = Status.SUSPENDED; statusDetail = reason;
            for (Subtask sub : subtasks) sub.suspend();
        }
    }
    public void resume() {
        if (status == Status.SUSPENDED) { status = Status.ACTIVE; statusDetail = "Resumed task: " + summary; }
    }
    public void complete(String detail) {
        for (Subtask s : subtasks) s.markCompleted();
        status = Status.COMPLETED; failureReason = FailureReason.NONE; statusDetail = detail == null ? "" : detail;
    }
    public void cancel(String detail) { status = Status.CANCELLED; statusDetail = detail == null ? "Cancelled" : detail; }
    public void fail(FailureReason reason, String detail) {
        status = Status.FAILED; failureReason = reason == null ? FailureReason.ACTION_RETRIES_EXCEEDED : reason;
        statusDetail = detail == null ? failureReason.defaultMessage() : detail;
    }
}
