package com.github.bandithelps.gui.screens;

import com.github.bandithelps.gui.tree.schema.DocSchema;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class TreeEditorTypePickerScreen extends Screen {
    private static final int TEXT_LIGHT = TreeEditorTheme.TEXT;
    private static final int ROW = TreeEditorTheme.LIST_ROW;

    private final Screen parent;
    private final List<DocSchema> all;
    private final Consumer<String> onSelect;
    private final List<DocSchema> filtered = new ArrayList<>();
    private EditBox searchBox;
    private int scroll;

    public TreeEditorTypePickerScreen(Screen parent, List<DocSchema> schemas, Consumer<String> onSelect) {
        super(Component.literal("Select Type"));
        this.parent = parent;
        this.all = schemas;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.panelX();
        int y = this.panelY();
        this.searchBox = new EditBox(this.font, x + 10, y + 28, this.panelW() - 20, 20, Component.literal("Search"));
        this.searchBox.setResponder(value -> {
            this.scroll = 0;
            this.applyFilter();
        });
        this.addRenderableWidget(this.searchBox);
        this.addRenderableWidget(new TreeEditorFlatButton(x + this.panelW() - 90, y + this.panelH() - 30, 80, 22, "Cancel", this::onClose));
        this.setInitialFocus(this.searchBox);
        this.applyFilter();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.parent.extractRenderState(graphics, Integer.MIN_VALUE, Integer.MIN_VALUE, partialTick);
        graphics.fill(0, 0, this.width, this.height, TreeEditorTheme.OVERLAY);
        int x = this.panelX();
        int y = this.panelY();
        TreeEditorTheme.dialog(graphics, this.font, x, y, this.panelW(), this.panelH(), this.title.getString());

        int listTop = y + 54;
        int listBottom = y + this.panelH() - 36;
        int visible = Math.max(1, (listBottom - listTop) / ROW);
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.filtered.size() - visible)));
        graphics.enableScissor(x + 6, listTop, x + this.panelW() - 6, listBottom);
        for (int index = 0; index < this.filtered.size(); index++) {
            int rowY = listTop + (index - this.scroll) * ROW;
            if (rowY + ROW < listTop || rowY > listBottom) {
                continue;
            }
            DocSchema schema = this.filtered.get(index);
            boolean hovered = mouseX >= x + 8 && mouseX < x + this.panelW() - 8 && mouseY >= rowY && mouseY < rowY + ROW;
            if (hovered) {
                graphics.fill(x + 6, rowY, x + this.panelW() - 6, rowY + ROW, TreeEditorTheme.SELECT);
            }
            graphics.text(this.font, schema.name() + "  " + schema.id(), x + 10, rowY + 3, TEXT_LIGHT, false);
        }
        graphics.disableScissor();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int x = this.panelX();
        int y = this.panelY();
        int listTop = y + 54;
        int listBottom = y + this.panelH() - 36;
        if (mouseX < x + 8 || mouseX >= x + this.panelW() - 8 || mouseY < listTop || mouseY >= listBottom) {
            return true;
        }
        int index = this.scroll + (mouseY - listTop) / ROW;
        if (index >= 0 && index < this.filtered.size()) {
            this.onSelect.accept(this.filtered.get(index).id());
            if (this.minecraft != null && this.minecraft.screen == this) {
                this.onClose();
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scroll -= (int) Math.signum(scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
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

    private void applyFilter() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        this.filtered.clear();
        for (DocSchema schema : this.all) {
            if (query.isEmpty()
                    || schema.id().toLowerCase(Locale.ROOT).contains(query)
                    || schema.name().toLowerCase(Locale.ROOT).contains(query)) {
                this.filtered.add(schema);
            }
        }
        this.filtered.sort(Comparator
                .comparing((DocSchema schema) -> schema.name(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(DocSchema::id, String.CASE_INSENSITIVE_ORDER));
    }

    private int panelW() {
        return Math.min(460, Math.max(280, this.width - 24));
    }

    private int panelH() {
        return Math.min(280, Math.max(180, this.height - 24));
    }

    private int panelX() {
        return Math.max(0, (this.width - this.panelW()) / 2);
    }

    private int panelY() {
        return Math.max(0, (this.height - this.panelH()) / 2);
    }
}
