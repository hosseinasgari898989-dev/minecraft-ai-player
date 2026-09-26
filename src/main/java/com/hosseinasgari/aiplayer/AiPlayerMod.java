package com.hosseinasgari.aiplayer;

import com.hosseinasgari.aiplayer.entity.AiPlayerEntity;
import com.hosseinasgari.aiplayer.entity.ModEntities;
import com.hosseinasgari.aiplayer.entity.RobotCommandPlanner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.stream.Stream;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class AiPlayerMod implements ModInitializer {
    public static final String MOD_ID = "ai_player";

    private static final URI LATEST_JAR = URI.create(
            "https://raw.githubusercontent.com/hosseinasgari898989-dev/minecraft-ai-player/main/latest/ai-player.jar"
    );

    private static AiConfig aiConfig;
    private static AiIntentClient aiClient;
    private static final Set<UUID> AI_REQUESTS_IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static long companionCheckTicker;

    @Override
    public void onInitialize() {
        ModEntities.registerAttributes();

        aiConfig = AiConfig.load();
        aiClient = new AiIntentClient(aiConfig);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> ensureTemporaryCompanion(handler.getPlayer()))
        );
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            removeOwnedRobots(server, handler.getPlayer().getUuid());
            AI_REQUESTS_IN_FLIGHT.remove(handler.getPlayer().getUuid());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            companionCheckTicker++;
            if (companionCheckTicker % 40L != 0L) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ensureTemporaryCompanion(player);
            }
        });

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
                handleNaturalLanguageChat(sender, message.getContent().getString())
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    literal("aiplayer")
                            .then(
                                    literal("hello")
                                            .executes(context -> {
                                                context.getSource().sendFeedback(
                                                        () -> Text.literal("AI Player robot engine is installed and ready."),
                                                        false
                                                );
                                                return 1;
                                            })
                            )
                            .then(
                                    literal("help")
                                            .executes(context -> showHelp(context.getSource()))
                            )
                            .then(
                                    literal("spawn")
                                            .executes(context -> {
                                                if (!(context.getSource().getEntity() instanceof ServerPlayerEntity player)) {
                                                    context.getSource().sendError(
                                                            Text.literal("Only a player can spawn the AI Player.")
                                                    );
                                                    return 0;
                                                }

                                                ensureTemporaryCompanion(player);
                                                var entity = findOwnedRobot(player);
                                                if (entity == null) {
                                                    context.getSource().sendError(
                                                            Text.literal("Could not create or find the AI Player.")
                                                    );
                                                    return 0;
                                                }

                                                entity.requestTeleport(
                                                        player.getX() + 2.0,
                                                        player.getY(),
                                                        player.getZ() + 2.0
                                                );
                                                entity.getNavigation().stop();

                                                context.getSource().sendFeedback(
                                                        () -> Text.literal(
                                                                "AI Player is with you. برای کنترلش کافی است در چت طبیعی صحبت کنی."
                                                        ),
                                                        false
                                                );
                                                return 1;
                                            })
                            )
                            .then(
                                    literal("status")
                                            .executes(context -> showStatus(
                                                    context.getSource().getPlayerOrThrow(),
                                                    context.getSource()
                                            ))
                            )
                            .then(
                                    literal("aistatus")
                                            .executes(context -> showAiStatus(context.getSource()))
                            )
                            .then(
                                    literal("aitest")
                                            .executes(context -> {
                                                var source = context.getSource();
                                                Thread.ofVirtual().start(() -> {
                                                    try {
                                                        int status = aiClient.ping();
                                                        source.getServer().execute(() ->
                                                                source.sendFeedback(
                                                                        () -> Text.literal("اتصال AI محلی: HTTP " + status),
                                                                        false
                                                                )
                                                        );
                                                    } catch (Exception error) {
                                                        source.getServer().execute(() ->
                                                                source.sendError(
                                                                        Text.literal("اتصال AI محلی برقرار نشد: " + error.getMessage())
                                                                )
                                                        );
                                                    }
                                                });
                                                return 1;
                                            })
                            )
                            .then(
                                    literal("aireload")
                                            .executes(context -> reloadAiConfig(context.getSource()))
                            )
                            .then(
                                    literal("sethome")
                                            .executes(context -> {
                                                var player = context.getSource().getPlayerOrThrow();
                                                var robot = findOwnedRobot(player);
                                                if (robot == null) {
                                                    context.getSource().sendError(
                                                            Text.literal("اول /aiplayer spawn را بزن.")
                                                    );
                                                    return 0;
                                                }
                                                robot.setHome();
                                                context.getSource().sendFeedback(
                                                        () -> Text.literal("خانه ربات ذخیره شد."),
                                                        false
                                                );
                                                return 1;
                                            })
                            )
                            .then(
                                    literal("un")
                                            .then(argument("action", greedyString())
                                                    .executes(context -> setActionEnabled(
                                                            context.getSource(), getString(context, "action"), false
                                                    )))
                            )
                            .then(
                                    literal("on")
                                            .then(argument("action", greedyString())
                                                    .executes(context -> setActionEnabled(
                                                            context.getSource(), getString(context, "action"), true
                                                    )))
                            )
                            .then(
                                    literal("update")
                                            .executes(context -> updateMod(context.getSource()))
                            )
            );
        });
    }

    private static void handleNaturalLanguageChat(ServerPlayerEntity player, String rawMessage) {
        if (!aiConfig.chatEnabled()) {
            return;
        }

        String instruction = extractBotInstruction(player, rawMessage);
        if (instruction == null || instruction.isBlank()) {
            return;
        }

        AiPlayerEntity robot = findOwnedRobot(player);
        if (robot == null && aiConfig.autoSpawn()) {
            ensureTemporaryCompanion(player);
            robot = findOwnedRobot(player);
        }

        if (robot == null) {
            player.sendMessage(Text.literal("🤖 ربات همراه پیدا نشد."), false);
            return;
        }

        final AiPlayerEntity commandRobot = robot;

        if (!aiClient.isConfigured()) {
            AgentMemoryStore.State offlineMemory = AgentMemoryStore.load(player.getUuid());
            AgentMemoryStore.addChat(
                    offlineMemory,
                    "user",
                    instruction,
                    aiConfig.memoryMessages()
            );

            RobotCommandPlanner.Plan localPlan = RobotCommandPlanner.plan(instruction);
            String offlineReply;
            if (localPlan != null && aiConfig.isActionEnabled(localPlan.mode().name())) {
                robot.applyPlan(localPlan);
                offlineReply = "AI تنظیم نیست؛ فرمان شناخته‌شده را اجرا کردم: " + localPlan.description();
            } else {
                offlineReply = "AI تنظیم نیست؛ پیام را در حافظه نگه داشتم تا بعداً ادامه بدهیم.";
            }

            AgentMemoryStore.addChat(
                    offlineMemory,
                    "assistant",
                    offlineReply,
                    aiConfig.memoryMessages()
            );
            AgentMemoryStore.save(player.getUuid(), offlineMemory);

            player.sendMessage(Text.literal("🤖 " + offlineReply), false);
            return;
        }

        if (!AI_REQUESTS_IN_FLIGHT.add(player.getUuid())) {
            player.sendMessage(Text.literal("🤖 یک پیام هنوز در حال پردازش است..."), false);
            return;
        }

        String context = buildRobotContext(robot);
        AgentMemoryStore.State memoryState = AgentMemoryStore.load(player.getUuid());
        String memoryContext = AgentMemoryStore.buildModelContext(
                memoryState,
                aiConfig.memoryMessages()
        );

        Thread.ofVirtual().start(() -> {
            try {
                Optional<AiIntentClient.Decision> result =
                        aiClient.chat(instruction, context, memoryContext);

                player.getServer().execute(() -> {
                    try {
                        if (result.isEmpty()) {
                            player.sendMessage(Text.literal("🤖 پاسخی از AI نگرفتم."), false);
                            return;
                        }

                        AiIntentClient.Decision decision = result.get();
                        String reply = decision.reply();

                        if (!reply.isBlank()) {
                            player.sendMessage(
                                    Text.literal("§dAI Player§f: " + reply),
                                    false
                            );
                        }

                        AgentMemoryStore.addChat(
                                memoryState,
                                "user",
                                instruction,
                                aiConfig.memoryMessages()
                        );
                        if (!reply.isBlank()) {
                            AgentMemoryStore.addChat(
                                    memoryState,
                                    "assistant",
                                    reply,
                                    aiConfig.memoryMessages()
                            );
                        }

                        AgentMemoryStore.mergeAiOutput(
                                memoryState,
                                decision.memories(),
                                decision.commands(),
                                decision.tasks()
                        );

                        List<RobotCommandPlanner.Plan> agentPlans = decision.actions().stream()
                                .map(action -> AgentSkillRegistry.resolve(action.skill()))
                                .flatMap(Optional::stream)
                                .filter(plan -> aiConfig.isActionEnabled(plan.mode().name()))
                                .toList();

                        if (!agentPlans.isEmpty()) {
                            commandRobot.enqueueAgentPlans(agentPlans);
                            AgentMemoryStore.markLatestTaskRunning(memoryState);
                        } else if (decision.plan() != null) {
                            commandRobot.applyPlan(decision.plan());
                            AgentMemoryStore.markLatestTaskRunning(memoryState);
                        }

                        AgentMemoryStore.save(player.getUuid(), memoryState);

                        if (agentPlans.isEmpty() && decision.plan() == null && !decision.tasks().isEmpty()) {
                            AiIntentClient.TaskSuggestion task = decision.tasks().get(0);
                            player.sendMessage(
                                    Text.literal("🧠 تسک ثبت شد: " + task.title()),
                                    false
                            );
                        }
                    } finally {
                        AI_REQUESTS_IN_FLIGHT.remove(player.getUuid());
                    }
                });
            } catch (Exception error) {
                player.getServer().execute(() -> {
                    try {
                        RobotCommandPlanner.Plan fallback = RobotCommandPlanner.plan(instruction);
                        String fallbackReply;

                        if (fallback != null && aiConfig.isActionEnabled(fallback.mode().name())) {
                            commandRobot.applyPlan(fallback);
                            fallbackReply = "ارتباط با AI مشکل داشت؛ فرمان شناخته‌شده را مستقیم اجرا کردم.";
                            player.sendMessage(
                                    Text.literal("§dAI Player§f: " + fallbackReply),
                                    false
                            );
                        } else {
                            fallbackReply = "ارتباط با AI برقرار نشد؛ پیام را نگه داشتم تا بعداً بتوانیم ادامه بدهیم.";
                            player.sendMessage(
                                    Text.literal("§dAI Player§f: " + fallbackReply),
                                    false
                            );
                        }

                        AgentMemoryStore.addChat(
                                memoryState,
                                "user",
                                instruction,
                                aiConfig.memoryMessages()
                        );
                        AgentMemoryStore.addChat(
                                memoryState,
                                "assistant",
                                fallbackReply,
                                aiConfig.memoryMessages()
                        );
                        AgentMemoryStore.save(player.getUuid(), memoryState);
                    } finally {
                        AI_REQUESTS_IN_FLIGHT.remove(player.getUuid());
                    }
                });
            }
        });
    }

    private static String buildRobotContext(AiPlayerEntity robot) {
        var pos = robot.getBlockPos();
        String home = robot.getHomePos() == null
                ? "unset"
                : robot.getHomePos().getX() + "," + robot.getHomePos().getY() + "," + robot.getHomePos().getZ();

        return "mode=" + robot.getMode().name()
                + "; task=" + robot.getTaskDescription()
                + "; health=" + robot.getHealth()
                + "; position=" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + "; home=" + home
                + "; commands=" + robot.getCommandsExecuted()
                + "; blocksBroken=" + robot.getBlocksBroken()
                + "; blocksPlaced=" + robot.getBlocksPlaced()
                + "; attacks=" + robot.getAttacksMade()
                + "; queuedActions=" + robot.getQueuedPlanCount();
    }

    private static String extractBotInstruction(ServerPlayerEntity player, String rawMessage) {
        if (rawMessage == null) {
            return null;
        }

        String input = rawMessage.trim();
        if (input.isBlank()) {
            return null;
        }

        boolean singleplayer = player.getServer() != null && player.getServer().isSingleplayer();
        if (!aiConfig.requirePrefix() && singleplayer) {
            return input;
        }

        String prefix = aiConfig.triggerPrefix();
        String[] prefixes = {
                prefix + " ",
                prefix + ":",
                "@" + prefix + " ",
                "@" + prefix + ":",
                "bot ",
                "bot:",
                "@bot ",
                "@bot:"
        };

        for (String candidate : prefixes) {
            if (input.regionMatches(true, 0, candidate, 0, candidate.length())) {
                return input.substring(candidate.length()).trim();
            }
        }

        return null;
    }

    private static void applyPlanFromAi(ServerPlayerEntity player, RobotCommandPlanner.Plan plan) {
        AiPlayerEntity robot = findOwnedRobot(player);
        if (robot == null) {
            player.sendMessage(Text.literal("🤖 رباتت دیگر در محدوده نیست."), false);
            return;
        }

        robot.applyPlan(plan);
        player.sendMessage(Text.literal("🤖 انجام می‌دم: " + plan.description()), false);
    }

    private static int applyInstruction(
            ServerPlayerEntity player,
            String instruction,
            net.minecraft.server.command.ServerCommandSource source
    ) {
        AiPlayerEntity robot = findOwnedRobot(player);
        if (robot == null) {
            source.sendError(
                    Text.literal("No owned AI Player is nearby. Spawn one first with /aiplayer spawn.")
            );
            return 0;
        }

        RobotCommandPlanner.Plan plan = RobotCommandPlanner.plan(instruction);
        if (plan == null) {
            source.sendError(
                    Text.literal(
                            "I don't understand that yet. Try: follow me, guard here, protect me, gather wood, build house, wander, or stop."
                    )
            );
            return 0;
        }

        robot.applyPlan(plan);
        source.sendFeedback(
                () -> Text.literal("Robot command accepted: " + plan.description()),
                false
        );
        return 1;
    }

    private static int showHelp(net.minecraft.server.command.ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("§6===== AI Player ====="), false);
        source.sendFeedback(() -> Text.literal("§fربات با چت طبیعی کنترل می‌شود؛ مثلاً: §eبرو چوب جمع کن"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer aistatus §f- وضعیت AI و مدل"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer aireload §f- بارگذاری دوباره تنظیمات"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer aitest §f- تست اتصال مستقیم به AI محلی"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer un <action> §f- غیرفعال کردن یک توانایی"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer on <action> §f- فعال کردن توانایی"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer update §f- به‌روزرسانی مود"), false);
        source.sendFeedback(() -> Text.literal("§7ربات هنگام ورود شما به جهان به‌طور خودکار حاضر می‌شود."), false);
        return 1;
    }

    private static int showAiStatus(net.minecraft.server.command.ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("§6===== وضعیت AI ====="), false);
        source.sendFeedback(() -> Text.literal("§fفعال: §e" + aiConfig.isEnabled()), false);
        source.sendFeedback(() -> Text.literal("§fتنظیم شده: §e" + aiClient.isConfigured()), false);
        source.sendFeedback(() -> Text.literal("§fمدل: §e" + aiClient.model()), false);
        source.sendFeedback(() -> Text.literal("§fآدرس: §e" + aiConfig.baseUrl()), false);
        source.sendFeedback(() -> Text.literal("§fگفتگو: §e" + aiConfig.chatEnabled()), false);
        source.sendFeedback(() -> Text.literal("§fاسپان خودکار: §e" + aiConfig.autoSpawn()), false);
        source.sendFeedback(() -> Text.literal("§fغیرفعال‌ها: §e" + aiConfig.disabledActionsRaw()), false);
        source.sendFeedback(() -> Text.literal("§fفایل: §e" + AiConfig.getConfigPath()), false);
        source.sendFeedback(() -> Text.literal("§6===================="), false);
        return 1;
    }

    private static int reloadAiConfig(net.minecraft.server.command.ServerCommandSource source) {
        aiConfig = AiConfig.load();
        aiClient.reload(aiConfig);

        source.sendFeedback(
                () -> Text.literal(
                        "تنظیمات AI دوباره بارگذاری شد. آماده: " + aiClient.isConfigured()
                ),
                false
        );
        return 1;
    }

    private static int showStatus(
            ServerPlayerEntity player,
            net.minecraft.server.command.ServerCommandSource source
    ) {
        AiPlayerEntity robot = findOwnedRobot(player);
        if (robot == null) {
            source.sendError(Text.literal("No owned AI Player is nearby."));
            return 0;
        }

        var pos = robot.getBlockPos();
        var home = robot.getHomePos();
        String homeText = home == null
                ? "ثبت نشده"
                : home.getX() + ", " + home.getY() + ", " + home.getZ();

        source.sendFeedback(() -> Text.literal("§6===== وضعیت AI Player ====="), false);
        source.sendFeedback(() -> Text.literal("§fحالت: §e" + robot.getMode().name()), false);
        source.sendFeedback(() -> Text.literal("§fکار فعلی: §e" + robot.getTaskDescription()), false);
        source.sendFeedback(() -> Text.literal(String.format("§fسلامت: §e%.1f/%.1f", robot.getHealth(), robot.getMaxHealth())), false);
        source.sendFeedback(() -> Text.literal("§fموقعیت: §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
        source.sendFeedback(() -> Text.literal("§fخانه: §e" + homeText), false);
        source.sendFeedback(() -> Text.literal("§fفرمان‌های اجراشده: §e" + robot.getCommandsExecuted()), false);
        source.sendFeedback(() -> Text.literal("§fبلوک‌های شکسته‌شده: §e" + robot.getBlocksBroken()), false);
        source.sendFeedback(() -> Text.literal("§fبلوک‌های ساخته‌شده: §e" + robot.getBlocksPlaced()), false);
        source.sendFeedback(() -> Text.literal("§fحملات: §e" + robot.getAttacksMade()), false);
        source.sendFeedback(() -> Text.literal("§6==========================="), false);
        return 1;
    }

    private static AiPlayerEntity findOwnedRobot(ServerPlayerEntity player) {
        return player.getServerWorld()
                .getEntitiesByType(
                        ModEntities.AI_PLAYER,
                        entity -> player.getUuid().equals(entity.getOwnerUuid())
                                && entity.isAlive()
                                && !entity.isRemoved()
                )
                .stream()
                .min((a, b) -> Double.compare(player.squaredDistanceTo(a), player.squaredDistanceTo(b)))
                .orElse(null);
    }

    private static int setActionEnabled(
            net.minecraft.server.command.ServerCommandSource source,
            String rawAction,
            boolean enabled
    ) {
        String action = RobotCommandPlanner.normalizeAction(rawAction);
        if (action.isBlank()) {
            source.sendError(Text.literal("نام توانایی را وارد کن."));
            return 0;
        }

        aiConfig = AiConfig.load();
        if (enabled) {
            aiConfig.enableAction(action);
        } else {
            aiConfig.disableAction(action);
        }
        aiConfig.save();
        aiClient.reload(aiConfig);

        source.sendFeedback(
                () -> Text.literal(
                        enabled ? "توانایی فعال شد: " + action : "توانایی غیرفعال شد: " + action
                ),
                false
        );
        return 1;
    }

    private static void ensureTemporaryCompanion(ServerPlayerEntity player) {
        if (!aiConfig.autoSpawn() || !player.isAlive()) {
            return;
        }

        var server = player.getServer();
        if (server == null) {
            return;
        }

        AiPlayerEntity keeper = null;

        if (aiConfig.singleCompanion() && server.isSingleplayer()) {
            for (var world : server.getWorlds()) {
                for (AiPlayerEntity robot : world.getEntitiesByType(ModEntities.AI_PLAYER, entity -> true)) {
                    if (world == player.getServerWorld()
                            && keeper == null
                            && player.getUuid().equals(robot.getOwnerUuid())) {
                        keeper = robot;
                    } else {
                        robot.discard();
                    }
                }
            }
        } else {
            for (var world : server.getWorlds()) {
                for (AiPlayerEntity robot : world.getEntitiesByType(
                        ModEntities.AI_PLAYER,
                        entity -> player.getUuid().equals(entity.getOwnerUuid())
                )) {
                    if (world == player.getServerWorld() && keeper == null) {
                        keeper = robot;
                    } else {
                        robot.discard();
                    }
                }
            }
        }

        if (keeper != null && keeper.isAlive()) {
            keeper.setOwner(player);
            return;
        }

        AiPlayerEntity entity = ModEntities.AI_PLAYER.create(player.getServerWorld());
        if (entity == null) {
            return;
        }

        entity.refreshPositionAndAngles(
                player.getX() + 2.0,
                player.getY(),
                player.getZ() + 2.0,
                player.getYaw(),
                0.0f
        );
        entity.setCustomName(Text.literal("AI Player"));
        entity.setCustomNameVisible(true);
        entity.setOwner(player);
        player.getServerWorld().spawnEntity(entity);
    }

    private static void removeOwnedRobots(
            net.minecraft.server.MinecraftServer server,
            UUID ownerUuid
    ) {
        for (var world : server.getWorlds()) {
            for (AiPlayerEntity robot : world.getEntitiesByType(
                    ModEntities.AI_PLAYER,
                    entity -> ownerUuid.equals(entity.getOwnerUuid())
            )) {
                robot.discard();
            }
        }
    }

    private static int updateMod(net.minecraft.server.command.ServerCommandSource source) {
        Optional<Path> currentJar = getCurrentJar();
        if (currentJar.isEmpty()) {
            source.sendError(Text.literal("AI Player cannot locate its JAR file."));
            return 0;
        }

        source.sendFeedback(
                () -> Text.literal("Downloading the latest AI Player..."),
                false
        );

        Thread.ofVirtual().start(() -> {
            Path jar = currentJar.get();
            Path temp = jar.resolveSibling(jar.getFileName() + ".download");

            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .build();

                HttpRequest request = HttpRequest.newBuilder(LATEST_JAR)
                        .timeout(Duration.ofSeconds(60))
                        .header("User-Agent", "AI-Player-Updater")
                        .GET()
                        .build();

                HttpResponse<Path> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofFile(
                                temp,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING,
                                StandardOpenOption.WRITE
                        )
                );

                if (response.statusCode() != 200) {
                    Files.deleteIfExists(temp);
                    throw new IOException("GitHub returned HTTP " + response.statusCode());
                }

                long size = Files.size(temp);
                if (size < 10_000) {
                    Files.deleteIfExists(temp);
                    throw new IOException("Downloaded JAR looks invalid (" + size + " bytes).");
                }

                Files.move(
                        temp,
                        jar,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );

                cleanupOldCopies(jar);

                source.getServer().execute(() ->
                        source.sendFeedback(
                                () -> Text.literal(
                                        "AI Player updated successfully. Restart Minecraft to load the new JAR."
                                ),
                                false
                        )
                );
            } catch (Exception error) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }

                source.getServer().execute(() ->
                        source.sendError(
                                Text.literal("Update failed: " + error.getMessage())
                        )
                );
            }
        });

        return 1;
    }

    private static Optional<Path> getCurrentJar() {
        try {
            var location = AiPlayerMod.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation();

            Path path = Path.of(location.toURI()).toAbsolutePath().normalize();
            if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                return Optional.of(path);
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private static void cleanupOldCopies(Path currentJar) {
        Path modsDir = currentJar.getParent();
        if (modsDir == null) {
            return;
        }

        try (Stream<Path> files = Files.list(modsDir)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(currentJar))
                    .filter(path -> path.getFileName().toString().startsWith("ai-player"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }
}
