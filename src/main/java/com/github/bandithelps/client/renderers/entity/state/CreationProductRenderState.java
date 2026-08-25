package com.github.bandithelps.client.renderers.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class CreationProductRenderState extends EntityRenderState {
    public final ItemStackRenderState itemModel = new ItemStackRenderState();
    public float scale = 1.0f;
    public float spin;
}
