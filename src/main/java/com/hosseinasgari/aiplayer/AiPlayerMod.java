package com.hosseinasgari.aiplayer;

import com.hosseinasgari.aiplayer.entity.AiPlayerEntity;
import com.hosseinasgari.aiplayer.entity.ModEntities;
import com.hosseinasgari.aiplayer.entity.RobotCommandPlanner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Main Fabric entry point. The companion brain is local and deterministic.
 * No HTTP requests, hosted LLMs, API keys, or runtime model downloads are used.
 */
public final class AiPlayerMod implements ModInitializer {
    public static final String MOD_ID = "ai_player";
    private static AiConfig config;
    private static AiIntentClient brain;

    @Override
    public void onInitialize() {
        ModEntities.registerAttributes();
        config = AiConfig.load();
        brain = new AiIntentClient(config);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            if (config.autoSpawn()) ensureCompanion(handler.getPlayer());
        }));

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
                handleInstruction(sender, message.getContent().getString()));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("aiplayer")
                        .then(literal("hello").executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.literal("§b[AI Player] §fOffline companion brain is ready."), false);
                            return 1;
                        }))
                        .then(literal("help").executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.literal("§bAI Player commands: /aiplayer spawn, status, dismiss, ask <text>"), false);
                            ctx.getSource().sendFeedback(() -> Text.literal("You can also write naturally in chat: «دنبالم بیا»، «۱۶ تا چوب جمع کن»، «برام خونه بساز»."), false);
                            return 1;
                        }))
                        .then(literal("spawn").executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            AiPlayerEntity entity = ensureCompanion(player);
                            ctx.getSource().sendFeedback(() -> Text.literal("§b[AI Player] §fCompanion ready: " + entity.getTaskDescription()), false);
                            return 1;
                        }))
                        .then(literal("status").executes(ctx -> showStatus(ctx.getSource())))
                        .then(literal("dismiss").executes(ctx -> dismiss(ctx.getSource())))
                        .then(literal("ask").then(argument("instruction", greedyString()).executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            handleInstruction(player, getString(ctx, "instruction"));
                            return 1;
                        })))
                ));
    }

    private static void handleInstruction(ServerPlayerEntity player, String raw) {
        if (player == null || raw == null || raw.isBlank() || !config.chatEnabled()) return;
        String text = raw.trim();
        if (config.requirePrefix()) {
            String prefix = config.triggerPrefix();
            if (!text.toLowerCase(java.util.Locale.ROOT).startsWith(prefix.toLowerCase(java.util.Locale.ROOT))) return;
            text = text.substring(prefix.length()).trim();
        }

        Optional<AiIntentClient.Decision> result = brain.interpretOffline(text);
        if (result.isEmpty()) return;
        AiIntentClient.Decision decision = result.get();
        if (decision.plan() == null) {
            player.sendMessage(Text.literal("§b[AI Player] §f" + decision.reply()), false);
            return;
        }

        if (!config.isActionEnabled(decision.plan().mode().name())) {
            player.sendMessage(Text.literal("§e[AI Player] §fThis skill is disabled in the local configuration."), false);
            return;
        }
        AiPlayerEntity companion = findCompanion(player.getServer(), player.getUuid());
        if (companion == null) companion = ensureCompanion(player);
        companion.applyPlan(decision.plan());
        player.sendMessage(Text.literal("§b[AI Player] §f" + decision.reply()), false);
    }

    private static AiPlayerEntity ensureCompanion(ServerPlayerEntity player) {
        AiPlayerEntity found = findCompanion(player.getServer(), player.getUuid());
        if (found != null) return found;
        AiPlayerEntity created = new AiPlayerEntity(ModEntities.AI_PLAYER, player.getWorld());
        created.refreshPositionAndAngles(player.getX() + 2.0, player.getY(), player.getZ() + 2.0, player.getYaw(), 0.0f);
        created.setOwner(player);
        player.getWorld().spawnEntity(created);
        player.sendMessage(Text.literal("§b[AI Player] §fهمراه هوشمند آماده است. می‌توانی طبیعی با من صحبت کنی."), false);
        return created;
    }

    private static AiPlayerEntity findCompanion(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return null;
        AiPlayerEntity first = null;
        for (ServerWorld world : server.getWorlds()) {
            List<? extends AiPlayerEntity> all = world.getEntitiesByType(ModEntities.AI_PLAYER, e -> owner.equals(e.getOwnerUuid()));
            for (AiPlayerEntity entity : all) {
                if (first == null) first = entity;
                else entity.discard();
            }
        }
        return first;
    }

    private static int showStatus(ServerCommandSource source) {
        ServerPlayerEntity player;
        try { player = source.getPlayerOrThrow(); } catch (Exception ex) {
            source.sendError(Text.literal("Run this command as a player inside a world."));
            return 0;
        }
        AiPlayerEntity entity = findCompanion(player.getServer(), player.getUuid());
        if (entity == null) {
            source.sendFeedback(() -> Text.literal("§b[AI Player] §fNo companion is active. Use /aiplayer spawn."), false);
            return 0;
        }
        source.sendFeedback(() -> Text.literal("§b[AI Player] §fMode: " + entity.getMode() + " | Task: " + entity.getTaskDescription()), false);
        source.sendFeedback(() -> Text.literal("§7Brain: " + brain.model() + " | Online model/API: none"), false);
        return 1;
    }

    private static int dismiss(ServerCommandSource source) {
        ServerPlayerEntity player;
        try { player = source.getPlayerOrThrow(); } catch (Exception ex) {
            source.sendError(Text.literal("Run this command as a player inside a world."));
            return 0;
        }
        AiPlayerEntity entity = findCompanion(player.getServer(), player.getUuid());
        if (entity != null) entity.discard();
        source.sendFeedback(() -> Text.literal("§b[AI Player] §fCompanion dismissed."), false);
        return 1;
    }
}
