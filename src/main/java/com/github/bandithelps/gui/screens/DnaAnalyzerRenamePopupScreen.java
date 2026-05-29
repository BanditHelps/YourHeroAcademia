package com.github.bandithelps.gui.screens;

import com.github.bandithelps.network.DNAAnalyzerRenamePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

public class DnaAnalyzerRenamePopupScreen extends Screen {
    private static final int POPUP_WIDTH = 220;
    private static final int POPUP_HEIGHT = 92;

    private final Screen parent;
    private final BlockPos analyzerPos;
    private final int slotIndex;
    private final String initialName;
    private EditBox nameBox;

    public DnaAnalyzerRenamePopupScreen(Screen parent, BlockPos analyzerPos, int slotIndex, String initialName) {
        super(Component.literal("Rename Gene"));
        this.parent = parent;
        this.analyzerPos = analyzerPos;
        this.slotIndex = slotIndex;
        this.initialName = initialName == null ? "" : initialName;
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - POPUP_WIDTH) / 2;
        int y = (this.height - POPUP_HEIGHT) / 2;

        this.nameBox = new EditBox(this.font, x + 10, y + 28, POPUP_WIDTH - 20, 20, Component.literal("Gene name"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setValue(this.initialName);
        this.addRenderableWidget(this.nameBox);
        this.setInitialFocus(this.nameBox);

        this.addRenderableWidget(Button.builder(Component.literal("Rename"), button -> this.submitRename())
                .bounds(x + 10, y + 56, 92, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
                .bounds(x + POPUP_WIDTH - 102, y + 56, 92, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        int x = (this.width - POPUP_WIDTH) / 2;
        int y = (this.height - POPUP_HEIGHT) / 2;
        graphics.fill(x, y, x + POPUP_WIDTH, y + POPUP_HEIGHT, 0xE6111720);
        graphics.fill(x + 1, y + 1, x + POPUP_WIDTH - 1, y + POPUP_HEIGHT - 1, 0xE6202B3A);
        graphics.fill(x, y, x + POPUP_WIDTH, y + 1, 0xFF79B8FF);
        graphics.fill(x, y + POPUP_HEIGHT - 1, x + POPUP_WIDTH, y + POPUP_HEIGHT, 0xFF79B8FF);
        graphics.fill(x, y, x + 1, y + POPUP_HEIGHT, 0xFF79B8FF);
        graphics.fill(x + POPUP_WIDTH - 1, y, x + POPUP_WIDTH, y + POPUP_HEIGHT, 0xFF79B8FF);
        graphics.centeredText(this.font, "Rename Gene", x + (POPUP_WIDTH / 2), y + 10, 0xFFE6F2FF);
        graphics.text(this.font, "Slot " + (this.slotIndex + 1), x + 10, y + 18, 0xFF9FC9EE, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            this.submitRename();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void submitRename() {
        if (this.minecraft == null || this.analyzerPos == null) {
            this.onClose();
            return;
        }
        String newName = this.nameBox == null ? "" : this.nameBox.getValue().trim();
        if (!newName.isEmpty()) {
            ClientPacketDistributor.sendToServer(new DNAAnalyzerRenamePayload(this.analyzerPos, this.slotIndex, newName));
        }
        this.onClose();
    }
}
