package com.hosseinasgari.aiplayer.client;

import com.hosseinasgari.aiplayer.entity.AiPlayerEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

public class AiPlayerRenderer extends BipedEntityRenderer<AiPlayerEntity, PlayerEntityModel<AiPlayerEntity>> {
    private static final Identifier TEXTURE =
            new Identifier("minecraft", "textures/entity/steve.png");

    public AiPlayerRenderer(EntityRendererFactory.Context context) {
        super(
                context,
                new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false),
                0.5f
        );
    }

    @Override
    public Identifier getTexture(AiPlayerEntity entity) {
        return TEXTURE;
    }
}
