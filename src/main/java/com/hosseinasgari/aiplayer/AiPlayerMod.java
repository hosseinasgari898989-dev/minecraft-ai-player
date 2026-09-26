package com.hosseinasgari.aiplayer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public class AiPlayerMod implements ModInitializer {
    public static final String MOD_ID = "ai_player";

    @Override
    public void onInitialize() {
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
            );
        });
    }
}
