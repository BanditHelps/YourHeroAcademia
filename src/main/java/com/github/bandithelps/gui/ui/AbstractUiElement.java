package com.github.bandithelps.gui.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public abstract class AbstractUiElement {
    private UiRect bounds;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean hovered;

    protected AbstractUiElement(UiRect bounds) {
        this.bounds = bounds;
    }

    public UiRect bounds() {
        return this.bounds;
    }

    public void setBounds(UiRect bounds) {
        this.bounds = bounds;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isHovered() {
        return this.hovered;
    }

    public void updateHover(double mouseX, double mouseY) {
        this.hovered = this.visible && this.bounds.contains(mouseX, mouseY);
    }

    public boolean contains(double mouseX, double mouseY) {
        return this.visible && this.bounds.contains(mouseX, mouseY);
    }

    public boolean wantsGlobalMouseClicks() {
        return false;
    }

    public Component tooltip() {
        return null;
    }

    public boolean onMouseClicked(int button, double mouseX, double mouseY) {
        return false;
    }

    public boolean onMouseScrolled(double mouseX, double mouseY, double scrollY) {
        return false;
    }

    public abstract void render(GuiGraphics graphics, Font font, int mouseX, int mouseY);
}
