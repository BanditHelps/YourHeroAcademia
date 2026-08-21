package com.github.bandithelps.client.renderers;

import com.github.bandithelps.YourHeroAcademia;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws Blackwhip ribbons after translucent terrain so the original alpha look composites
 * on top of water/ice instead of being covered by it (or covering it incorrectly).
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class BlackwhipRibbonLateRenderer {
    private record Command(PoseStack.Pose pose, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer renderer) {
    }

    private static final List<Command> QUEUE = new ArrayList<>();

    private BlackwhipRibbonLateRenderer() {
    }

    public static void queue(PoseStack poseStack, RenderType renderType,
                             SubmitNodeCollector.CustomGeometryRenderer renderer) {
        QUEUE.add(new Command(poseStack.last().copy(), renderType, renderer));
    }

    @SubscribeEvent
    public static void afterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        if (QUEUE.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderTarget translucent = event.getLevelRenderer().getTranslucentTarget();
        try {
            if (translucent != null) {
                RenderSystem.outputColorTextureOverride = translucent.getColorTextureView();
                RenderSystem.outputDepthTextureOverride = translucent.getDepthTextureView();
            }
            for (Command command : QUEUE) {
                command.renderer().render(command.pose(), buffers.getBuffer(command.renderType()));
            }
            buffers.endBatch();
        } finally {
            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
            QUEUE.clear();
        }
    }
}
