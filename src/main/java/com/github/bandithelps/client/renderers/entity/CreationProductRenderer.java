package com.github.bandithelps.client.renderers.entity;

import com.github.bandithelps.client.renderers.entity.state.CreationProductRenderState;
import com.github.bandithelps.entities.CreationProductEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class CreationProductRenderer extends EntityRenderer<CreationProductEntity, CreationProductRenderState> {
    private final ItemModelResolver itemModelResolver;

    public CreationProductRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
        this.shadowRadius = 0.0f;
    }

    @Override
    protected boolean affectedByCulling(CreationProductEntity entity) {
        return false;
    }

    @Override
    public CreationProductRenderState createRenderState() {
        return new CreationProductRenderState();
    }

    @Override
    public void extractRenderState(CreationProductEntity entity, CreationProductRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        Vec3 visual = entity.visualPos(partialTick);
        state.x = visual.x;
        state.y = visual.y;
        state.z = visual.z;

        float progress = entity.growProgress(partialTick);
        state.scale = entity.growScale(partialTick);
        state.wobble = Mth.sin((entity.getAge() + partialTick) * 0.8f) * 6.0f * (1.0f - progress);
        Vec3 normal = entity.outwardNormal(partialTick);
        state.normalX = normal.x;
        state.normalY = normal.y;
        state.normalZ = normal.z;
        state.yaw = (float) (Mth.atan2(-normal.x, normal.z) * Mth.RAD_TO_DEG);

        ItemStack stack = entity.getItem();
        BlockState blockState = CreationProductEntity.growingBlockState(stack);
        state.renderBlock = CreationProductEntity.rendersAsGrowingBlock(stack);
        if (state.renderBlock) {
            state.itemModel.clear();
            state.emerge = 0.5f;
            BlockPos pos = BlockPos.containing(visual.x, visual.y, visual.z);
            state.movingBlockRenderState.randomSeedPos = pos;
            state.movingBlockRenderState.blockPos = pos;
            state.movingBlockRenderState.blockState = blockState;
            if (entity.level() instanceof ClientLevel clientLevel) {
                state.movingBlockRenderState.biome = clientLevel.getBiome(pos);
                state.movingBlockRenderState.cardinalLighting = clientLevel.cardinalLighting();
                state.movingBlockRenderState.lightEngine = clientLevel.getLightEngine();
            }
        } else {
            state.emerge = 0.35f;
            if (!stack.isEmpty()) {
                this.itemModelResolver.updateForNonLiving(state.itemModel, stack, ItemDisplayContext.FIXED, entity);
            } else {
                state.itemModel.clear();
            }
        }
    }

    @Override
    public void submit(CreationProductRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        boolean block = state.renderBlock
                && state.movingBlockRenderState.blockState.getRenderShape() == RenderShape.MODEL;
        if (!block && state.itemModel.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(
                state.normalX * state.scale * state.emerge,
                state.normalY * state.scale * state.emerge,
                state.normalZ * state.scale * state.emerge
        );
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
        if (block) {
            poseStack.mulPose(Axis.XP.rotationDegrees(state.wobble));
            poseStack.scale(state.scale, state.scale, state.scale);
            poseStack.translate(-0.5, -0.5, -0.5);
            collector.submitMovingBlock(poseStack, state.movingBlockRenderState);
        } else {
            // Tip the sprite so its long axis points outward (slides out of the body).
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0f + state.wobble));
            poseStack.scale(state.scale, state.scale, state.scale);
            state.itemModel.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}

