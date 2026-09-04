package com.github.bandithelps.client.renderers.entity.state;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class CreationProductRenderState extends EntityRenderState {
    public final ItemStackRenderState itemModel = new ItemStackRenderState();
    public final MovingBlockRenderState movingBlockRenderState = new MovingBlockRenderState();
    public boolean renderBlock;
    public float scale = 1.0f;
    public float emerge;
    public float wobble;
    public float yaw;
    public double normalX;
    public double normalY;
    public double normalZ;
}
