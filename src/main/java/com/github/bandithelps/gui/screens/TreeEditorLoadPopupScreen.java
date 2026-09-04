package com.github.bandithelps.gui.screens;

import com.github.bandithelps.gui.tree.PowerSourceJson;
import com.github.bandithelps.gui.tree.TreeEditorDraft;
import com.github.bandithelps.gui.tree.TreeEditorExports;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.threetag.palladium.power.Power;
import net.threetag.palladium.registry.PalladiumRegistryKeys;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TreeEditorLoadPopupScreen extends Screen {
    private static final int TEXT_LIGHT = TreeEditorTheme.TEXT;
    private static final int ROW = TreeEditorTheme.LIST_ROW;

    private enum Source {
        POWERS,
        EXPORTS
    }

    private final TreeEditorScreen parent;
    private final boolean dirty;
    private Source source = Source.POWERS;
    private final List<String> powers = new ArrayList<>();
    private final List<Path> exports = new ArrayList<>();
    private int scroll;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    public TreeEditorLoadPopupScreen(TreeEditorScreen parent, boolean dirty) {
        super(Component.literal("Load Power"));
        this.parent = parent;
        this.dirty = dirty;
    }

    @Override
    protected void init() {
        super.init();
        this.panelW = Math.min(420, Math.max(280, this.width - 24));
        this.panelH = Math.min(280, Math.max(180, this.height - 24));
        this.panelX = Math.max(0, (this.width - this.panelW) / 2);
        this.panelY = Math.max(0, (this.height - this.panelH) / 2);
        this.powers.clear();
        if (this.minecraft != null && this.minecraft.level != null) {
            var lookup = this.minecraft.level.registryAccess().lookupOrThrow(PalladiumRegistryKeys.POWER);
            lookup.listElementIds().map(key -> key.identifier().toString()).sorted().forEach(this.powers::add);
        }
        if (this.minecraft != null) {
            this.exports.clear();
            this.exports.addAll(TreeEditorExports.listJsonFiles(this.minecraft));
        }
        int tabW = 84;
        this.addRenderableWidget(new TreeEditorFlatButton(this.panelX + 10, this.panelY + 28, tabW, 20, "Powers", () -> {
            this.source = Source.POWERS;
            this.scroll = 0;
        }));
        this.addRenderableWidget(new TreeEditorFlatButton(this.panelX + 98, this.panelY + 28, tabW, 20, "Exports", () -> {
            this.source = Source.EXPORTS;
            this.scroll = 0;
        }));
        this.addRenderableWidget(new TreeEditorFlatButton(this.panelX + 186, this.panelY + 28, 96, 20, "New Power", () ->
                this.confirmThen(() -> this.parent.replaceDraft(TreeEditorDraft.blank()))));
        this.addRenderableWidget(new TreeEditorFlatButton(this.panelX + this.panelW - 86, this.panelY + this.panelH - 28, 76, 20, "Cancel", this::onClose));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.parent.extractRenderState(graphics, Integer.MIN_VALUE, Integer.MIN_VALUE, partialTick);
        graphics.fill(0, 0, this.width, this.height, TreeEditorTheme.OVERLAY);
        TreeEditorTheme.dialog(graphics, this.font, this.panelX, this.panelY, this.panelW, this.panelH, "Open Power");
        int listTop = this.panelY + 54;
        int listBottom = this.panelY + this.panelH - 36;
        int visible = Math.max(1, (listBottom - listTop) / ROW);
        int size = this.source == Source.POWERS ? this.powers.size() : this.exports.size();
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, size - visible)));
        graphics.enableScissor(this.panelX + 6, listTop, this.panelX + this.panelW - 6, listBottom);
        for (int index = 0; index < size; index++) {
            int rowY = listTop + (index - this.scroll) * ROW;
            if (rowY + ROW < listTop || rowY > listBottom) {
                continue;
            }
            String label = this.source == Source.POWERS
                    ? this.powers.get(index)
                    : this.exports.get(index).getFileName().toString();
            boolean hovered = mouseX >= this.panelX + 8 && mouseX < this.panelX + this.panelW - 8
                    && mouseY >= rowY && mouseY < rowY + ROW;
            if (hovered) {
                graphics.fill(this.panelX + 6, rowY, this.panelX + this.panelW - 6, rowY + ROW, TreeEditorTheme.SELECT);
            }
            graphics.text(this.font, label, this.panelX + 10, rowY + 3, TEXT_LIGHT, false);
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
        int listTop = this.panelY + 54;
        int listBottom = this.panelY + this.panelH - 36;
        if (mouseX < this.panelX + 8 || mouseX >= this.panelX + this.panelW - 8 || mouseY < listTop || mouseY >= listBottom) {
            return true;
        }
        int index = this.scroll + (mouseY - listTop) / ROW;
        if (this.source == Source.POWERS && index >= 0 && index < this.powers.size()) {
            this.confirmThen(() -> this.loadPower(this.powers.get(index)));
        } else if (this.source == Source.EXPORTS && index >= 0 && index < this.exports.size()) {
            this.confirmThen(() -> this.loadExport(this.exports.get(index)));
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

    private void confirmThen(Runnable action) {
        if (!this.dirty) {
            action.run();
            return;
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(new TreeEditorConfirmPopupScreen(this, "Discard unsaved changes?", action));
        }
    }

    private void loadPower(String rawId) {
        if (this.minecraft == null || this.minecraft.level == null) {
            return;
        }
        Identifier powerId;
        try {
            powerId = Identifier.parse(rawId);
        } catch (RuntimeException exception) {
            return;
        }
        var lookup = this.minecraft.level.registryAccess().lookupOrThrow(PalladiumRegistryKeys.POWER);
        Holder.Reference<Power> holder = lookup.get(ResourceKey.create(PalladiumRegistryKeys.POWER, powerId)).orElse(null);
        if (holder == null) {
            return;
        }
        String sourceJson = PowerSourceJson.read(this.minecraft, powerId);
        this.parent.replaceDraft(TreeEditorDraft.fromPower(powerId, holder.value(), sourceJson));
    }

    private void loadExport(Path file) {
        if (this.minecraft == null) {
            return;
        }
        try {
            this.parent.replaceDraft(TreeEditorExports.read(this.minecraft, file));
        } catch (Exception exception) {
            try {
                JsonObject root = JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
                this.parent.replaceDraft(TreeEditorDraft.fromJson(TreeEditorDraft.NEW_POWER_ID, root));
            } catch (Exception ignored) {
                // Leave the current draft if the file cannot be parsed.
            }
        }
    }
}
