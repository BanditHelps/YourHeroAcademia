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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        MobEffectInstance smokeBlindEffect = getActiveSmokeBlindEffect();
        if (smokeBlindEffect == null) return;

        event.setRed(0.5411F);
        event.setGreen(0.1686f);
        event.setBlue(0.8862F);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        MobEffectInstance smokeBlindEffect = getActiveSmokeBlindEffect();
        if (smokeBlindEffect == null) return;

        float baseRange = 4.0f - smokeBlindEffect.getAmplifier() * 1.5f;
        float far = Math.max(3.0F, baseRange);
        event.setNearPlaneDistance(0.25f);
        event.setFarPlaneDistance(far);
    }

    private static MobEffectInstance getActiveSmokeBlindEffect() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        return mc.player.getEffect(ModEffects.SMOKE_BLIND);
    }

}
