package com.github.bandithelps.gui.ui;

import com.github.bandithelps.gui.GeneUiStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public class UiIconButton extends AbstractUiElement {
    private final Supplier<String> iconSupplier;
    private final Runnable onPress;
    private final Supplier<Component> tooltipSupplier;

    public UiIconButton(UiRect bounds, Supplier<String> iconSupplier, Runnable onPress, Supplier<Component> tooltipSupplier) {
        super(bounds);
        this.iconSupplier = iconSupplier;
        this.onPress = onPress;
        this.tooltipSupplier = tooltipSupplier;
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
    public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        UiRect rect = bounds();
        GeneUiStyle.drawSlot(graphics, rect.x(), rect.y(), rect.width(), rect.height(), isHovered(), false);
        graphics.centeredText(font, this.iconSupplier.get(), rect.x() + rect.width() / 2, rect.y() + 2, 0xFF161616);
    }
}
