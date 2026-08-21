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
public class SmokeFog {
    private static final float SKY_FOG_END = 0.2F;
    private static final float MIN_GROUND_FOG_END = 0.35F;
    private static final float BASE_GROUND_FOG_END = 4.1F;
    private static final float GROUND_FOG_PER_LEVEL = 0.5F;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        MobEffectInstance smokeBlindEffect = getActiveSmokeBlindEffect();
        if (smokeBlindEffect == null) return;

        event.setRed(0.792F);
        event.setGreen(0.133f);
        event.setBlue(0.839F);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        MobEffectInstance smokeBlindEffect = getActiveSmokeBlindEffect();
        if (smokeBlindEffect == null) return;

        int amplifier = smokeBlindEffect.getAmplifier();
        float far = Math.max(MIN_GROUND_FOG_END, BASE_GROUND_FOG_END - amplifier * GROUND_FOG_PER_LEVEL);
        float near = Math.max(0.05F, far * 0.55F);

        event.setNearPlaneDistance(near);
        event.setFarPlaneDistance(far);

        // In 26.1, sky and clouds are controlled by dedicated fog channels.
        var fog = event.getFogData();
        fog.environmentalStart = near;
        fog.environmentalEnd = far;
        fog.skyEnd = SKY_FOG_END;
        fog.cloudEnd = SKY_FOG_END;
    }

    private static MobEffectInstance getActiveSmokeBlindEffect() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        return mc.player.getEffect(ModEffects.SMOKE_BLIND);
    }

}
