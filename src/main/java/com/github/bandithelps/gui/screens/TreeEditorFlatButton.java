package com.github.bandithelps.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.function.BooleanSupplier;

public class TreeEditorFlatButton extends AbstractWidget {
    public enum Style {
        DEFAULT,
        ACCENT,
        DANGER,
        MENU,
        TOGGLE
    }

    private final Runnable onPress;
    private Style style;
    private BooleanSupplier highlighted = () -> false;

    public TreeEditorFlatButton(int x, int y, int width, int height, String text, Runnable onPress) {
        this(x, y, width, height, text, Style.DEFAULT, onPress);
    }

    public TreeEditorFlatButton(int x, int y, int width, int height, String text, Style style, Runnable onPress) {
        super(x, y, width, height, Component.literal(text));
        this.onPress = onPress;
        this.style = style;
    }

    public TreeEditorFlatButton highlightWhen(BooleanSupplier highlighted) {
        this.highlighted = highlighted;
        return this;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    public void setLabel(String text) {
        this.setMessage(Component.literal(text));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean hover = this.isHoveredOrFocused();
        boolean lit = this.highlighted.getAsBoolean() || hover;
        int fill = switch (this.style) {
            case ACCENT -> lit ? 0xFFB8883E : TreeEditorTheme.ACCENT_DIM;
            case DANGER -> lit ? 0xFFC45A62 : 0xFF8A3A40;
            case MENU -> lit ? TreeEditorTheme.HOVER : 0x00000000;
            case TOGGLE -> lit ? TreeEditorTheme.SELECT : TreeEditorTheme.INPUT;
            default -> lit ? TreeEditorTheme.HOVER : TreeEditorTheme.PANEL_ALT;
        };
        int border = this.style == Style.MENU
                ? 0x00000000
                : (this.highlighted.getAsBoolean() ? TreeEditorTheme.ACCENT : TreeEditorTheme.BORDER);
        if (this.style == Style.MENU) {
            TreeEditorTheme.fill(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), fill);
            if (this.highlighted.getAsBoolean()) {
                TreeEditorTheme.fill(graphics, this.getX() + 4, this.getY() + this.getHeight() - 2, this.getWidth() - 8, 2, TreeEditorTheme.ACCENT);
            }
        } else {
            TreeEditorTheme.rect(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), fill, border);
        }
        Minecraft minecraft = Minecraft.getInstance();
        String label = this.getMessage().getString();
        int textWidth = minecraft.font.width(label);
        int textX = this.getX() + Math.max(4, (this.getWidth() - textWidth) / 2);
        int textY = this.getY() + Math.max(1, (this.getHeight() - 8) / 2);
        int color = this.active ? TreeEditorTheme.TEXT : TreeEditorTheme.TEXT_MUTED;
        graphics.text(minecraft.font, label, textX, textY, color, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active || !this.visible || event.button() != 0 || !this.isMouseOver(event.x(), event.y())) {
            return false;
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        this.onPress.run();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getMessage());
    }
}
