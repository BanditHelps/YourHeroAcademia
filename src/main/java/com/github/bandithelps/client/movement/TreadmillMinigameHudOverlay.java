package com.github.bandithelps.client.movement;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class TreadmillMinigameHudOverlay {
    private static final int KEY_SIZE = 18;
    private static final int KEY_GAP = 4;
    private static final int BOTTOM_MARGIN = 50;
    private static final int PANEL_PADDING = 6;
    private static final String KEY_TEXTURE_FOLDER = "textures/gui/treadmill/keys/";
    private static final String[] KEY_LABELS = {"W", "A", "S", "D"};
    private static final Identifier[] KEY_TEXTURES = new Identifier[] {
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, KEY_TEXTURE_FOLDER + "key_w.png"),
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, KEY_TEXTURE_FOLDER + "key_a.png"),
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, KEY_TEXTURE_FOLDER + "key_s.png"),
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, KEY_TEXTURE_FOLDER + "key_d.png")
    };

    private TreadmillMinigameHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.CHAT.equals(event.getName())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        if (!ClientTreadmillState.isMounted() || !ClientTreadmillState.isMinigameActive()) {
            return;
        }

        int sequenceLength = ClientTreadmillState.getSequenceLength();
        if (sequenceLength <= 0) {
            return;
        }

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int rowWidth = sequenceLength * KEY_SIZE + (sequenceLength - 1) * KEY_GAP;
        int panelWidth = rowWidth + (PANEL_PADDING * 2);
        int panelHeight = KEY_SIZE + (PANEL_PADDING * 2);
        int panelX = (screenWidth - panelWidth) / 2;
        int panelY = screenHeight - BOTTOM_MARGIN - panelHeight;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xAA101018);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF304050);
        graphics.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, 0xFF304050);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, 0xFF304050);
        graphics.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF304050);

        int progress = ClientTreadmillState.getProgressIndex();
        int keyX = panelX + PANEL_PADDING;
        int keyY = panelY + PANEL_PADDING;
        for (int i = 0; i < sequenceLength; i++) {
            int keyIndex = ClientTreadmillState.getSequenceKeyAt(i);
            drawKey(graphics, minecraft, keyIndex, keyX, keyY);

            if (i < progress) {
                graphics.fill(keyX, keyY, keyX + KEY_SIZE, keyY + KEY_SIZE, 0x9900CC66);
            } else if (i == progress) {
                graphics.fill(keyX, keyY, keyX + KEY_SIZE, keyY + KEY_SIZE, 0x667EC8FF);
            }

            keyX += KEY_SIZE + KEY_GAP;
        }

        // Ceil to whole seconds so the timer does not look shorter than the server window.
        int remTicks = Math.max(0, ClientTreadmillState.getRemainingTicks());
        int remainingSeconds = (remTicks + 19) / 20;
        String prompt = "Match the sequence! " + remainingSeconds + "s";
        int promptX = (screenWidth - minecraft.font.width(prompt)) / 2;
        graphics.text(minecraft.font, prompt, promptX, panelY - 10, 0xFFFFFFFF, true);
    }

    private static void drawKey(GuiGraphicsExtractor graphics, Minecraft minecraft, int keyIndex, int x, int y) {
        if (keyIndex >= 0 && keyIndex < KEY_TEXTURES.length && minecraft.getResourceManager().getResource(KEY_TEXTURES[keyIndex]).isPresent()) {
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    KEY_TEXTURES[keyIndex],
                    x,
                    y,
                    0.0F,
                    0.0F,
                    KEY_SIZE,
                    KEY_SIZE,
                    KEY_SIZE,
                    KEY_SIZE
            );
            return;
        }

        graphics.fill(x, y, x + KEY_SIZE, y + KEY_SIZE, 0xFF2E2E35);
        graphics.fill(x, y, x + KEY_SIZE, y + 1, 0xFF606070);
        graphics.fill(x, y + KEY_SIZE - 1, x + KEY_SIZE, y + KEY_SIZE, 0xFF606070);
        graphics.fill(x, y, x + 1, y + KEY_SIZE, 0xFF606070);
        graphics.fill(x + KEY_SIZE - 1, y, x + KEY_SIZE, y + KEY_SIZE, 0xFF606070);

        String label = keyIndex >= 0 && keyIndex < KEY_LABELS.length ? KEY_LABELS[keyIndex] : "?";
        int labelX = x + (KEY_SIZE - minecraft.font.width(label)) / 2;
        int labelY = y + (KEY_SIZE - 8) / 2;
        graphics.text(minecraft.font, label, labelX, labelY, 0xFFFFFFFF, false);
    }
}
