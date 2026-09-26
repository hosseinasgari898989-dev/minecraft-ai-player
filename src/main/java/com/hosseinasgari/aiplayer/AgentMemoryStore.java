package com.hosseinasgari.aiplayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AgentMemoryStore {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final Path ROOT = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("ai-player")
            .resolve("brain");

    private AgentMemoryStore() {
    }

    public static final class State {
        public int version = 1;
        public List<ChatTurn> chat = new ArrayList<>();
        public List<MemoryItem> memories = new ArrayList<>();
        public List<LearnedCommand> learnedCommands = new ArrayList<>();
        public List<TaskItem> tasks = new ArrayList<>();
    }

    public static final class ChatTurn {
        public String role;
        public String content;
        public long createdAt;

        public ChatTurn() {
        }

        public ChatTurn(String role, String content, long createdAt) {
            this.role = role;
            this.content = content;
            this.createdAt = createdAt;
        }
    }

    public static final class MemoryItem {
        public String type;
        public String content;
        public long createdAt;

        public MemoryItem() {
        }

        public MemoryItem(String type, String content, long createdAt) {
            this.type = type;
            this.content = content;
            this.createdAt = createdAt;
        }
    }

    public static final class LearnedCommand {
        public String phrase;
        public String intent;
        public long createdAt;

        public LearnedCommand() {
        }

        public LearnedCommand(String phrase, String intent, long createdAt) {
            this.phrase = phrase;
            this.intent = intent;
            this.createdAt = createdAt;
        }
    }

    public static final class TaskItem {
        public String id;
        public String title;
        public String details;
        public List<String> steps = new ArrayList<>();
        public String status;
        public int priority;
        public long createdAt;
        public long updatedAt;

        public TaskItem() {
        }

        public TaskItem(
                String id,
                String title,
                String details,
                List<String> steps,
                String status,
                int priority,
                long createdAt
        ) {
            this.id = id;
            this.title = title;
            this.details = details;
            this.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
            this.status = status;
            this.priority = priority;
            this.createdAt = createdAt;
            this.updatedAt = createdAt;
        }
    }

    public static State load(UUID playerUuid) {
        Path path = pathFor(playerUuid);

        try {
            Files.createDirectories(ROOT);

            if (Files.notExists(path)) {
                return new State();
            }

            String json = Files.readString(path, StandardCharsets.UTF_8);
            State state = GSON.fromJson(json, State.class);
            if (state == null) {
                return new State();
            }

            normalize(state);
            return state;
        } catch (Exception ignored) {
            return new State();
        }
    }

    public static void save(UUID playerUuid, State state) {
        if (playerUuid == null || state == null) {
            return;
        }

        normalize(state);

        try {
            Files.createDirectories(ROOT);

            Path target = pathFor(playerUuid);
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(
                    temp,
                    GSON.toJson(state),
                    StandardCharsets.UTF_8
            );

            try {
                Files.move(
                        temp,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (IOException atomicMoveNotSupported) {
                Files.move(
                        temp,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException ignored) {
            // Memory is useful but must never stop Minecraft from running.
        }
    }

    public static void addChat(State state, String role, String content, int maxMessages) {
        if (state == null || content == null || content.isBlank()) {
            return;
        }

        if (state.chat == null) {
            state.chat = new ArrayList<>();
        }

        state.chat.add(new ChatTurn(
                role == null || role.isBlank() ? "user" : role,
                content.trim(),
                System.currentTimeMillis()
        ));

        int limit = Math.max(4, Math.min(100, maxMessages));
        while (state.chat.size() > limit) {
            state.chat.remove(0);
        }
    }

    public static void mergeAiOutput(
            State state,
            List<AiIntentClient.MemorySuggestion> memories,
            List<AiIntentClient.LearnedCommandSuggestion> commands,
            List<AiIntentClient.TaskSuggestion> tasks
    ) {
        if (state == null) {
            return;
        }

        if (state.memories == null) {
            state.memories = new ArrayList<>();
        }
        if (state.learnedCommands == null) {
            state.learnedCommands = new ArrayList<>();
        }
        if (state.tasks == null) {
            state.tasks = new ArrayList<>();
        }

        long now = System.currentTimeMillis();

        if (memories != null) {
            for (AiIntentClient.MemorySuggestion item : memories) {
                if (item == null || item.content() == null || item.content().isBlank()) {
                    continue;
                }

                String type = item.type() == null || item.type().isBlank()
                        ? "fact"
                        : item.type().trim();

                String content = item.content().trim();
                if (!containsMemory(state.memories, content)) {
                    state.memories.add(new MemoryItem(type, content, now));
                }
            }
        }

        if (commands != null) {
            for (AiIntentClient.LearnedCommandSuggestion command : commands) {
                if (command == null
                        || command.phrase() == null
                        || command.phrase().isBlank()
                        || command.intent() == null
                        || command.intent().isBlank()) {
                    continue;
                }

                String phrase = command.phrase().trim();
                String intent = command.intent().trim();

                state.learnedCommands.removeIf(existing ->
                        existing.phrase != null
                                && existing.phrase.equalsIgnoreCase(phrase));

                state.learnedCommands.add(
                        new LearnedCommand(phrase, intent, now)
                );
            }
        }

        if (tasks != null) {
            for (AiIntentClient.TaskSuggestion task : tasks) {
                if (task == null || task.title() == null || task.title().isBlank()) {
                    continue;
                }

                int priority = Math.max(0, Math.min(10, task.priority()));
                List<String> steps = task.steps() == null
                        ? new ArrayList<>()
                        : task.steps().stream()
                        .filter(step -> step != null && !step.isBlank())
                        .map(String::trim)
                        .limit(32)
                        .toList();

                state.tasks.add(new TaskItem(
                        UUID.randomUUID().toString(),
                        task.title().trim(),
                        task.details() == null ? "" : task.details().trim(),
                        steps,
                        "PLANNED",
                        priority,
                        now
                ));
            }
        }

        trimHistory(state);
    }

    public static void markLatestTaskRunning(State state) {
        if (state == null || state.tasks == null || state.tasks.isEmpty()) {
            return;
        }

        TaskItem latest = state.tasks.get(state.tasks.size() - 1);
        latest.status = "RUNNING";
        latest.updatedAt = System.currentTimeMillis();
    }

    public static String buildModelContext(State state, int maxChatMessages) {
        if (state == null) {
            return "No persistent memory is available yet.";
        }

        StringBuilder result = new StringBuilder();

        result.append("PERSISTENT_MEMORY:\n");
        if (state.memories != null && !state.memories.isEmpty()) {
            int start = Math.max(0, state.memories.size() - 30);
            for (int i = start; i < state.memories.size(); i++) {
                MemoryItem item = state.memories.get(i);
                result.append("- [")
                        .append(item.type == null ? "fact" : item.type)
                        .append("] ")
                        .append(item.content == null ? "" : item.content)
                        .append('\n');
            }
        } else {
            result.append("- none\n");
        }

        result.append("\nLEARNED_COMMANDS:\n");
        if (state.learnedCommands != null && !state.learnedCommands.isEmpty()) {
            int start = Math.max(0, state.learnedCommands.size() - 30);
            for (int i = start; i < state.learnedCommands.size(); i++) {
                LearnedCommand command = state.learnedCommands.get(i);
                result.append("- ")
                        .append(command.phrase == null ? "" : command.phrase)
                        .append(" => ")
                        .append(command.intent == null ? "" : command.intent)
                        .append('\n');
            }
        } else {
            result.append("- none\n");
        }

        result.append("\nTASK_MEMORY:\n");
        if (state.tasks != null && !state.tasks.isEmpty()) {
            int start = Math.max(0, state.tasks.size() - 20);
            for (int i = start; i < state.tasks.size(); i++) {
                TaskItem task = state.tasks.get(i);
                result.append("- [")
                        .append(task.status == null ? "UNKNOWN" : task.status)
                        .append("] ")
                        .append(task.title == null ? "" : task.title)
                        .append(" :: ")
                        .append(task.details == null ? "" : task.details)
                        .append('\n');
            }
        } else {
            result.append("- none\n");
        }

        result.append("\nRECENT_CONVERSATION:\n");
        if (state.chat != null && !state.chat.isEmpty()) {
            int limit = Math.max(2, Math.min(40, maxChatMessages));
            int start = Math.max(0, state.chat.size() - limit);
            for (int i = start; i < state.chat.size(); i++) {
                ChatTurn turn = state.chat.get(i);
                result.append(turn.role == null ? "user" : turn.role)
                        .append(": ")
                        .append(turn.content == null ? "" : turn.content)
                        .append('\n');
            }
        } else {
            result.append("- none\n");
        }

        return result.toString();
    }

    private static Path pathFor(UUID playerUuid) {
        return ROOT.resolve(playerUuid + ".json");
    }

    private static boolean containsMemory(List<MemoryItem> memories, String content) {
        return memories.stream().anyMatch(existing ->
                existing.content != null
                        && existing.content.equalsIgnoreCase(content)
        );
    }

    private static void normalize(State state) {
        if (state.version <= 0) {
            state.version = 1;
        }
        if (state.chat == null) {
            state.chat = new ArrayList<>();
        }
        if (state.memories == null) {
            state.memories = new ArrayList<>();
        }
        if (state.learnedCommands == null) {
            state.learnedCommands = new ArrayList<>();
        }
        if (state.tasks == null) {
            state.tasks = new ArrayList<>();
        }

        trimHistory(state);
    }

    private static void trimHistory(State state) {
        trimList(state.chat, 80);
        trimList(state.memories, 100);
        trimList(state.learnedCommands, 100);
        trimList(state.tasks, 100);
    }

    private static <T> void trimList(List<T> list, int maxSize) {
        while (list.size() > maxSize) {
            list.remove(0);
        }
    }
}
