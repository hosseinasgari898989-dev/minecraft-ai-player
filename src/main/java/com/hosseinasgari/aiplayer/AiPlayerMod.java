package com.hosseinasgari.aiplayer;

import com.hosseinasgari.aiplayer.entity.AiPlayerEntity;
import com.hosseinasgari.aiplayer.entity.ModEntities;
import com.hosseinasgari.aiplayer.entity.RobotCommandPlanner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    @Override
    public void onInitialize() {
        ModEntities.registerAttributes();

        aiConfig = AiConfig.load();
        aiClient = new AiIntentClient(aiConfig);

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

                                                var world = player.getServerWorld();
                                                var entity = ModEntities.AI_PLAYER.create(world);
                                                if (entity == null) {
                                                    context.getSource().sendError(
                                                            Text.literal("Could not create the AI Player.")
                                                    );
                                                    return 0;
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
                                                entity.setPersistent();
                                                entity.setOwner(player);
                                                world.spawnEntity(entity);

                                                context.getSource().sendFeedback(
                                                        () -> Text.literal(
                                                                "AI Player spawned and linked to you. Try /aiplayer command follow me"
                                                        ),
                                                        false
                                                );
                                                return 1;
                                            })
                            )
                            .then(
                                    literal("command")
                                            .then(
                                                    argument("instruction", greedyString())
                                                            .executes(context -> applyInstruction(
                                                                    context.getSource().getPlayerOrThrow(),
                                                                    getString(context, "instruction"),
                                                                    context.getSource()
                                                            ))
                                            )
                            )
                            .then(
                                    literal("do")
                                            .then(
                                                    argument("instruction", greedyString())
                                                            .executes(context -> applyInstruction(
                                                                    context.getSource().getPlayerOrThrow(),
                                                                    getString(context, "instruction"),
                                                                    context.getSource()
                                                            ))
                                            )
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
                                    literal("follow")
                                            .executes(context -> applyInstruction(
                                                    context.getSource().getPlayerOrThrow(),
                                                    "follow me",
                                                    context.getSource()
                                            ))
                            )
                            .then(
                                    literal("protect")
                                            .executes(context -> applyInstruction(
                                                    context.getSource().getPlayerOrThrow(),
                                                    "protect me",
                                                    context.getSource()
                                            ))
                            )
                            .then(
                                    literal("guard")
                                            .executes(context -> applyInstruction(
                                                    context.getSource().getPlayerOrThrow(),
                                                    "guard here",
                                                    context.getSource()
                                            ))
                            )
                            .then(
                                    literal("patrol")
                                            .executes(context -> applyInstruction(
                                                    context.getSource().getPlayerOrThrow(),
                                                    "patrol",
                                                    context.getSource()
                                            ))
                            )
                            .then(
                                    literal("explore")
                                            .executes(context -> applyInstruction(
                                                    context.getSource().getPlayerOrThrow(),
                                                    "explore",
                                                    context.getSource()
                                            ))
                            )
                            .then(
                                    literal("stop")
                                            .executes(context -> applyInstruction(
                                                    context.getSource().getPlayerOrThrow(),
                                                    "stop",
                                                    context.getSource()
                                            ))
                            )
                            .then(
                                    literal("update")
                                            .executes(context -> updateMod(context.getSource()))
                            )
            );
        });
    }

    private static void handleNaturalLanguageChat(ServerPlayerEntity player, String rawMessage) {
        String instruction = extractBotInstruction(rawMessage);
        if (instruction == null || instruction.isBlank()) {
            return;
        }

        if (findOwnedRobot(player) == null) {
            player.sendMessage(Text.literal("🤖 اول رباتت را با /aiplayer spawn ظاهر کن."), false);
            return;
        }

        if (!aiClient.isConfigured()) {
            RobotCommandPlanner.Plan localPlan = RobotCommandPlanner.plan(instruction);
            if (localPlan == null) {
                player.sendMessage(
                        Text.literal("🤖 هوش مصنوعی هنوز تنظیم نشده؛ فایل config/ai-player.properties را تنظیم کن."),
                        false
                );
                return;
            }

            findOwnedRobot(player).applyPlan(localPlan);
            player.sendMessage(Text.literal("🤖 " + localPlan.description()), false);
            return;
        }

        if (!AI_REQUESTS_IN_FLIGHT.add(player.getUuid())) {
            player.sendMessage(Text.literal("🤖 هنوز دارم فرمان قبلی را تحلیل می‌کنم..."), false);
            return;
        }

        player.sendMessage(Text.literal("🤖 دارم فرمانت را بررسی می‌کنم..."), false);

        Thread.ofVirtual().start(() -> {
            try {
                Optional<RobotCommandPlanner.Plan> result = aiClient.classify(instruction);

                player.getServer().execute(() -> {
                    try {
                        if (result.isEmpty()) {
                            RobotCommandPlanner.Plan fallback = RobotCommandPlanner.plan(instruction);
                            if (fallback != null) {
                                applyPlanFromAi(player, fallback);
                            } else {
                                player.sendMessage(Text.literal("🤖 این کار را هنوز بلد نیستم."), false);
                            }
                            return;
                        }

                        applyPlanFromAi(player, result.get());
                    } finally {
                        AI_REQUESTS_IN_FLIGHT.remove(player.getUuid());
                    }
                });
            } catch (Exception error) {
                player.getServer().execute(() -> {
                    try {
                        RobotCommandPlanner.Plan fallback = RobotCommandPlanner.plan(instruction);
                        if (fallback != null) {
                            applyPlanFromAi(player, fallback);
                            player.sendMessage(
                                    Text.literal("🤖 اتصال AI خطا داد؛ از فرمان داخلی استفاده کردم."),
                                    false
                            );
                        } else {
                            player.sendMessage(
                                    Text.literal("🤖 اتصال به AI ناموفق بود. تنظیمات API را بررسی کن."),
                                    false
                            );
                        }
                    } finally {
                        AI_REQUESTS_IN_FLIGHT.remove(player.getUuid());
                    }
                });
            }
        });
    }

    private static String extractBotInstruction(String rawMessage) {
        if (rawMessage == null) {
            return null;
        }

        String input = rawMessage.trim();
        String[] prefixes = {
                aiConfig.triggerPrefix() + " ",
                aiConfig.triggerPrefix() + ":",
                "@" + aiConfig.triggerPrefix() + " ",
                "@" + aiConfig.triggerPrefix() + ":",
                "bot ",
                "bot:",
                "@bot ",
                "@bot:"
        };

        for (String prefix : prefixes) {
            if (input.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return input.substring(prefix.length()).trim();
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
        source.sendFeedback(() -> Text.literal("§6===== دستورهای AI Player ====="), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer spawn §f- ساختن ربات و وصل‌کردن آن به شما"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer follow §f- دنبال‌کردن شما"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer protect §f- محافظت از شما و حمله به دشمن‌ها"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer guard §f- نگهبانی از محل خانه"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer patrol §f- گشت‌زنی اطراف خانه"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer explore §f- کاوش و رفتن به نقاط مختلف"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer sethome §f- ذخیره محل فعلی به‌عنوان خانه ربات"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer stop §f- توقف کامل کار فعلی"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer status §f- نمایش وضعیت، سلامت و آمار ربات"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer aistatus §f- وضعیت اتصال هوش مصنوعی"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer aireload §f- بارگذاری دوباره تنظیمات AI"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer command <دستور> §f- دستور مستقیم طبیعی فارسی یا انگلیسی"), false);
        source.sendFeedback(() -> Text.literal("§7برای AI: §fربات برو چوب جمع کن"), false);
        source.sendFeedback(() -> Text.literal("§7برای AI: §fربات خونه بساز"), false);
        source.sendFeedback(() -> Text.literal("§7برای AI: §fربات دنبالم بیا"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer hello §f- تست نصب مود"), false);
        source.sendFeedback(() -> Text.literal("§e/aiplayer update §f- دریافت آخرین نسخه از GitHub"), false);
        source.sendFeedback(() -> Text.literal("§6================================"), false);
        return 1;
    }

    private static int showAiStatus(net.minecraft.server.command.ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("§6===== وضعیت AI ====="), false);
        source.sendFeedback(
                () -> Text.literal("§fفعال: §e" + aiConfig.isEnabled()),
                false
        );
        source.sendFeedback(
                () -> Text.literal("§fتنظیم شده: §e" + aiClient.isConfigured()),
                false
        );
        source.sendFeedback(
                () -> Text.literal("§fمدل: §e" + aiClient.model()),
                false
        );
        source.sendFeedback(
                () -> Text.literal("§fفایل تنظیمات: §e" + AiConfig.getConfigPath()),
                false
        );
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
                .getEntitiesByClass(
                        AiPlayerEntity.class,
                        player.getBoundingBox().expand(64.0),
                        entity -> player.getUuid().equals(entity.getOwnerUuid()) && entity.isAlive()
                )
                .stream()
                .min((a, b) -> Double.compare(player.squaredDistanceTo(a), player.squaredDistanceTo(b)))
                .orElse(null);
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
