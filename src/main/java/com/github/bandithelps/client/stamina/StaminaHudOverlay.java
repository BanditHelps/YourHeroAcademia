package com.github.bandithelps.client.stamina;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class StaminaHudOverlay {
    private static final int BAR_WIDTH = 96;
    private static final int BAR_HEIGHT = 10;
    private static final int LEFT_MARGIN = 10;
    private static final int BOTTOM_MARGIN = 18;
    private static final int DEBUG_TOP_MARGIN = 8;
    private static final int DEBUG_RIGHT_MARGIN = 8;
    private static final int DEBUG_LINE_HEIGHT = 10;

    private StaminaHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int x = LEFT_MARGIN;
        int y = screenHeight - BOTTOM_MARGIN - BAR_HEIGHT;

        int current = ClientStaminaState.getCurrentStamina();
        int max = Math.max(1, ClientStaminaState.getMaxStamina());
        float ratio = Mth.clamp((float) current / (float) max, 0.0f, 1.0f);
        int innerWidth = BAR_WIDTH - 2;
        int fillWidth = Math.round(innerWidth * ratio);

        GuiGraphics graphics = event.getGuiGraphics();
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xCC101018);
        graphics.fill(x + 1, y + 1, x + 1 + fillWidth, y + BAR_HEIGHT - 1, 0xFF2ECC71);
        graphics.fill(x, y, x + BAR_WIDTH, y + 1, 0xFF304050);
        graphics.fill(x, y + BAR_HEIGHT - 1, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF304050);
        graphics.fill(x, y, x + 1, y + BAR_HEIGHT, 0xFF304050);
        graphics.fill(x + BAR_WIDTH - 1, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF304050);

        String label = "STA " + current + "/" + max;
        graphics.drawString(minecraft.font, label, x, y - 10, 0xFFFFFFFF, true);

        if (!ClientStaminaState.isDebugOverlayEnabled()) {
            return;
        }

        String[] lines = new String[] {
                "YHA Stamina Debug",
                "currentStamina: " + ClientStaminaState.getCurrentStamina(),
                "maxStamina: " + ClientStaminaState.getMaxStamina(),
                "usageTotal: " + ClientStaminaState.getUsageTotal(),
                "regenCooldown: " + ClientStaminaState.getRegenCooldown(),
                "regenAmount: " + ClientStaminaState.getRegenAmount(),
                "exhaustionLevel: " + ClientStaminaState.getExhaustionLevel(),
                "lastHurrahUsed: " + ClientStaminaState.getLastHurrahUsed(),
                "powersDisabled: " + ClientStaminaState.isPowersDisabled(),
                "initialized: " + ClientStaminaState.isInitialized(),
                "upgradePoints: " + ClientStaminaState.getUpgradePoints(),
                "pointsProgress: " + ClientStaminaState.getPointsProgress()
        };

        int yPos = DEBUG_TOP_MARGIN;
        for (String line : lines) {
            int xPos = screenWidth - DEBUG_RIGHT_MARGIN - minecraft.font.width(line);
            graphics.drawString(minecraft.font, line, xPos, yPos, 0xFFFFFFFF, true);
            yPos += DEBUG_LINE_HEIGHT;
        }
    }
}
