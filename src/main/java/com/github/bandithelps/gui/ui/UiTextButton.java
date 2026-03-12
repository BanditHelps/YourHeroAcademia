package com.github.bandithelps.gui.ui;

import com.github.bandithelps.gui.GeneUiStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public class UiTextButton extends AbstractUiElement {
    private final Supplier<Component> labelSupplier;
    private final Runnable onPress;
    private final Supplier<Component> tooltipSupplier;
    private boolean active;

    public UiTextButton(UiRect bounds, Supplier<Component> labelSupplier, Runnable onPress, Supplier<Component> tooltipSupplier) {
        super(bounds);
        this.labelSupplier = labelSupplier;
        this.onPress = onPress;
        this.tooltipSupplier = tooltipSupplier;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public Component tooltip() {
        return this.tooltipSupplier == null ? null : this.tooltipSupplier.get();
    }

    @Override
    public boolean onMouseClicked(int button, double mouseX, double mouseY) {
        if (button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        this.onPress.run();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        UiRect rect = bounds();
        GeneUiStyle.drawSlot(graphics, rect.x(), rect.y(), rect.width(), rect.height(), isHovered(), this.active);
        Component label = this.labelSupplier.get();
        graphics.drawCenteredString(font, label, rect.x() + rect.width() / 2, rect.y() + 3, this.active ? 0xFF111111 : 0xFF2A2A2A);
    }
}
