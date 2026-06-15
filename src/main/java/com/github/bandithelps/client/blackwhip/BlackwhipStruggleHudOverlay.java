package com.github.bandithelps.client.blackwhip;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Center-screen "break free" prompt + progress bar shown while the local player is restrained.
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class BlackwhipStruggleHudOverlay {

    private static final int BAR_WIDTH = 160;
    private static final int BAR_HEIGHT = 12;

    private BlackwhipStruggleHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.CHAT.equals(event.getName())) {
            return;
        }
        if (!ClientBlackwhipStruggleState.isActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int x = (screenWidth - BAR_WIDTH) / 2;
        int y = screenHeight / 2 + 24;

        float progress = ClientBlackwhipStruggleState.getProgress();
        int innerWidth = BAR_WIDTH - 2;
        int fillWidth = Math.round(innerWidth * Mth.clamp(progress, 0f, 1f));

        GuiGraphicsExtractor graphics = event.getGuiGraphics();

        String prompt = "BREAK FREE! - mash JUMP";
        int textX = (screenWidth - minecraft.font.width(prompt)) / 2;
        graphics.text(minecraft.font, prompt, textX, y - 12, 0xFF2BE5C0, true);

        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xCC101018);
        // Color shifts from teal toward white as the bar fills.
        int fillColor = lerpColor(0xFF1FA98C, 0xFFEFFFFA, progress);
        graphics.fill(x + 1, y + 1, x + 1 + fillWidth, y + BAR_HEIGHT - 1, fillColor);
        graphics.fill(x, y, x + BAR_WIDTH, y + 1, 0xFF304050);
        graphics.fill(x, y + BAR_HEIGHT - 1, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF304050);
        graphics.fill(x, y, x + 1, y + BAR_HEIGHT, 0xFF304050);
        graphics.fill(x + BAR_WIDTH - 1, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF304050);
    }

    private static int lerpColor(int from, int to, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int a = (int) Mth.lerp(t, (from >> 24) & 0xFF, (to >> 24) & 0xFF);
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
