package com.github.bandithelps.client;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.effects.ModEffects;
import net.minecraft.client.Minecraft;
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
    private static final float FADED_FOG_END = 8.0F;

    private FlashbangFog() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        MobEffectInstance effect = getActiveFlashbang();
        if (effect == null) {
            return;
        }
        event.setRed(1.0F);
        event.setGreen(1.0F);
        event.setBlue(0.96F);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        MobEffectInstance effect = getActiveFlashbang();
        if (effect == null) {
            return;
        }

        float intensity = FlashbangOverlay.overlayIntensity(effect);
        float far = FULL_FOG_END + (FADED_FOG_END - FULL_FOG_END) * (1.0F - intensity);
        float near = Math.max(0.05F, far * 0.35F);

        event.setNearPlaneDistance(near);
        event.setFarPlaneDistance(far);

        var fog = event.getFogData();
        fog.environmentalStart = near;
        fog.environmentalEnd = far;
        fog.skyEnd = SKY_FOG_END;
        fog.cloudEnd = SKY_FOG_END;
    }

    private static MobEffectInstance getActiveFlashbang() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        return mc.player.getEffect(ModEffects.FLASHBANGED);
    }
}
