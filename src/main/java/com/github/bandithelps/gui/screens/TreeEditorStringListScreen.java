package com.github.bandithelps.gui.screens;

import com.github.bandithelps.gui.tree.TreeEditorJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TreeEditorStringListScreen extends TreeEditorPopupScreen {
    private final Consumer<JsonElement> onSave;
    private final List<String> values = new ArrayList<>();

    public TreeEditorStringListScreen(Screen parent, String title, @Nullable JsonElement current, Consumer<JsonElement> onSave) {
        super(parent, title);
        this.onSave = onSave;
        if (current != null && current.isJsonArray()) {
            for (JsonElement element : current.getAsJsonArray()) {
                this.values.add(TreeEditorJson.asText(element));
            }
        } else if (current != null && current.isJsonPrimitive()) {
            this.values.add(current.getAsString());
        }
        this.panelW = 400;
    }

    @Override
    protected void init() {
        super.init();
        this.rows.clear();
        this.layoutPanel(400);
        int fieldW = this.fieldW();
        for (int index = 0; index < this.values.size(); index++) {
            int captured = index;
            EditBox box = new EditBox(this.font, 0, 0, fieldW - 40, FIELD_H, Component.literal("Item"));
            box.setMaxLength(512);
            box.setValue(this.values.get(index));
            box.setResponder(value -> this.values.set(captured, value));
            this.addRenderableWidget(box);
            TreeEditorFlatButton remove = this.addRenderableWidget(new TreeEditorFlatButton(0, 0, 32, FIELD_H, "-", () -> {
                this.values.remove(captured);
                this.init(this.width, this.height);
            }));
            this.addSplitRow("Item " + (index + 1), box, fieldW - 40, remove, 32);
        }
        this.addRow("", this.addRenderableWidget(new TreeEditorFlatButton(0, 0, fieldW, FIELD_H, "+ Add", () -> {
            this.values.add("");
            this.init(this.width, this.height);
        })));
        this.finishRows();
        this.addFooterButton("Save", this.panelX + 12, this::save);
        this.addRenderableWidget(new TreeEditorFlatButton(this.panelX + 110, this.panelY + this.panelH - 28, 80, 22, "Clear", () -> {
            this.values.clear();
            this.onSave.accept(null);
            this.onClose();
        }));
        this.addFooterButton("Cancel", this.panelX + this.panelW - 102, this::onClose);
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
        JsonArray array = new JsonArray();
        for (String value : this.values) {
            if (value != null && !value.isBlank()) {
                array.add(value);
            }
        }
        this.onSave.accept(array.isEmpty() ? null : array);
        this.onClose();
    }
}
