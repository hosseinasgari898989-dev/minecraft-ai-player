package com.hosseinasgari.aiplayer;

import com.hosseinasgari.aiplayer.entity.ModEntities;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
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
import java.util.stream.Stream;

import static net.minecraft.server.command.CommandManager.literal;

public class AiPlayerMod implements ModInitializer {
    public static final String MOD_ID = "ai_player";
    private static final URI LATEST_JAR = URI.create(
            "https://raw.githubusercontent.com/hosseinasgari898989-dev/minecraft-ai-player/main/latest/ai-player.jar"
    );

    @Override
    public void onInitialize() {
        ModEntities.registerAttributes();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    literal("aiplayer")
                            .then(
                                    literal("hello")
                                            .executes(context -> {
                                                context.getSource().sendFeedback(
                                                        () -> Text.literal("AI Player v0.1 is installed and ready."),
                                                        false
                                                );
                                                return 1;
                                            })
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
                                                world.spawnEntity(entity);

                                                context.getSource().sendFeedback(
                                                        () -> Text.literal("AI Player spawned."),
                                                        false
                                                );
                                                return 1;
                                            })
                            )
                            .then(
                                    literal("update")
                                            .executes(context -> updateMod(context.getSource()))
                            )
            );
        });
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
