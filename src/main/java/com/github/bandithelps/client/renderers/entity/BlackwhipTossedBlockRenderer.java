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

public class BlackwhipTossedBlockRenderer
        extends EntityRenderer<BlackwhipTossedBlockEntity, BlackwhipTossedBlockRenderState> {

    public BlackwhipTossedBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5f;
    }

    @Override
    public void submit(BlackwhipTossedBlockRenderState state, PoseStack poseStack,
                      SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        BlockState blockState = state.movingBlockRenderState.blockState;
        if (blockState.getRenderShape() == RenderShape.MODEL) {
            poseStack.pushPose();
            poseStack.translate(-0.5, 0.0, -0.5);
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
        BlockPos pos = BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
        state.movingBlockRenderState.randomSeedPos = entity.blockPosition();
        state.movingBlockRenderState.blockPos = pos;
        state.movingBlockRenderState.blockState = entity.getBlockState();
        if (entity.level() instanceof ClientLevel clientLevel) {
            state.movingBlockRenderState.biome = clientLevel.getBiome(pos);
            state.movingBlockRenderState.cardinalLighting = clientLevel.cardinalLighting();
            state.movingBlockRenderState.lightEngine = clientLevel.getLightEngine();
        }
    }
}
