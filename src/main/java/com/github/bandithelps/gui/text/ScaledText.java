package com.github.bandithelps.gui.text;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;

public final class ScaledText {
    private static final String ELLIPSIS = "..";

    private ScaledText() {
    }

    public static void draw(
            GuiGraphicsExtractor gui,
            Font font,
            String text,
            int x,
            int y,
            float scale,
            int color,
            boolean shadow
    ) {
        float resolved = resolve(scale);
        if (resolved == 1.0f) {
            gui.text(font, text, x, y, color, shadow);
            return;
        }
        Matrix3x2fStack pose = gui.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(resolved, resolved);
        gui.text(font, text, 0, 0, color, shadow);
        pose.popMatrix();
    }

    public static void draw(
            GuiGraphicsExtractor gui,
            Font font,
            Component text,
            int x,
            int y,
            float scale,
            int color,
            boolean shadow
    ) {
        draw(gui, font, text.getVisualOrderText(), x, y, scale, color, shadow);
    }

    public static void draw(
            GuiGraphicsExtractor gui,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            float scale,
            int color,
            boolean shadow
    ) {
        float resolved = resolve(scale);
        if (resolved == 1.0f) {
            gui.text(font, text, x, y, color, shadow);
            return;
        }
        Matrix3x2fStack pose = gui.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(resolved, resolved);
        gui.text(font, text, 0, 0, color, shadow);
        pose.popMatrix();
    }

    public static int width(Font font, String text, float scale) {
        return Math.round(font.width(text) * resolve(scale));
    }

    public static int width(Font font, Component text, float scale) {
        return Math.round(font.width(text) * resolve(scale));
    }

    public static int width(Font font, FormattedCharSequence text, float scale) {
        return Math.round(font.width(text) * resolve(scale));
    }

    public static int lineHeight(Font font, float scale) {
        return Math.round(font.lineHeight * resolve(scale));
    }

    public static String ellipsize(Font font, String text, int maxWidth, float scale) {
        if (width(font, text, scale) <= maxWidth) {
            return text;
        }
        int budget = maxWidth - width(font, ELLIPSIS, scale);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (width(font, builder.toString() + text.charAt(i), scale) > budget) {
                break;
            }
            builder.append(text.charAt(i));
        }
        return builder + ELLIPSIS;
    }

    private static float resolve(float scale) {
        return scale <= 0.0f ? 1.0f : scale;
    }
}
