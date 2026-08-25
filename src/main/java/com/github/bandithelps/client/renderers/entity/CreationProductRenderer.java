package com.github.bandithelps.client.renderers.entity;

import com.github.bandithelps.client.renderers.entity.state.CreationProductRenderState;
import com.github.bandithelps.entities.CreationProductEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class CreationProductRenderer extends EntityRenderer<CreationProductEntity, CreationProductRenderState> {
    private final ItemModelResolver itemModelResolver;

    public CreationProductRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
        this.shadowRadius = 0.0f;
    }

    @Override
    public CreationProductRenderState createRenderState() {
        return new CreationProductRenderState();
    }

    @Override
    public void extractRenderState(CreationProductEntity entity, CreationProductRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        ItemStack stack = entity.getItem();
        state.scale = entity.growScale(partialTick);
        state.spin = (entity.tickCount + partialTick) * 8.0f;
        if (!stack.isEmpty()) {
            this.itemModelResolver.updateForNonLiving(state.itemModel, stack, ItemDisplayContext.GROUND, entity);
        } else {
            state.itemModel.clear();
        }
    }

    @Override
    public void submit(CreationProductRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.itemModel.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.scale(state.scale, state.scale, state.scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.spin));
        state.itemModel.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
