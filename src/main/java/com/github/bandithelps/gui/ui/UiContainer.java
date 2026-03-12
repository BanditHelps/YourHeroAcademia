package com.github.bandithelps.gui.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UiContainer extends AbstractUiElement {
    private final List<AbstractUiElement> children = new ArrayList<>();

    public UiContainer(UiRect bounds) {
        super(bounds);
    }

    public void clearChildren() {
        this.children.clear();
    }

    public void addChild(AbstractUiElement element) {
        this.children.add(element);
    }

    public List<AbstractUiElement> children() {
        return Collections.unmodifiableList(this.children);
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        for (AbstractUiElement child : this.children) {
            child.updateHover(mouseX, mouseY);
            if (child.isVisible()) {
                child.render(graphics, font, mouseX, mouseY);
            }
        }
    }

    @Override
    public Component tooltip() {
        for (int i = this.children.size() - 1; i >= 0; i--) {
            AbstractUiElement child = this.children.get(i);
            if (!child.isVisible() || !child.isHovered()) {
                continue;
            }
            Component tooltip = child.tooltip();
            if (tooltip != null) {
                return tooltip;
            }
        }
        return null;
    }

    @Override
    public boolean onMouseClicked(int button, double mouseX, double mouseY) {
        for (int i = this.children.size() - 1; i >= 0; i--) {
            AbstractUiElement child = this.children.get(i);
            if (!child.isVisible() || !child.isEnabled()) {
                continue;
            }
            if (!child.contains(mouseX, mouseY) && !child.wantsGlobalMouseClicks()) {
                continue;
            }
            if (child.onMouseClicked(button, mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double scrollY) {
        for (int i = this.children.size() - 1; i >= 0; i--) {
            AbstractUiElement child = this.children.get(i);
            if (!child.isVisible() || !child.isEnabled() || !child.contains(mouseX, mouseY)) {
                continue;
            }
            if (child.onMouseScrolled(mouseX, mouseY, scrollY)) {
                return true;
            }
        }
        return false;
    }
}
