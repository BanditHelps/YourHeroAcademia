package com.github.bandithelps.gui.screens;

import com.github.bandithelps.gui.tree.TreeEditorDraft;
import com.github.bandithelps.gui.tree.TreeEditorNode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class TreeEditorEditPopupScreen extends Screen {
    private static final int POPUP_WIDTH = 240;
    private static final int POPUP_HEIGHT = 148;

    private final TreeEditorScreen parent;
    private final TreeEditorDraft draft;
    private final TreeEditorNode node;
    private EditBox keyBox;
    private EditBox titleBox;
    private EditBox costBox;
    private String error = "";

    public TreeEditorEditPopupScreen(TreeEditorScreen parent, TreeEditorDraft draft, TreeEditorNode node) {
        super(Component.literal("Edit Tree Node"));
        this.parent = parent;
        this.draft = draft;
        this.node = node;
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - POPUP_WIDTH) / 2;
        int y = (this.height - POPUP_HEIGHT) / 2;

        this.keyBox = new EditBox(this.font, x + 10, y + 28, POPUP_WIDTH - 20, 18, Component.literal("Key"));
        this.keyBox.setMaxLength(48);
        this.keyBox.setValue(this.node.getKey());
        this.keyBox.setEditable(this.node.isCreated());
        this.addRenderableWidget(this.keyBox);

        this.titleBox = new EditBox(this.font, x + 10, y + 64, POPUP_WIDTH - 20, 18, Component.literal("Title"));
        this.titleBox.setMaxLength(64);
        this.titleBox.setValue(this.node.getTitle());
        this.addRenderableWidget(this.titleBox);

        this.costBox = new EditBox(this.font, x + 10, y + 100, 80, 18, Component.literal("Cost"));
        this.costBox.setMaxLength(4);
        this.costBox.setValue(Integer.toString(this.node.getCostPoints()));
        this.addRenderableWidget(this.costBox);

        this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> this.save())
                .bounds(x + 10, y + POPUP_HEIGHT - 28, 92, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
                .bounds(x + POPUP_WIDTH - 102, y + POPUP_HEIGHT - 28, 92, 20)
                .build());
        this.setInitialFocus(this.node.isCreated() ? this.keyBox : this.titleBox);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        int x = (this.width - POPUP_WIDTH) / 2;
        int y = (this.height - POPUP_HEIGHT) / 2;
        graphics.fill(x, y, x + POPUP_WIDTH, y + POPUP_HEIGHT, 0xE6111720);
        graphics.fill(x + 1, y + 1, x + POPUP_WIDTH - 1, y + POPUP_HEIGHT - 1, 0xE6202B3A);
        graphics.centeredText(this.font, "Edit Node", x + (POPUP_WIDTH / 2), y + 8, 0xFFE6F2FF);
        graphics.text(this.font, "Key", x + 10, y + 18, 0xFF9FC9EE, false);
        graphics.text(this.font, "Title", x + 10, y + 54, 0xFF9FC9EE, false);
        graphics.text(this.font, "Upgrade point cost", x + 10, y + 90, 0xFF9FC9EE, false);
        if (!this.error.isEmpty()) {
            graphics.text(this.font, this.error, x + 96, y + 104, 0xFFFF6666, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            this.save();
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

    private void save() {
        String title = this.titleBox.getValue().trim();
        if (title.isEmpty()) {
            this.error = "Title required";
            return;
        }
        int cost;
        try {
            cost = Integer.parseInt(this.costBox.getValue().trim());
        } catch (NumberFormatException exception) {
            this.error = "Cost must be a number";
            return;
        }
        if (cost < 1) {
            this.error = "Cost must be >= 1";
            return;
        }
        if (this.node.isCreated() && !this.draft.rename(this.node, this.keyBox.getValue())) {
            this.error = "Key is invalid or taken";
            return;
        }
        this.node.setTitle(title);
        this.node.setCostPoints(cost);
        this.onClose();
    }
}
