package com.github.bandithelps.gui.screens;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class TreeEditorJsonEditScreen extends TreeEditorPopupScreen {
    private final Consumer<JsonElement> onSave;
    private String value;
    private EditBox box;

    public TreeEditorJsonEditScreen(Screen parent, String title, @Nullable JsonElement current, Consumer<JsonElement> onSave) {
        super(parent, title);
        this.onSave = onSave;
        this.value = current == null || current.isJsonNull() ? "" : current.toString();
        this.panelW = 420;
    }

    @Override
    protected void init() {
        super.init();
        this.rows.clear();
        this.layoutPanel(420);
        this.box = new EditBox(this.font, 0, 0, this.fieldW(), FIELD_H, Component.literal("JSON"));
        this.box.setMaxLength(8192);
        this.box.setValue(this.value);
        this.box.setResponder(text -> this.value = text);
        this.addRenderableWidget(this.box);
        this.addRow("JSON", this.box);
        this.finishRows();
        this.addFooterButton("Save", this.panelX + 12, this::save);
        this.addRenderableWidget(new TreeEditorFlatButton(this.panelX + 110, this.panelY + this.panelH - 28, 80, 22, "Clear", () -> {
            this.value = "";
            this.box.setValue("");
        }));
        this.addFooterButton("Cancel", this.panelX + this.panelW - 102, this::onClose);
        this.setInitialFocus(this.box);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.drawChrome(graphics, this.getTitle().getString());
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
        if (this.value.isBlank()) {
            this.onSave.accept(null);
            this.onClose();
            return;
        }
        try {
            this.onSave.accept(JsonParser.parseString(this.value));
            this.onClose();
        } catch (RuntimeException exception) {
            this.error = "Invalid JSON";
        }
    }
}
