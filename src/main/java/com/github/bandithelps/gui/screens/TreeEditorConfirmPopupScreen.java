package com.github.bandithelps.gui.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TreeEditorConfirmPopupScreen extends Screen {
    private final Screen parent;
    private final String message;
    private final Runnable onConfirm;

    public TreeEditorConfirmPopupScreen(Screen parent, String message, Runnable onConfirm) {
        super(Component.literal("Confirm"));
        this.parent = parent;
        this.message = message;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        super.init();
        int w = 320;
        int h = 88;
        int x = Math.max(0, (this.width - w) / 2);
        int y = Math.max(0, (this.height - h) / 2);
        this.addRenderableWidget(new TreeEditorFlatButton(x + 12, y + h - 32, 100, 22, "Discard", TreeEditorFlatButton.Style.DANGER, this.onConfirm));
        this.addRenderableWidget(new TreeEditorFlatButton(x + w - 112, y + h - 32, 100, 22, "Cancel", this::onClose));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.parent.extractRenderState(graphics, Integer.MIN_VALUE, Integer.MIN_VALUE, partialTick);
        graphics.fill(0, 0, this.width, this.height, TreeEditorTheme.OVERLAY);
        int w = 320;
        int h = 88;
        int x = Math.max(0, (this.width - w) / 2);
        int y = Math.max(0, (this.height - h) / 2);
        TreeEditorTheme.dialog(graphics, this.font, x, y, w, h, "Confirm");
        graphics.centeredText(this.font, this.message, x + w / 2, y + 34, TreeEditorTheme.TEXT);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
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
}
