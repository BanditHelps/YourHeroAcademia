package com.github.bandithelps.gui.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ExampleScreen extends Screen {
    public ExampleScreen() {
        super(Component.literal("YHA Test Screen"));
    }

    @Override
    public void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2 - 30;

        this.addRenderableWidget(Button.builder(Component.literal("Button 1"), button -> onTestButtonPressed(1))
                .bounds(centerX - 60, centerY, 120, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Button 2"), button -> onTestButtonPressed(2))
                .bounds(centerX - 60, centerY + 24, 120, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Button 3"), button -> onTestButtonPressed(3))
                .bounds(centerX - 60, centerY + 48, 120, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Close Menu"), button -> closeButtonPressed())
                .bounds(centerX - 60, centerY + 72, 120, 20)
                .build());
    }

    private void onTestButtonPressed(int index) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.literal("Pressed test button " + index));
        }
    }

    private void closeButtonPressed() {
        if (this.minecraft.player != null) {
            this.onClose();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width / 2, this.height / 2, 0xFF333333);
        graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
