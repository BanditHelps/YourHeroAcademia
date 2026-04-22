package com.github.bandithelps.client.renderers.entity;

import com.github.bandithelps.entities.SmokeCanisterProjectileEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class SmokeCanisterProjectileRenderer extends ThrownItemRenderer<SmokeCanisterProjectileEntity> {
    public SmokeCanisterProjectileRenderer(EntityRendererProvider.Context context) {
        super(context, 1.0f, true);
    }
}
