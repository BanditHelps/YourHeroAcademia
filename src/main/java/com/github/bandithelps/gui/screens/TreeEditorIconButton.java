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

public class TreeEditorIconButton extends AbstractWidget {
    private final Runnable onPress;
    private String iconId;

    public TreeEditorIconButton(int width, int height, String iconId, Runnable onPress) {
        super(0, 0, width, height, Component.literal("Icon"));
        this.iconId = iconId == null ? "" : iconId;
        this.onPress = onPress;
    }

    public void setIconId(String iconId) {
        this.iconId = iconId == null ? "" : iconId;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean hover = this.isHoveredOrFocused();
        TreeEditorTheme.rect(
                graphics,
                this.getX(),
                this.getY(),
                this.getWidth(),
                this.getHeight(),
                hover ? TreeEditorTheme.HOVER : TreeEditorTheme.PANEL_ALT,
                TreeEditorTheme.BORDER
        );
        int iconY = this.getY() + Math.max(0, (this.getHeight() - 16) / 2);
        TreeEditorIcons.draw(graphics, this.iconId, this.getX() + 4, iconY);
        String label = this.iconId.isBlank() ? "Choose…" : "Change";
        graphics.text(Minecraft.getInstance().font, label, this.getX() + 24, this.getY() + Math.max(1, (this.getHeight() - 8) / 2), TreeEditorTheme.TEXT, false);
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
