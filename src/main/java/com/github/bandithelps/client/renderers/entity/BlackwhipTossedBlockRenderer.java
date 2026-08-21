package com.github.bandithelps.client.renderers.entity;

import com.github.bandithelps.client.renderers.entity.state.BlackwhipTossedBlockRenderState;
import com.github.bandithelps.entities.BlackwhipTossedBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class BlackwhipTossedBlockRenderer
        extends EntityRenderer<BlackwhipTossedBlockEntity, BlackwhipTossedBlockRenderState> {

    public BlackwhipTossedBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    protected boolean affectedByCulling(BlackwhipTossedBlockEntity entity) {
        return !entity.isHovering();
    }

    @Override
    public void submit(BlackwhipTossedBlockRenderState state, PoseStack poseStack,
                      SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        BlockState blockState = state.movingBlockRenderState.blockState;
        if (blockState.getRenderShape() == RenderShape.MODEL) {
            poseStack.pushPose();
            if (state.hovering) {
                poseStack.translate(state.hoverDx, state.hoverDy, state.hoverDz);
                poseStack.translate(-0.5, -0.5, -0.5);
            } else {
                poseStack.translate(-0.5, 0.0, -0.5);
            }
            submitNodeCollector.submitMovingBlock(poseStack, state.movingBlockRenderState);
            poseStack.popPose();
            super.submit(state, poseStack, submitNodeCollector, camera);
        }
    }

    @Override
    public BlackwhipTossedBlockRenderState createRenderState() {
        return new BlackwhipTossedBlockRenderState();
    }

    @Override
    public void extractRenderState(BlackwhipTossedBlockEntity entity, BlackwhipTossedBlockRenderState state,
                                   float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.hovering = entity.isHovering();
        state.hoverDx = 0.0;
        state.hoverDy = 0.0;
        state.hoverDz = 0.0;
        Vec3 visual = state.hovering ? entity.hoverVisualCenter(partialTicks) : null;
        if (visual != null && state.hovering) {
            state.hoverDx = visual.x - state.x;
            state.hoverDy = visual.y - state.y;
            state.hoverDz = visual.z - state.z;
        }
        double px = visual != null ? visual.x : entity.getX();
        double py = visual != null ? visual.y : entity.getBoundingBox().maxY;
        double pz = visual != null ? visual.z : entity.getZ();
        BlockPos pos = BlockPos.containing(px, py, pz);
        state.movingBlockRenderState.randomSeedPos = pos;
        state.movingBlockRenderState.blockPos = pos;
        state.movingBlockRenderState.blockState = entity.getBlockState();
        if (entity.level() instanceof ClientLevel clientLevel) {
            state.movingBlockRenderState.biome = clientLevel.getBiome(pos);
            state.movingBlockRenderState.cardinalLighting = clientLevel.cardinalLighting();
            state.movingBlockRenderState.lightEngine = clientLevel.getLightEngine();
        }
    }
}
