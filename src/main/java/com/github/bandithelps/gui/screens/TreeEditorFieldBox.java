package com.github.bandithelps.gui.screens;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class TreeEditorFieldBox extends EditBox {
    private Runnable onExpand = () -> {
    };

    public TreeEditorFieldBox(Font font, int width, int height, String label) {
        super(font, 0, 0, width, height, Component.literal(label));
    }

    public void setExpand(Runnable onExpand) {
        this.onExpand = onExpand == null ? () -> {
        } : onExpand;
    }

    public void showStart() {
        this.moveCursorToStart(false);
        this.setHighlightPos(this.getCursorPosition());
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            this.showStart();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (doubleClick && this.active && this.visible && this.isMouseOver(event.x(), event.y())) {
            this.onExpand.run();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
