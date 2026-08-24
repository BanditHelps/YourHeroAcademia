package com.github.bandithelps.gui.screens;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class TreeEditorTheme {
    public static final int BG = 0xFF14161A;
    public static final int PANEL = 0xFF1E2228;
    public static final int PANEL_ALT = 0xFF252A31;
    public static final int HEADER = 0xFF2A3038;
    public static final int BORDER = 0xFF3E4550;
    public static final int ACCENT = 0xFFD4A25A;
    public static final int ACCENT_DIM = 0xFF8A6A38;
    public static final int TEXT = 0xFFE8E4DA;
    public static final int TEXT_MUTED = 0xFF9A9488;
    public static final int INPUT = 0xFF12151A;
    public static final int HOVER = 0xFF333A44;
    public static final int SELECT = 0xFF3D3426;
    public static final int DANGER = 0xFFE06C75;
    public static final int OVERLAY = 0xB00A0B0D;
    public static final int MENU_H = 22;
    public static final int STATUS_H = 24;
    public static final int FIELD_H = 20;
    public static final int ROW = 38;
    public static final int SECTION_H = 18;
    public static final int LIST_ROW = 18;

    private TreeEditorTheme() {
    }

    public static void fill(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
    }

    public static void rect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill, int border) {
        fill(graphics, x, y, width, height, border);
        fill(graphics, x + 1, y + 1, width - 2, height - 2, fill);
    }

    public static void border(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        fill(graphics, x, y, width, 1, color);
        fill(graphics, x, y + height - 1, width, 1, color);
        fill(graphics, x, y, 1, height, color);
        fill(graphics, x + width - 1, y, 1, height, color);
    }

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        rect(graphics, x, y, width, height, PANEL, BORDER);
    }

    public static void dialog(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, String title) {
        rect(graphics, x, y, width, height, PANEL, BORDER);
        fill(graphics, x + 1, y + 1, width - 2, 22, HEADER);
        fill(graphics, x + 1, y + 22, width - 2, 1, ACCENT_DIM);
        graphics.text(font, title, x + 10, y + 8, TEXT, false);
    }

    public static void section(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, String title) {
        section(graphics, font, x, y, width, title, false);
    }

    public static void section(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, String title, boolean collapsed) {
        fill(graphics, x, y, width, SECTION_H, HEADER);
        fill(graphics, x, y, 2, SECTION_H, ACCENT);
        graphics.text(font, (collapsed ? ">  " : "v  ") + title, x + 8, y + 5, ACCENT, false);
    }

    public static void scrollbar(GuiGraphicsExtractor graphics, int trackX, int trackY, int trackH, int contentH, int scroll, int maxScroll) {
        if (maxScroll <= 0 || trackH <= 0) {
            return;
        }
        int thumbH = Math.max(16, trackH * trackH / Math.max(trackH, contentH));
        int thumbY = trackY + (trackH - thumbH) * scroll / maxScroll;
        fill(graphics, trackX, trackY, 4, trackH, INPUT);
        fill(graphics, trackX, thumbY, 4, thumbH, ACCENT_DIM);
    }

    public static int walkWrapped(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int maxWidth, int color) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), maxWidth);
        for (FormattedCharSequence line : lines) {
            if (graphics != null) {
                graphics.text(font, line, x, y, color, false);
            }
            y += 11;
        }
        return y;
    }

    public static int measureWrapped(Font font, String text, int maxWidth) {
        return font.split(Component.literal(text), maxWidth).size() * 11;
    }
}
