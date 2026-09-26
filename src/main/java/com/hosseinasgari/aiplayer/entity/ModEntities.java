package com.hosseinasgari.aiplayer.entity;

import com.hosseinasgari.aiplayer.AiPlayerMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
    public static final EntityType<AiPlayerEntity> AI_PLAYER = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(AiPlayerMod.MOD_ID, "ai_player"),
            EntityType.Builder.create(AiPlayerEntity::new, SpawnGroup.CREATURE)
                    .dimensions(0.6f, 1.8f)
                    .build("ai_player")
    );

    private ModEntities() {
    }

    public static void registerAttributes() {
        FabricDefaultAttributeRegistry.register(AI_PLAYER, AiPlayerEntity.createAiPlayerAttributes());
    }
}
