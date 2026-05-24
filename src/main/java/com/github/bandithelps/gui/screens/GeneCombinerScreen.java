package com.github.bandithelps.gui.screens;

import com.github.bandithelps.gui.menu.GeneCombinerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class GeneCombinerScreen extends AbstractContainerScreen<GeneCombinerMenu> {
    private static final Identifier BACKGROUND_TEXTURE =
            Identifier.fromNamespaceAndPath("yha", "textures/gui/gene_combiner.png");
    private static final int BACKGROUND_TEXTURE_SIZE = 256;
    private Button combineButton;

    public GeneCombinerScreen(GeneCombinerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.combineButton = this.addRenderableWidget(Button.builder(Component.literal("Combine"), button -> {
                    if (this.minecraft != null && this.minecraft.gameMode != null) {
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
                    }
                }).bounds(this.leftPos + 116, this.topPos + 52, 54, 20)
                .build());
        this.combineButton.active = !this.menu.isProcessing();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                BACKGROUND_TEXTURE,
                this.leftPos,
                this.topPos,
                0.0F,
                0.0F,
                this.imageWidth,
                this.imageHeight,
                BACKGROUND_TEXTURE_SIZE,
                BACKGROUND_TEXTURE_SIZE
        );
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
