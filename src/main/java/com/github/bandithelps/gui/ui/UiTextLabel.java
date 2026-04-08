package com.github.bandithelps.gui.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public class UiTextLabel extends AbstractUiElement {
    private final Supplier<Component> textSupplier;
    private final int color;
    private final boolean centered;

    public UiTextLabel(UiRect bounds, Supplier<Component> textSupplier, int color, boolean centered) {
        super(bounds);
        this.textSupplier = textSupplier;
        this.color = color;
        this.centered = centered;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        UiRect rect = bounds();
        Component text = this.textSupplier.get();
        if (this.centered) {
            graphics.centeredText(font, text, rect.x() + rect.width() / 2, rect.y(), this.color);
            return;
        }
        graphics.text(font, text, rect.x(), rect.y(), this.color, false);
    }
}
