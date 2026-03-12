package com.github.bandithelps.gui.ui;

import com.github.bandithelps.gui.GeneUiStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UiDropdown extends AbstractUiElement {
    private final Supplier<List<String>> optionsSupplier;
    private final Supplier<String> selectedSupplier;
    private final Consumer<String> onSelect;
    private final Supplier<Component> tooltipSupplier;
    private final int rowHeight;
    private boolean expanded;

    public UiDropdown(
            UiRect bounds,
            int rowHeight,
            Supplier<List<String>> optionsSupplier,
            Supplier<String> selectedSupplier,
            Consumer<String> onSelect,
            Supplier<Component> tooltipSupplier
    ) {
        super(bounds);
        this.rowHeight = rowHeight;
        this.optionsSupplier = optionsSupplier;
        this.selectedSupplier = selectedSupplier;
        this.onSelect = onSelect;
        this.tooltipSupplier = tooltipSupplier;
    }

    public boolean isExpanded() {
        return this.expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    @Override
    public boolean wantsGlobalMouseClicks() {
        return this.expanded;
    }

    @Override
    public boolean contains(double mouseX, double mouseY) {
        if (super.contains(mouseX, mouseY)) {
            return true;
        }
        if (!this.expanded) {
            return false;
        }
        return popupRect().contains(mouseX, mouseY);
    }

    @Override
    public void updateHover(double mouseX, double mouseY) {
        super.updateHover(mouseX, mouseY);
    }

    @Override
    public Component tooltip() {
        return this.tooltipSupplier == null ? null : this.tooltipSupplier.get();
    }

    @Override
    public boolean onMouseClicked(int button, double mouseX, double mouseY) {
        if (button != 0) {
            return false;
        }
        if (bounds().contains(mouseX, mouseY)) {
            this.expanded = !this.expanded;
            return true;
        }
        if (!this.expanded) {
            return false;
        }

        List<String> options = this.optionsSupplier.get();
        UiRect popup = popupRect();
        if (popup.contains(mouseX, mouseY)) {
            int optionTop = popup.y() + 1;
            for (String option : options) {
                UiRect optionRect = new UiRect(popup.x(), optionTop, popup.width(), this.rowHeight);
                if (optionRect.contains(mouseX, mouseY)) {
                    this.onSelect.accept(option);
                    this.expanded = false;
                    return true;
                }
                optionTop += this.rowHeight + 2;
            }
            return true;
        }

        this.expanded = false;
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        UiRect rect = bounds();
        String selected = this.selectedSupplier.get();
        String label = "Category: " + (selected == null ? "None" : selected) + (this.expanded ? " ^" : " v");
        GeneUiStyle.drawSlot(graphics, rect.x(), rect.y(), rect.width(), rect.height(), isHovered(), this.expanded);
        graphics.drawString(font, label, rect.x() + 4, rect.y() + 3, 0xFF1F1F1F, false);

        if (!this.expanded) {
            return;
        }

        UiRect popup = popupRect();
        GeneUiStyle.drawBevelPanel(graphics, popup.x(), popup.y(), popup.width(), popup.height(), 0xFFB3B3B3);

        int optionTop = popup.y() + 1;
        for (String option : this.optionsSupplier.get()) {
            UiRect optionRect = new UiRect(popup.x(), optionTop, popup.width(), this.rowHeight);
            boolean hovered = optionRect.contains(mouseX, mouseY);
            GeneUiStyle.drawSlot(graphics, optionRect.x(), optionRect.y(), optionRect.width(), optionRect.height(), hovered, false);
            graphics.drawString(font, option, optionRect.x() + 4, optionRect.y() + 3, 0xFF1F1F1F, false);
            optionTop += this.rowHeight + 2;
        }
    }

    private UiRect popupRect() {
        List<String> options = new ArrayList<>(this.optionsSupplier.get());
        UiRect rect = bounds();
        int popupY = rect.y() + rect.height() + 1;
        int popupH = options.size() * (this.rowHeight + 2) + 2;
        return new UiRect(rect.x(), popupY, rect.width(), popupH);
    }
}
