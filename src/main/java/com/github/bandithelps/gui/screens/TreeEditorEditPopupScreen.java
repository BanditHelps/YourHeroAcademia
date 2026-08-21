package com.github.bandithelps.gui.screens;

import com.github.bandithelps.gui.tree.TreeEditorCostDraft;
import com.github.bandithelps.gui.tree.TreeEditorCostSchema;
import com.github.bandithelps.gui.tree.TreeEditorDraft;
import com.github.bandithelps.gui.tree.TreeEditorNode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class TreeEditorEditPopupScreen extends Screen {
    private static final int POPUP_WIDTH = 240;
    private static final int MARGIN = 8;
    private static final int HEADER = 16;
    private static final int FOOTER = 26;
    private static final int FIELD_H = 16;
    private static final int COST_ROW = 30;
    private static final int BODY_PAD = 12;
    private static final int TEXT_LIGHT = 0xFFFFFFFF;

    private final TreeEditorScreen parent;
    private final TreeEditorDraft draft;
    private final TreeEditorNode node;
    private final List<TreeEditorCostSchema> schemas;
    private final List<BodyItem> body = new ArrayList<>();
    private final boolean creating;
    private EditBox keyBox;

    private String titleValue;
    private String keyValue;
    private String descriptionValue;
    private String lockedDescriptionValue;
    private final boolean splitDescription;
    private String iconId;
    private boolean keyLocked;
    private final TreeEditorCostDraft cost;
    private String error = "";
    private int panelX;
    private int panelY;
    private int panelH;
    private int contentHeight;
    private int scroll;
    private boolean committed;

    public TreeEditorEditPopupScreen(TreeEditorScreen parent, TreeEditorDraft draft, TreeEditorNode node) {
        this(parent, draft, node, false);
    }

    public TreeEditorEditPopupScreen(TreeEditorScreen parent, TreeEditorDraft draft, TreeEditorNode node, boolean creating) {
        super(Component.literal("Edit Tree Node"));
        this.parent = parent;
        this.draft = draft;
        this.node = node;
        this.creating = creating;
        this.schemas = parent.costSchemas();
        this.titleValue = node.getTitle();
        this.keyValue = node.getKey();
        this.descriptionValue = node.getDescription();
        this.lockedDescriptionValue = node.getLockedDescription();
        this.splitDescription = node.hasSplitDescription();
        this.iconId = node.getIconId();
        this.cost = node.getCost();
        this.cost.setTypeId(this.cost.getTypeId(), this.schemas);
        this.keyLocked = !node.isCreated() || !TreeEditorDraft.keyFromTitle(this.titleValue).equals(this.keyValue);
    }

    @Override
    protected void init() {
        super.init();
        this.body.clear();
        this.layoutPanel();
        int fieldW = this.fieldW();

        EditBox titleBox = new EditBox(this.font, 0, 0, fieldW, FIELD_H, Component.literal("Title"));
        titleBox.setMaxLength(64);
        titleBox.setValue(this.titleValue);
        titleBox.setResponder(value -> {
            this.titleValue = value;
            if (this.node.isCreated() && !this.keyLocked && this.keyBox != null) {
                this.keyValue = TreeEditorDraft.keyFromTitle(value);
                this.keyBox.setValue(this.keyValue);
            }
        });
        this.addBody("Title", this.addRenderableWidget(titleBox), 10, 0, fieldW, null);

        this.keyBox = new EditBox(this.font, 0, 0, fieldW, FIELD_H, Component.literal("Id"));
        this.keyBox.setMaxLength(48);
        this.keyBox.setValue(this.keyValue);
        this.keyBox.setEditable(this.node.isCreated());
        this.keyBox.setResponder(value -> {
            if (this.node.isCreated() && this.keyBox.isFocused()) {
                this.keyLocked = true;
                this.keyValue = value;
            }
        });
        this.addBody("Id", this.addRenderableWidget(this.keyBox), 38, 0, fieldW, null);

        this.addBody("Icon", this.addRenderableWidget(Button.builder(Component.literal("Choose"), button -> this.openIconPicker())
                .bounds(0, 0, 110, FIELD_H)
                .build()), 66, 22, 110, () -> this.iconId);

        EditBox descriptionBox = new EditBox(this.font, 0, 0, fieldW, FIELD_H, Component.literal("Description"));
        descriptionBox.setMaxLength(1024);
        descriptionBox.setValue(this.descriptionValue);
        descriptionBox.setResponder(value -> this.descriptionValue = value);
        this.addBody(this.splitDescription ? "Unlocked Description" : "Description", this.addRenderableWidget(descriptionBox), 94, 0, fieldW, null);
        if (this.splitDescription) {
            EditBox lockedBox = new EditBox(this.font, 0, 0, fieldW, FIELD_H, Component.literal("Locked Description"));
            lockedBox.setMaxLength(1024);
            lockedBox.setValue(this.lockedDescriptionValue);
            lockedBox.setResponder(value -> this.lockedDescriptionValue = value);
            this.addBody("Locked Description", this.addRenderableWidget(lockedBox), 122, 0, fieldW, null);
        }

        this.addBody("Cost", this.addRenderableWidget(Button.builder(Component.literal(this.costTypeLabel()), button -> this.cycleCostType())
                .bounds(0, 0, fieldW, FIELD_H)
                .build()), this.costRelY(), 0, fieldW, null);
        this.addCostFields(this.costRelY() + COST_ROW, fieldW);

        this.contentHeight = this.bodyBottom() + BODY_PAD;
        this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll()));
        this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> this.save())
                .bounds(this.panelX + 10, this.panelY + this.panelH - 22, 92, 18)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
                .bounds(this.panelX + POPUP_WIDTH - 102, this.panelY + this.panelH - 22, 92, 18)
                .build());
        this.applyScroll();
        this.setInitialFocus(titleBox);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.parent.extractRenderState(graphics, Integer.MIN_VALUE, Integer.MIN_VALUE, partialTick);
        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        int x = this.panelX;
        int y = this.panelY;
        graphics.fill(x, y, x + POPUP_WIDTH, y + this.panelH, 0xFF2B2B2B);
        graphics.fill(x + 1, y + 1, x + POPUP_WIDTH - 1, y + this.panelH - 1, 0xFFC6C6C6);
        graphics.fill(x + 4, y + 4, x + POPUP_WIDTH - 4, y + this.panelH - FOOTER, 0xFF000000);
        graphics.centeredText(this.font, this.node.isCreated() ? "New Node" : "Edit Node", x + POPUP_WIDTH / 2, y + 6, TEXT_LIGHT);

        int clipTop = this.contentTop();
        int clipBottom = this.contentBottom();
        graphics.enableScissor(x + 4, clipTop, x + POPUP_WIDTH - 4, clipBottom);
        for (BodyItem item : this.body) {
            int labelY = clipTop + item.relY - 10 - this.scroll;
            int widgetY = clipTop + item.relY - this.scroll;
            if (labelY + 8 > clipTop && labelY < clipBottom) {
                graphics.text(this.font, item.label, x + 10, labelY, item.showItem() ? TEXT_LIGHT : 0xFFDDDDDD, false);
            }
            if (item.showItem() && widgetY + FIELD_H > clipTop && widgetY < clipBottom) {
                this.drawItem(graphics, x + 10, widgetY, item.itemId().get());
            }
        }
        graphics.disableScissor();
        this.drawScrollbar(graphics);

        if (!this.error.isEmpty()) {
            graphics.text(this.font, this.error, x + 108, y + this.panelH - 18, 0xFFFF5555, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        super.mouseClicked(event, doubleClick);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.isInPanel((int) mouseX, (int) mouseY) && this.maxScroll() > 0) {
            this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int) Math.signum(scrollY) * COST_ROW));
            this.applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        if (this.creating && !this.committed) {
            this.parent.discardCreatedNode(this.node);
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void cycleCostType() {
        List<TreeEditorCostSchema> options = this.costOptions();
        int index = 0;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).id().equals(this.cost.getTypeId())) {
                index = i;
                break;
            }
        }
        this.cost.setTypeId(options.get((index + 1) % options.size()).id(), options);
        this.scroll = 0;
        this.init(this.width, this.height);
    }

    private void addCostFields(int startRelY, int fieldW) {
        TreeEditorCostSchema schema = TreeEditorCostDraft.find(this.costOptions(), this.cost.getTypeId());
        if (schema == null || schema.fields().isEmpty()) {
            return;
        }
        int relY = startRelY;
        for (TreeEditorCostSchema.Field field : schema.fields()) {
            AbstractWidget widget;
            if (field.itemLike()) {
                String key = field.key();
                widget = Button.builder(Component.literal("Choose"), button -> this.openCostItemPicker(key))
                        .bounds(0, 0, 110, FIELD_H)
                        .build();
                this.addBody(field.key(), this.addRenderableWidget(widget), relY, 22, 110, () -> this.cost.get(key));
            } else {
                EditBox box = new EditBox(this.font, 0, 0, fieldW, FIELD_H, Component.literal(field.key()));
                box.setMaxLength(64);
                box.setValue(this.cost.get(field.key()));
                box.setResponder(value -> this.cost.set(field.key(), value));
                this.addBody(field.key(), this.addRenderableWidget(box), relY, 0, fieldW, null);
            }
            relY += COST_ROW;
        }
    }

    private void openIconPicker() {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(new TreeEditorPickerScreen(this, "Select Icon", TreeEditorPickerScreen.Mode.ITEMS, id -> {
            this.iconId = id.toString();
        }));
    }

    private void openCostItemPicker(String fieldKey) {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(new TreeEditorPickerScreen(this, "Select Item", TreeEditorPickerScreen.Mode.ITEMS, id -> {
            this.cost.set(fieldKey, id.toString());
        }));
    }

    private void drawItem(GuiGraphicsExtractor graphics, int x, int y, String rawId) {
        Identifier id = this.parseItemId(rawId);
        if (id == null) {
            return;
        }
        try {
            var item = BuiltInRegistries.ITEM.get(id);
            if (item.isPresent()) {
                graphics.item(new ItemStack(item.get()), x, y);
            }
        } catch (RuntimeException ignored) {
            // Invalid item id; leave the slot empty.
        }
    }

    @Nullable
    private Identifier parseItemId(String rawId) {
        if (rawId == null || rawId.isBlank() || rawId.charAt(0) == '{') {
            return null;
        }
        try {
            return Identifier.parse(rawId.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void save() {
        String title = this.titleValue.trim();
        if (title.isEmpty()) {
            this.error = "Title required";
            return;
        }
        TreeEditorCostSchema schema = TreeEditorCostDraft.find(this.costOptions(), this.cost.getTypeId());
        if (schema != null && !this.cost.isNone()) {
            for (TreeEditorCostSchema.Field field : schema.fields()) {
                String value = this.cost.get(field.key());
                if (value.isBlank()) {
                    this.error = field.key() + " required";
                    return;
                }
                if (field.numeric()) {
                    try {
                        if (Integer.parseInt(value.trim()) < 1) {
                            this.error = field.key() + " must be >= 1";
                            return;
                        }
                    } catch (NumberFormatException exception) {
                        this.error = field.key() + " must be a number";
                        return;
                    }
                }
            }
        }
        if (this.node.isCreated() && !this.draft.rename(this.node, this.keyValue)) {
            this.error = "Id is invalid or taken";
            return;
        }
        this.node.setTitle(title);
        this.node.setDescription(this.descriptionValue.trim());
        this.node.setLockedDescription(this.splitDescription ? this.lockedDescriptionValue.trim() : "");
        this.node.setIconId(this.iconId);
        this.node.setCost(this.cost);
        this.committed = true;
        this.onClose();
    }

    private List<TreeEditorCostSchema> costOptions() {
        if (this.schemas.isEmpty()) {
            return List.of(TreeEditorCostSchema.NONE);
        }
        return this.schemas;
    }

    private String costTypeLabel() {
        TreeEditorCostSchema schema = TreeEditorCostDraft.find(this.costOptions(), this.cost.getTypeId());
        return schema == null ? "None" : schema.name();
    }

    private void addBody(String label, AbstractWidget widget, int relY, int xOffset, int width, @Nullable Supplier<String> itemId) {
        this.body.add(new BodyItem(label, widget, relY, xOffset, width, itemId));
    }

    private void layoutPanel() {
        int lastRelY = this.costFieldCount() == 0
                ? this.costRelY() + FIELD_H
                : this.costRelY() + COST_ROW + (this.costFieldCount() - 1) * COST_ROW + FIELD_H;
        int needed = HEADER + lastRelY + BODY_PAD + FOOTER;
        this.panelH = Math.min(needed, Math.max(HEADER + 80 + FOOTER, this.height - MARGIN * 2));
        this.panelX = Math.max(0, (this.width - Math.min(POPUP_WIDTH, this.width)) / 2);
        this.panelY = Math.max(0, (this.height - this.panelH) / 2);
    }

    private void applyScroll() {
        this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll()));
        int top = this.contentTop();
        int bottom = this.contentBottom();
        for (BodyItem item : this.body) {
            int x = this.panelX + 10 + item.xOffset;
            int y = top + item.relY - this.scroll;
            item.widget.setRectangle(item.width, FIELD_H, x, y);
            boolean inView = y >= top && y + FIELD_H <= bottom;
            item.widget.visible = inView;
            item.widget.active = inView;
            if (!inView && item.widget.isFocused()) {
                item.widget.setFocused(false);
            }
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        int max = this.maxScroll();
        if (max <= 0) {
            return;
        }
        int trackX = this.panelX + POPUP_WIDTH - 7;
        int trackY = this.contentTop();
        int trackH = this.contentBottom() - trackY;
        int thumbH = Math.max(12, trackH * trackH / Math.max(trackH, this.contentHeight));
        int thumbY = trackY + (trackH - thumbH) * this.scroll / max;
        graphics.fill(trackX, trackY, trackX + 3, trackY + trackH, 0xFF3A3A3A);
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFFB0B0B0);
    }

    private int costRelY() {
        return this.splitDescription ? 150 : 122;
    }

    private int costFieldCount() {
        TreeEditorCostSchema schema = TreeEditorCostDraft.find(this.costOptions(), this.cost.getTypeId());
        return schema == null ? 0 : schema.fields().size();
    }

    private int contentTop() {
        return this.panelY + HEADER;
    }

    private int contentBottom() {
        return this.panelY + this.panelH - FOOTER;
    }

    private int bodyBottom() {
        int bottom = 0;
        for (BodyItem item : this.body) {
            bottom = Math.max(bottom, item.relY + FIELD_H);
        }
        return bottom;
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight - (this.contentBottom() - this.contentTop()));
    }

    private int fieldW() {
        return POPUP_WIDTH - 26;
    }

    private boolean isInPanel(int mouseX, int mouseY) {
        return mouseX >= this.panelX && mouseX < this.panelX + POPUP_WIDTH
                && mouseY >= this.panelY && mouseY < this.panelY + this.panelH;
    }

    private record BodyItem(String label, AbstractWidget widget, int relY, int xOffset, int width, @Nullable Supplier<String> itemId) {
        boolean showItem() {
            return this.itemId != null;
        }
    }
}
