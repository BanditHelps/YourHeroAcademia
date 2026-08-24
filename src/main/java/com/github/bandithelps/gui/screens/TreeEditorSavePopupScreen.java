package com.github.bandithelps.gui.screens;

import com.github.bandithelps.gui.tree.TreeEditorCostSchema;
import com.github.bandithelps.gui.tree.TreeEditorDraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class TreeEditorSavePopupScreen extends TreeEditorPopupScreen {
    private final TreeEditorScreen tree;
    private String fileName;

    public TreeEditorSavePopupScreen(TreeEditorScreen parent, TreeEditorDraft draft, List<TreeEditorCostSchema> schemas) {
        super(parent, "Save Power");
        this.tree = parent;
        this.fileName = draft.getExportFileName();
        this.panelW = 320;
    }

    @Override
    protected void init() {
        super.init();
        this.rows.clear();
        this.layoutPanel(320);
        EditBox box = new EditBox(this.font, 0, 0, this.fieldW(), FIELD_H, Component.literal("File name"));
        box.setMaxLength(64);
        box.setValue(this.fileName);
        box.setResponder(value -> this.fileName = value);
        this.addRow("File name (yha_exports)", this.addRenderableWidget(box));
        this.finishRows();
        this.addFooterButton("Save", this.panelX + 12, this::save);
        this.addFooterButton("Cancel", this.panelX + this.panelW - 102, this::onClose);
        this.setInitialFocus(box);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.drawChrome(graphics, "Save Power As");
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

    private void save() {
        String sanitized = TreeEditorDraft.sanitizeFileName(this.fileName);
        if (sanitized.isBlank()) {
            this.error = "Name required";
            return;
        }
        this.tree.saveToFile(sanitized);
    }
}
