package com.hosseinasgari.aiplayer;

import com.hosseinasgari.aiplayer.entity.ModEntities;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public class AiPlayerMod implements ModInitializer {
    public static final String MOD_ID = "ai_player";

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
            );
        });
    }
}
