package com.hosseinasgari.aiplayer.client;

import com.hosseinasgari.aiplayer.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class AiPlayerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.AI_PLAYER, AiPlayerRenderer::new);
    }
}
