package com.github.bandithelps.gui.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class TreeEditorExportPopupScreen extends Screen {
    private static final int POPUP_WIDTH = 280;
    private static final int HEADER = 16;
    private static final int FOOTER = 26;
    private static final int TEXT_LIGHT = 0xFFFFFFFF;

    private final TreeEditorScreen parent;
    private final String path;
    private int panelX;
    private int panelY;
    private int panelH;
    private List<String> pathLines = List.of();

    public TreeEditorExportPopupScreen(TreeEditorScreen parent, String path) {
        super(Component.literal("Export Successful"));
        this.parent = parent;
        this.path = path == null ? "" : path;
    }

    @Override
    protected void init() {
        super.init();
        int innerWidth = POPUP_WIDTH - 20;
        this.pathLines = wrapPath(this.path, innerWidth);
        int body = 24 + this.pathLines.size() * 10;
        this.panelH = HEADER + body + FOOTER;
        this.panelX = Math.max(0, (this.width - Math.min(POPUP_WIDTH, this.width)) / 2);
        this.panelY = Math.max(0, (this.height - this.panelH) / 2);
        this.addRenderableWidget(Button.builder(Component.literal("OK"), button -> this.onClose())
                .bounds(this.panelX + POPUP_WIDTH - 58, this.panelY + this.panelH - 22, 50, 16)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.parent.extractRenderState(graphics, Integer.MIN_VALUE, Integer.MIN_VALUE, partialTick);
        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        int x = this.panelX;
        int y = this.panelY;
        graphics.fill(x, y, x + POPUP_WIDTH, y + this.panelH, 0xFF2B2B2B);
        graphics.fill(x + 1, y + 1, x + POPUP_WIDTH - 1, y + this.panelH - 1, 0xFFC6C6C6);
        graphics.fill(x + 4, y + 4, x + POPUP_WIDTH - 4, y + this.panelH - FOOTER, 0xFF000000);
        graphics.centeredText(this.font, "Export Successful", x + POPUP_WIDTH / 2, y + 6, TEXT_LIGHT);
        int textY = y + HEADER + 8;
        graphics.text(this.font, "Saved power file to:", x + 10, textY, 0xFFDDDDDD, false);
        textY += 12;
        for (String line : this.pathLines) {
            graphics.text(this.font, line, x + 10, textY, TEXT_LIGHT, false);
            textY += 10;
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        super.mouseClicked(event, doubleClick);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private List<String> wrapPath(String path, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String remaining = path;
        while (!remaining.isEmpty()) {
            int fit = remaining.length();
            while (fit > 1 && this.font.width(remaining.substring(0, fit)) > maxWidth) {
                fit--;
            }
            lines.add(remaining.substring(0, fit));
            remaining = remaining.substring(fit);
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }
}
