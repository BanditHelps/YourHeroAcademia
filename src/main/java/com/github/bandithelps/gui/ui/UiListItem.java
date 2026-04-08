package com.github.bandithelps.gui.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.function.Function;

public class UiListItem<T> extends AbstractUiElement {
    @FunctionalInterface
    public interface ItemRenderer<T> {
        void render(UiListItem<T> item, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY);
    }

    @FunctionalInterface
    public interface ItemClickHandler<T> {
        boolean onClick(UiListItem<T> item, int button, double mouseX, double mouseY);
    }

    private final T data;
    private final ItemRenderer<T> renderer;
    private final ItemClickHandler<T> clickHandler;
    private final Function<UiListItem<T>, Component> tooltipSupplier;

    public UiListItem(
            UiRect bounds,
            T data,
            ItemRenderer<T> renderer,
            ItemClickHandler<T> clickHandler,
            Function<UiListItem<T>, Component> tooltipSupplier
    ) {
        super(bounds);
        this.data = data;
        this.renderer = renderer;
        this.clickHandler = clickHandler;
        this.tooltipSupplier = tooltipSupplier;
    }

    public T data() {
        return this.data;
    }

    @Override
    public Component tooltip() {
        return this.tooltipSupplier == null ? null : this.tooltipSupplier.apply(this);
    }

    @Override
    public boolean onMouseClicked(int button, double mouseX, double mouseY) {
        return this.clickHandler != null && this.clickHandler.onClick(this, button, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        this.renderer.render(this, graphics, font, mouseX, mouseY);
    }
}
