package com.github.bandithelps.client.renderers.entity;

import com.github.bandithelps.entities.BlackwhipSegmentEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Invisible hit-proxy renderer. Visuals are drawn by {@link BlackwhipChainEntityRenderer}.
 */
public class BlackwhipSegmentEntityRenderer extends EntityRenderer<BlackwhipSegmentEntity, EntityRenderState> {

    public BlackwhipSegmentEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        // Intentionally empty — segments are attack hitboxes only.
    }
}
