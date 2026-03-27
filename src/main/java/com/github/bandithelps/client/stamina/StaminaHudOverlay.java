package com.github.bandithelps.client.stamina;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.capabilities.body.BodyDisplayBar;
import com.github.bandithelps.capabilities.body.BodyDisplayBarType;
import com.github.bandithelps.client.body.ClientBodyState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class StaminaHudOverlay {
    private static final int BAR_WIDTH = 144;
    private static final int BAR_HEIGHT = 14;
    private static final int LEFT_MARGIN = 10;
    private static final int BOTTOM_MARGIN = 5;

    private static final int BODY_BAR_WIDTH = 72;
    private static final int BODY_BAR_HEIGHT = 6;
    private static final int BODY_LEFT_MARGIN = 82;
    private static final int STACK_GAP = 1;
    private static final int BODY_ICON_SIZE = 6;
    private static final int BODY_ICON_GAP = 0;
    private static final String BODY_ICON_TEXTURE_FOLDER = "textures/gui/body_bars/";

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
        renderBodyDisplayBars(graphics, minecraft, BODY_LEFT_MARGIN, y - BODY_BAR_HEIGHT - STACK_GAP);

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
                "pointsProgress: " + ClientStaminaState.getPointsProgress(),
                "upgradeProgressCooldown: " + ClientStaminaState.getUpgradeProgressCooldown()
        };

        int yPos = DEBUG_TOP_MARGIN;
        for (String line : lines) {
            int xPos = screenWidth - DEBUG_RIGHT_MARGIN - minecraft.font.width(line);
            graphics.drawString(minecraft.font, line, xPos, yPos, 0xFFFFFFFF, true);
            yPos += DEBUG_LINE_HEIGHT;
        }
    }

    private static void renderBodyDisplayBars(GuiGraphics graphics, Minecraft minecraft, int x, int startY) {
        int y = startY;
        for (BodyDisplayBar displayBar : ClientBodyState.getDisplayBars().values()) {
            float currentValue = ClientBodyState.getCustomFloat(displayBar.part(), displayBar.key(), displayBar.minValue());
            float ratio = getRatio(displayBar, currentValue);

            drawBarFrame(graphics, x, y);
            if (displayBar.type() == BodyDisplayBarType.SLIDER) {
                drawSlider(graphics, x, y, ratio, displayBar);
            } else {
                drawFillBar(graphics, x, y, ratio, displayBar);
            }
            renderBarIconIfPresent(graphics, minecraft, displayBar.id(), x, y);

            if (ClientStaminaState.isDebugOverlayEnabled()) {
                String text = displayBar.label() + " " + String.format("%.1f", currentValue);
                graphics.drawString(minecraft.font, text, x + 2, y + 1, 0xFFFFFFFF, false);
            }

            y -= BODY_BAR_HEIGHT + STACK_GAP;
        }
    }

    private static float getRatio(BodyDisplayBar displayBar, float value) {
        float range = Math.max(0.0001F, displayBar.maxValue() - displayBar.minValue());
        return Mth.clamp((value - displayBar.minValue()) / range, 0.0F, 1.0F);
    }

    private static void drawFillBar(GuiGraphics graphics, int x, int y, float ratio, BodyDisplayBar displayBar) {
        int innerWidth = BODY_BAR_WIDTH - 2;
        int fillWidth = Math.round(innerWidth * ratio);
        if (fillWidth <= 0) {
            return;
        }
        drawHorizontalGradient(
                graphics,
                x + 1,
                y + 1,
                fillWidth,
                BODY_BAR_HEIGHT - 2,
                displayBar.gradientLeftColorRgb(),
                displayBar.gradientRightColorRgb()
        );
    }

    private static void drawSlider(GuiGraphics graphics, int x, int y, float ratio, BodyDisplayBar displayBar) {
        int innerWidth = BODY_BAR_WIDTH - 2;
        int innerX = x + 1;

        int gradientTop = y + 1;
        int gradientBottom = y + (BODY_BAR_HEIGHT > 2 ? BODY_BAR_HEIGHT - 1 : BODY_BAR_HEIGHT);
        if (gradientBottom > gradientTop) {
            drawHorizontalGradient(
                    graphics,
                    innerX,
                    gradientTop,
                    innerWidth,
                    gradientBottom - gradientTop,
                    displayBar.gradientLeftColorRgb(),
                    displayBar.gradientRightColorRgb()
            );
        }

        int markerHalfWidth = 1;
        int markerCenter = innerX + Math.round(innerWidth * ratio);
        int markerColor = 0xFF000000 | displayBar.sliderColorRgb();
        graphics.fill(
                markerCenter - markerHalfWidth,
                y + 1,
                markerCenter + markerHalfWidth + 1,
                y + BODY_BAR_HEIGHT - 1,
                markerColor
        );
    }

    private static void drawHorizontalGradient(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int leftRgb,
            int rightRgb
    ) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (width == 1) {
            graphics.fill(x, y, x + 1, y + height, 0xFF000000 | leftRgb);
            return;
        }

        for (int i = 0; i < width; i++) {
            float t = (float) i / (float) (width - 1);
            int gradientColor = 0xFF000000 | lerpRgb(leftRgb, rightRgb, t);
            graphics.fill(x + i, y, x + i + 1, y + height, gradientColor);
        }
    }

    private static int lerpRgb(int leftRgb, int rightRgb, float t) {
        int lr = (leftRgb >> 16) & 0xFF;
        int lg = (leftRgb >> 8) & 0xFF;
        int lb = leftRgb & 0xFF;
        int rr = (rightRgb >> 16) & 0xFF;
        int rg = (rightRgb >> 8) & 0xFF;
        int rb = rightRgb & 0xFF;

        int r = Mth.floor(Mth.lerp(t, lr, rr));
        int g = Mth.floor(Mth.lerp(t, lg, rg));
        int b = Mth.floor(Mth.lerp(t, lb, rb));
        return (r << 16) | (g << 8) | b;
    }

    private static void drawBarFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + BODY_BAR_WIDTH, y + BODY_BAR_HEIGHT, 0xCC101018);
        graphics.fill(x, y, x + BODY_BAR_WIDTH, y + 1, 0xFF304050);
        graphics.fill(x, y + BODY_BAR_HEIGHT - 1, x + BODY_BAR_WIDTH, y + BODY_BAR_HEIGHT, 0xFF304050);
        graphics.fill(x, y, x + 1, y + BODY_BAR_HEIGHT, 0xFF304050);
        graphics.fill(x + BODY_BAR_WIDTH - 1, y, x + BODY_BAR_WIDTH, y + BODY_BAR_HEIGHT, 0xFF304050);
    }

    private static void renderBarIconIfPresent(GuiGraphics graphics, Minecraft minecraft, String displayBarId, int barX, int barY) {
        Identifier iconTexture = getIconTexture(displayBarId);
        if (minecraft.getResourceManager().getResource(iconTexture).isEmpty()) {
            return;
        }

        int iconX = barX - BODY_ICON_SIZE - BODY_ICON_GAP;
        int iconY = barY + (BODY_BAR_HEIGHT - BODY_ICON_SIZE) / 2;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                iconTexture,
                iconX,
                iconY,
                0.0F,
                0.0F,
                BODY_ICON_SIZE,
                BODY_ICON_SIZE,
                BODY_ICON_SIZE,
                BODY_ICON_SIZE
        );
    }

    private static Identifier getIconTexture(String displayBarId) {
        return Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, BODY_ICON_TEXTURE_FOLDER + displayBarId + ".png");
    }
}
