package com.github.bandithelps.client.renderers.entity;

import com.github.bandithelps.throwable.ThrownWeaponEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class ThrownWeaponRenderer extends ThrownItemRenderer<ThrownWeaponEntity> {
    public ThrownWeaponRenderer(EntityRendererProvider.Context context) {
        super(context, 1.0f, true);
    }
}
