package com.github.bandithelps.client;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.effects.ModEffects;
import com.github.bandithelps.throwable.EffectBurstDetonation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class FlashbangOverlay {
    /** Last this many ticks ease from full white-out down to clear. */
    private static final int FADE_TICKS = 80;
    private static final float MAX_ALPHA = 0.92f;
    /** Peak overlay strength when fully turned away (still visible, not a white-out). */
    private static final float LOOK_AWAY_STRENGTH = 0.5f;
    private static final int LOOK_AWAY_AMPLIFIER_MAX = EffectBurstDetonation.LOOK_AWAY_AMPLIFIER_MAX;

    private FlashbangOverlay() {
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.CAMERA_OVERLAYS.equals(event.getName())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        MobEffectInstance effect = minecraft.player.getEffect(ModEffects.FLASHBANGED);
        if (effect == null) {
            return;
        }

        float intensity = overlayIntensity(effect);
        if (intensity <= 0.0f) {
            return;
        }

        int alpha = Math.round(255.0f * MAX_ALPHA * intensity);
        int color = (alpha << 24) | 0xFFFFFF;
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        graphics.fill(0, 0, width, height, color);
    }

    static float overlayIntensity(MobEffectInstance effect) {
        return overlayIntensity(effect.getDuration(), effect.getAmplifier());
    }

    static float overlayIntensity(int remainingTicks, int amplifier) {
        float timeFade;
        if (remainingTicks >= FADE_TICKS) {
            timeFade = 1.0f;
        } else {
            float linear = Mth.clamp(remainingTicks / (float) FADE_TICKS, 0.0f, 1.0f);
            // Ease-out: stay near full strength, then recede in the last stretch.
            float inv = 1.0f - linear;
            timeFade = 1.0f - inv * inv * inv;
        }
        float lookAway = Mth.clamp(amplifier / (float) LOOK_AWAY_AMPLIFIER_MAX, 0.0f, 1.0f);
        float facingStrength = Mth.lerp(lookAway, 1.0f, LOOK_AWAY_STRENGTH);
        return timeFade * facingStrength;
    }
}
