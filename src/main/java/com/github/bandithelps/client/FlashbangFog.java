package com.github.bandithelps.client;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.effects.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class FlashbangFog {
    private static final float SKY_FOG_END = 0.15F;
    private static final float FULL_FOG_END = 0.45F;
    private static final float FULL_FOG_START = 0.05F;

    private FlashbangFog() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        MobEffectInstance effect = getActiveFlashbang();
        if (effect == null) {
            return;
        }
        float intensity = FlashbangOverlay.overlayIntensity(effect);
        event.setRed(Mth.lerp(intensity, event.getRed(), 1.0F));
        event.setGreen(Mth.lerp(intensity, event.getGreen(), 1.0F));
        event.setBlue(Mth.lerp(intensity, event.getBlue(), 0.96F));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        MobEffectInstance effect = getActiveFlashbang();
        if (effect == null) {
            return;
        }

        float intensity = FlashbangOverlay.overlayIntensity(effect);
        if (intensity <= 0.0F) {
            return;
        }

        var fog = event.getFogData();
        // Blend toward vanilla distances (already in FogData) so the white haze recedes
        // all the way to the horizon, matching darkness, instead of stopping a few blocks out.
        float near = Mth.lerp(intensity, fog.environmentalStart, FULL_FOG_START);
        float far = Mth.lerp(intensity, fog.environmentalEnd, FULL_FOG_END);
        if (far < near) {
            far = near;
        }

        event.setNearPlaneDistance(near);
        event.setFarPlaneDistance(far);

        fog.environmentalStart = near;
        fog.environmentalEnd = far;
        fog.renderDistanceStart = Mth.lerp(intensity, fog.renderDistanceStart, FULL_FOG_START);
        fog.renderDistanceEnd = Mth.lerp(intensity, fog.renderDistanceEnd, FULL_FOG_END);
        fog.skyEnd = Mth.lerp(intensity, fog.skyEnd, SKY_FOG_END);
        fog.cloudEnd = Mth.lerp(intensity, fog.cloudEnd, SKY_FOG_END);
    }

    private static MobEffectInstance getActiveFlashbang() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        return mc.player.getEffect(ModEffects.FLASHBANGED);
    }
}
