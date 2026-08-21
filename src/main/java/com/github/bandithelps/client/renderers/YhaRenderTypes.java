package com.github.bandithelps.client.renderers;

import java.util.function.Function;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Original Blackwhip blend modes (soft vertex alpha). Geometry is flushed after water/ice
 * by {@link BlackwhipRibbonLateRenderer} so these types do not fight translucent terrain.
 */
public final class YhaRenderTypes {
    private YhaRenderTypes() {
    }

    private static final Function<Identifier, RenderType> ENTITY_TRANSLUCENT = Util.memoize(
            texture -> RenderType.create(
                    "yha_entity_translucent",
                    RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
                            .withTexture("Sampler0", texture)
                            .useLightmap()
                            .useOverlay()
                            .bufferSize(RenderType.SMALL_BUFFER_SIZE)
                            .createRenderSetup()));

    private static final Function<Identifier, RenderType> ENTITY_TRANSLUCENT_EMISSIVE = Util.memoize(
            texture -> RenderType.create(
                    "yha_entity_translucent_emissive",
                    RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE)
                            .withTexture("Sampler0", texture)
                            .useOverlay()
                            .bufferSize(RenderType.SMALL_BUFFER_SIZE)
                            .createRenderSetup()));

    public static RenderType entityTranslucent(Identifier texture) {
        return ENTITY_TRANSLUCENT.apply(texture);
    }

    public static RenderType entityTranslucentEmissive(Identifier texture) {
        return ENTITY_TRANSLUCENT_EMISSIVE.apply(texture);
    }
}
