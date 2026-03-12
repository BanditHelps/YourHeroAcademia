package com.github.bandithelps.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

public final class GeneUiStyle {
    public static final Identifier FRAME_TEXTURE = Identifier.fromNamespaceAndPath("yha", "textures/gui/gene_frame.png");
    public static final Identifier SLOT_TEXTURE = Identifier.fromNamespaceAndPath("yha", "textures/gui/gene_slot.png");
    public static final Identifier TAB_TEXTURE = Identifier.fromNamespaceAndPath("yha", "textures/gui/gene_tab.png");

    public static final int FRAME_BG = 0xFFC6C6C6;
    public static final int FRAME_BORDER_DARK = 0xFF555555;
    public static final int FRAME_BORDER_LIGHT = 0xFFFFFFFF;
    public static final int PANEL_BG = 0xFF8B8B8B;
    public static final int INSET_BG = 0xFF373737;
    public static final int SLOT_BG = 0xFF8B8B8B;
    public static final int SLOT_HOVER = 0xFFA0A0A0;
    public static final int SLOT_ACTIVE = 0xFFC6C6C6;
    public static final int TEXT = 0xFF404040;
    public static final int TEXT_BRIGHT = 0xFFFFFFFF;
    public static final int HINT_TEXT = 0xFFE0E0E0;

    private GeneUiStyle() {
    }

    public static void drawBevelPanel(GuiGraphics graphics, int x, int y, int width, int height, int fill) {
        graphics.fill(x, y, x + width, y + height, fill);
        drawInsetOutline(graphics, x, y, width, height, FRAME_BORDER_LIGHT, FRAME_BORDER_DARK);
    }

    public static void drawTiledTexture(GuiGraphics graphics, Identifier texture, int x, int y, int width, int height) {
        // Placeholder textures are not authored as scalable GUI atlases yet.
        // Keep the hook for future assets, but skip blitting to avoid sampling artifacts.
    }

    public static void drawInsetPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, INSET_BG);
        drawInsetOutline(graphics, x, y, width, height, FRAME_BORDER_DARK, FRAME_BORDER_LIGHT);
    }

    public static void drawSlot(GuiGraphics graphics, int x, int y, int width, int height, boolean hovered, boolean active) {
        int fill = active ? SLOT_ACTIVE : (hovered ? SLOT_HOVER : SLOT_BG);
        drawTiledTexture(graphics, SLOT_TEXTURE, x, y, width, height);
        graphics.fill(x, y, x + width, y + height, fill);
        drawInsetOutline(graphics, x, y, width, height, FRAME_BORDER_LIGHT, FRAME_BORDER_DARK);
    }

    private static void drawInsetOutline(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int topLeftColor,
            int bottomRightColor
    ) {
        graphics.hLine(x, x + width - 1, y, topLeftColor);
        graphics.vLine(x, y, y + height - 1, topLeftColor);
        graphics.hLine(x, x + width - 1, y + height - 1, bottomRightColor);
        graphics.vLine(x + width - 1, y, y + height - 1, bottomRightColor);
    }
}
