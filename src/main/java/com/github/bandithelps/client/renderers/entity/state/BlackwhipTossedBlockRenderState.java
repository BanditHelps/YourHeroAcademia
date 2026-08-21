package com.github.bandithelps.client.renderers.entity.state;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class BlackwhipTossedBlockRenderState extends EntityRenderState {
    public final MovingBlockRenderState movingBlockRenderState = new MovingBlockRenderState();
    public boolean hovering;
    public double hoverDx;
    public double hoverDy;
    public double hoverDz;
}
