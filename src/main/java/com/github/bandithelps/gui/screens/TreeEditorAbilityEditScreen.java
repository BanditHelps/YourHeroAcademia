package com.github.bandithelps.gui.screens;

import com.github.bandithelps.gui.tree.TreeEditorCostDraft;
import com.github.bandithelps.gui.tree.TreeEditorCostSchema;
import com.github.bandithelps.gui.tree.TreeEditorDraft;
import com.github.bandithelps.gui.tree.TreeEditorJson;
import com.github.bandithelps.gui.tree.TreeEditorNode;
import com.github.bandithelps.gui.tree.schema.DocField;
import com.github.bandithelps.gui.tree.schema.DocFieldKind;
import com.github.bandithelps.gui.tree.schema.DocSchema;
import com.github.bandithelps.gui.tree.schema.PalladiumDocCatalog;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class TreeEditorAbilityEditScreen extends TreeEditorPopupScreen {
    private enum Tab {
        IDENTITY,
        PROPERTIES,
        FIELDS,
        STATE
    }

    private final TreeEditorScreen tree;
    private final TreeEditorDraft draft;
    private final TreeEditorNode node;
    private final PalladiumDocCatalog catalog;
    private final List<TreeEditorCostSchema> costs;
    private final boolean creating;
    private boolean committed;
    private Tab tab = Tab.IDENTITY;

    private String titleValue;
    private String keyValue;
    private String typeId;
    private String descriptionValue;
    private String lockedDescriptionValue;
    private String iconId;
    private boolean hiddenInGui;
    private boolean hiddenInBar;
    private String listIndex;
    private String activationStamina;
    private String staminaInterval;
    private String staminaIntervalCost;
    private JsonObject typeFields;
    private JsonElement unlocking;
    private JsonElement enabling;
    private final TreeEditorCostDraft cost;

    public TreeEditorAbilityEditScreen(TreeEditorScreen parent, TreeEditorDraft draft, TreeEditorNode node, boolean creating) {
        super(parent, creating ? "New Ability" : "Edit Ability");
        this.tree = parent;
        this.draft = draft;
        this.node = node;
        this.catalog = parent.catalog();
        this.costs = parent.costSchemas();
        this.creating = creating;
        this.titleValue = node.getTitle();
        this.keyValue = node.getKey();
        this.typeId = node.getTypeId();
        this.descriptionValue = node.getDescription();
        this.lockedDescriptionValue = node.getLockedDescription();
        this.iconId = node.getIconId();
        this.hiddenInGui = node.isHiddenInGui();
        this.hiddenInBar = node.isHiddenInBar();
        this.listIndex = Integer.toString(node.getListIndex());
        this.activationStamina = Integer.toString(node.getActivationStamina());
        this.staminaInterval = Integer.toString(node.getStaminaInterval());
        this.staminaIntervalCost = Integer.toString(node.getStaminaIntervalCost());
        this.typeFields = node.getTypeFields().deepCopy();
        this.unlocking = node.getUnlocking() == null ? null : node.getUnlocking().deepCopy();
        this.enabling = node.getEnabling() == null ? null : node.getEnabling().deepCopy();
        this.cost = node.getCost().copy();
        this.cost.setTypeId(this.cost.getTypeId(), this.costs);
        this.panelW = 400;
    }

    @Override
    protected void init() {
        super.init();
        this.rows.clear();
        this.layoutPanel(400);
        int fieldW = this.fieldW();
        int tabW = (this.panelW - 20) / 4;
        this.addTabButton(Tab.IDENTITY, 0, tabW);
        this.addTabButton(Tab.PROPERTIES, 1, tabW);
        this.addTabButton(Tab.FIELDS, 2, tabW);
        this.addTabButton(Tab.STATE, 3, tabW);

        switch (this.tab) {
            case IDENTITY -> this.addIdentity(fieldW);
            case PROPERTIES -> this.addProperties(fieldW);
            case FIELDS -> this.addTypeFields(fieldW);
            case STATE -> this.addState(fieldW);
        }
        this.finishRows();
        this.positionTabs(tabW);
        this.addFooterButton("Save", this.panelX + 12, this::save);
        this.addFooterButton("Cancel", this.panelX + this.panelW - 102, this::onClose);
    }

    private void addTabButton(Tab tab, int index, int tabW) {
        Button button = Button.builder(Component.literal(tabLabel(tab)), clicked -> {
            this.tab = tab;
            this.scroll = 0;
            this.init(this.width, this.height);
        }).bounds(0, 0, tabW, 16).build();
        this.addRenderableWidget(button);
        button.active = this.tab != tab;
    }

    private void positionTabs(int tabW) {
        int x = this.panelX + 8;
        int y = this.panelY + 20;
        int seen = 0;
        for (var child : this.children()) {
            if (child instanceof Button button && seen < 4) {
                button.setRectangle(tabW - 2, 16, x + seen * tabW, y);
                seen++;
            }
        }
    }

    private void addIdentity(int fieldW) {
        EditBox title = new EditBox(this.font, 0, 0, fieldW, FIELD_H, Component.literal("Title"));
        title.setMaxLength(64);
        title.setValue(this.titleValue);
        title.setResponder(value -> this.titleValue = value);
        this.addRow("Title", this.addRenderableWidget(title));

        EditBox key = new EditBox(this.font, 0, 0, fieldW, FIELD_H, Component.literal("Id"));
        key.setMaxLength(48);
        key.setValue(this.keyValue);
        key.setResponder(value -> this.keyValue = value);
        this.addRow("Id", this.addRenderableWidget(key));

        DocSchema schema = this.catalog.findAbility(this.typeId);
        String typeLabel = schema == null ? this.typeId : schema.name();
        this.addRow("Type", this.addRenderableWidget(Button.builder(Component.literal(typeLabel), button ->
                        this.minecraft.setScreen(new TreeEditorTypePickerScreen(this, this.catalog.abilities(), id -> {
                            this.typeId = id;
                            this.typeFields = fieldsFromExample(id);
                        })))
                .bounds(0, 0, fieldW, FIELD_H)
                .build()));
        if (schema != null && !schema.description().isBlank()) {
            EditBox description = new EditBox(this.font, 0, 0, fieldW, FIELD_H, Component.literal("Type info"));
            description.setValue(schema.description());
            description.setEditable(false);
            this.addRow("About", this.addRenderableWidget(description));
        }
    }

    private void addProperties(int fieldW) {
        EditBox description = new EditBox(this.font, 0, 0, fieldW, FIELD_H, Component.literal("Description"));
        description.setMaxLength(1024);
        description.setValue(this.descriptionValue);
        description.setResponder(value -> this.descriptionValue = value);
        this.addRow("Description", this.addRenderableWidget(description));

        EditBox locked = new EditBox(this.font, 0, 0, fieldW, FIELD_H, Component.literal("Locked"));
        locked.setMaxLength(1024);
        locked.setValue(this.lockedDescriptionValue);
        locked.setResponder(value -> this.lockedDescriptionValue = value);
        this.addRow("Locked text", this.addRenderableWidget(locked));

        this.addRow("Icon", this.addRenderableWidget(Button.builder(Component.literal(this.iconId), button ->
                        this.minecraft.setScreen(new TreeEditorPickerScreen(this, "Select Icon", TreeEditorPickerScreen.Mode.ITEMS, id -> this.iconId = id.toString())))
                .bounds(0, 0, fieldW, FIELD_H)
                .build()));
        this.addToggle("Hidden in tree", this.hiddenInGui, value -> this.hiddenInGui = value, fieldW);
        this.addToggle("Hidden in bar", this.hiddenInBar, value -> this.hiddenInBar = value, fieldW);
        this.addNumber("List index", this.listIndex, value -> this.listIndex = value, fieldW);
        this.addNumber("Activation stamina", this.activationStamina, value -> this.activationStamina = value, fieldW);
        this.addNumber("Stamina interval", this.staminaInterval, value -> this.staminaInterval = value, fieldW);
        this.addNumber("Interval cost", this.staminaIntervalCost, value -> this.staminaIntervalCost = value, fieldW);
        this.addRow("Position", this.addRenderableWidget(Button.builder(
                        Component.literal(this.node.getGridX() + ", " + this.node.getGridY()),
                        button -> {
                        })
                .bounds(0, 0, fieldW, FIELD_H)
                .build()));
    }

    private void addTypeFields(int fieldW) {
        DocSchema schema = this.catalog.findAbility(this.typeId);
        if (schema == null || schema.fields().isEmpty()) {
            this.addRow("Fields", this.addRenderableWidget(Button.builder(Component.literal("Edit raw JSON"), button ->
                            this.minecraft.setScreen(new TreeEditorJsonEditScreen(this, "Type fields", this.typeFields, value -> {
                                if (value != null && value.isJsonObject()) {
                                    this.typeFields = value.getAsJsonObject();
                                }
                            })))
                    .bounds(0, 0, fieldW, FIELD_H)
                    .build()));
            return;
        }
        for (DocField field : schema.fields()) {
            this.addAbilityField(field, fieldW);
        }
    }

    private void addAbilityField(DocField field, int fieldW) {
        DocFieldKind kind = field.kind() == DocFieldKind.COMBINED ? combinedKind(field) : field.kind();
        if (kind == DocFieldKind.BOOLEAN) {
            boolean value = this.typeFields.has(field.key())
                    && this.typeFields.get(field.key()).isJsonPrimitive()
                    && this.typeFields.get(field.key()).getAsBoolean();
            this.addToggle(field.label(), value, next -> this.typeFields.addProperty(field.key(), next), fieldW);
            return;
        }
        if (kind == DocFieldKind.ENUM && !field.enumValues().isEmpty()) {
            String current = TreeEditorJson.asText(this.typeFields.get(field.key()));
            if (current.isBlank()) {
                current = field.enumValues().getFirst();
                this.typeFields.addProperty(field.key(), current);
            }
            String shown = current;
            this.addRow(field.label(), this.addRenderableWidget(Button.builder(Component.literal(shown), button -> {
                        int index = field.enumValues().indexOf(shown);
                        this.typeFields.addProperty(field.key(), field.enumValues().get((Math.max(index, 0) + 1) % field.enumValues().size()));
                        this.init(this.width, this.height);
                    })
                    .bounds(0, 0, fieldW, FIELD_H)
                    .build()));
            return;
        }
        if (kind == DocFieldKind.VALUE || kind == DocFieldKind.CONDITION || kind == DocFieldKind.ACTION || kind == DocFieldKind.RAW_JSON
                || kind == DocFieldKind.VEC2 || kind == DocFieldKind.VEC3 || kind == DocFieldKind.STRING_LIST) {
            TreeEditorTypedObjectScreen.Kind typed = kind == DocFieldKind.CONDITION
                    ? TreeEditorTypedObjectScreen.Kind.CONDITION
                    : kind == DocFieldKind.ACTION
                    ? TreeEditorTypedObjectScreen.Kind.ACTION
                    : TreeEditorTypedObjectScreen.Kind.VALUE;
            this.addRow(field.label(), this.addRenderableWidget(Button.builder(Component.literal(TreeEditorJson.summary(this.typeFields.get(field.key()))), button -> {
                        if (kind == DocFieldKind.VALUE || kind == DocFieldKind.CONDITION || kind == DocFieldKind.ACTION) {
                            this.minecraft.setScreen(new TreeEditorTypedObjectScreen(this, typed, this.catalog, this.costs, this.typeFields.get(field.key()), value -> {
                                if (value == null) {
                                    this.typeFields.remove(field.key());
                                } else {
                                    this.typeFields.add(field.key(), value);
                                }
                            }));
                        } else {
                            this.minecraft.setScreen(new TreeEditorJsonEditScreen(this, field.key(), this.typeFields.get(field.key()), value -> {
                                if (value == null) {
                                    this.typeFields.remove(field.key());
                                } else {
                                    this.typeFields.add(field.key(), value);
                                }
                            }));
                        }
                    })
                    .bounds(0, 0, fieldW, FIELD_H)
                    .build()));
            return;
        }
        if (kind == DocFieldKind.ITEM || kind == DocFieldKind.ICON) {
            this.addRow(field.label(), this.addRenderableWidget(Button.builder(Component.literal(TreeEditorJson.asText(this.typeFields.get(field.key()))), button ->
                            this.minecraft.setScreen(new TreeEditorPickerScreen(this, "Select Item", TreeEditorPickerScreen.Mode.ITEMS, id ->
                                    this.typeFields.addProperty(field.key(), id.toString()))))
                    .bounds(0, 0, fieldW, FIELD_H)
                    .build()));
            return;
        }
        EditBox box = new EditBox(this.font, 0, 0, fieldW, FIELD_H, Component.literal(field.key()));
        box.setMaxLength(512);
        box.setValue(TreeEditorJson.asText(this.typeFields.get(field.key())));
        box.setResponder(value -> {
            if (kind == DocFieldKind.INTEGER) {
                TreeEditorJson.putNumber(this.typeFields, field.key(), value, true);
            } else if (kind == DocFieldKind.FLOAT) {
                TreeEditorJson.putNumber(this.typeFields, field.key(), value, false);
            } else if (value.isBlank()) {
                this.typeFields.remove(field.key());
            } else {
                this.typeFields.addProperty(field.key(), value);
            }
        });
        this.addRow(field.label(), this.addRenderableWidget(box));
    }

    private void addState(int fieldW) {
        this.addRow("Unlocking", this.addRenderableWidget(Button.builder(Component.literal(TreeEditorJson.summary(this.unlocking)), button ->
                        this.minecraft.setScreen(new TreeEditorTypedObjectScreen(this, TreeEditorTypedObjectScreen.Kind.CONDITION, this.catalog, this.costs, this.unlocking, value -> this.unlocking = value)))
                .bounds(0, 0, fieldW, FIELD_H)
                .build()));
        this.addRow("Enabling", this.addRenderableWidget(Button.builder(Component.literal(TreeEditorJson.summary(this.enabling)), button ->
                        this.minecraft.setScreen(new TreeEditorTypedObjectScreen(this, TreeEditorTypedObjectScreen.Kind.CONDITION, this.catalog, this.costs, this.enabling, value -> this.enabling = value)))
                .bounds(0, 0, fieldW, FIELD_H)
                .build()));
        this.addRow("Cost", this.addRenderableWidget(Button.builder(Component.literal(this.cost.summary(this.costs)), button -> {
                    int index = 0;
                    for (int i = 0; i < this.costs.size(); i++) {
                        if (this.costs.get(i).id().equals(this.cost.getTypeId())) {
                            index = i;
                            break;
                        }
                    }
                    this.cost.setTypeId(this.costs.get((index + 1) % this.costs.size()).id(), this.costs);
                    this.init(this.width, this.height);
                })
                .bounds(0, 0, fieldW, FIELD_H)
                .build()));
        TreeEditorCostSchema schema = TreeEditorCostDraft.find(this.costs, this.cost.getTypeId());
        if (schema != null) {
            for (TreeEditorCostSchema.Field field : schema.fields()) {
                if (field.itemLike()) {
                    String key = field.key();
                    this.addRow(field.key(), this.addRenderableWidget(Button.builder(Component.literal(this.cost.get(key)), button ->
                                    this.minecraft.setScreen(new TreeEditorPickerScreen(this, "Select Item", TreeEditorPickerScreen.Mode.ITEMS, id -> this.cost.set(key, id.toString()))))
                            .bounds(0, 0, fieldW, FIELD_H)
                            .build()));
                } else {
                    EditBox box = new EditBox(this.font, 0, 0, fieldW, FIELD_H, Component.literal(field.key()));
                    box.setMaxLength(64);
                    box.setValue(this.cost.get(field.key()));
                    box.setResponder(value -> this.cost.set(field.key(), value));
                    this.addRow(field.key(), this.addRenderableWidget(box));
                }
            }
        }
    }

    private void addToggle(String label, boolean value, java.util.function.Consumer<Boolean> setter, int fieldW) {
        this.addRow(label, this.addRenderableWidget(Button.builder(Component.literal(value ? "On" : "Off"), button -> {
                    setter.accept(!value);
                    this.init(this.width, this.height);
                })
                .bounds(0, 0, fieldW, FIELD_H)
                .build()));
    }

    private void addNumber(String label, String value, java.util.function.Consumer<String> setter, int fieldW) {
        EditBox box = new EditBox(this.font, 0, 0, fieldW, FIELD_H, Component.literal(label));
        box.setMaxLength(8);
        box.setValue(value);
        box.setResponder(setter);
        this.addRow(label, this.addRenderableWidget(box));
    }

    private JsonObject fieldsFromExample(String typeId) {
        DocSchema schema = this.catalog.findAbility(typeId);
        JsonObject fields = new JsonObject();
        if (schema != null && schema.example() != null && schema.example().isJsonObject()) {
            JsonObject example = schema.example().getAsJsonObject();
            for (var entry : example.entrySet()) {
                if (!"type".equals(entry.getKey()) && !"properties".equals(entry.getKey()) && !"state".equals(entry.getKey())) {
                    fields.add(entry.getKey(), entry.getValue().deepCopy());
                }
            }
        }
        return fields;
    }

    private void save() {
        if (this.titleValue.trim().isEmpty()) {
            this.error = "Title required";
            return;
        }
        DocSchema schema = this.catalog.findAbility(this.typeId);
        if (schema != null) {
            for (DocField field : schema.fields()) {
                if (!field.required()) {
                    continue;
                }
                JsonElement value = this.typeFields.get(field.key());
                if (value == null || value.isJsonNull() || (value.isJsonPrimitive() && value.getAsString().isBlank())) {
                    this.error = field.key() + " required";
                    this.tab = Tab.FIELDS;
                    this.init(this.width, this.height);
                    return;
                }
            }
        }
        if (!this.draft.rename(this.node, this.keyValue)) {
            this.error = "Id is invalid or taken";
            return;
        }
        this.node.setTitle(this.titleValue.trim());
        this.node.setTypeId(this.typeId);
        this.node.setDescription(this.descriptionValue.trim());
        this.node.setLockedDescription(this.lockedDescriptionValue.trim());
        this.node.setIconId(this.iconId);
        this.node.setHiddenInGui(this.hiddenInGui);
        this.node.setHiddenInBar(this.hiddenInBar);
        this.node.setListIndex(parseInt(this.listIndex, 0));
        this.node.setActivationStamina(parseInt(this.activationStamina, 0));
        this.node.setStaminaInterval(parseInt(this.staminaInterval, 0));
        this.node.setStaminaIntervalCost(parseInt(this.staminaIntervalCost, 0));
        this.node.setTypeFields(this.typeFields);
        this.node.setUnlocking(this.unlocking);
        this.node.setEnabling(this.enabling);
        this.node.setCost(this.cost);
        com.github.bandithelps.gui.tree.TreeEditorStateSync.applyParentAndCost(this.node, this.costs);
        this.draft.markDirty();
        this.committed = true;
        this.onClose();
    }

    @Override
    protected int contentTop() {
        return this.panelY + 40;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.drawChrome(graphics, this.creating ? "New Ability" : "Edit Ability");
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
    public void onClose() {
        if (this.creating && !this.committed) {
            this.tree.discardCreatedNode(this.node);
        }
        super.onClose();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static DocFieldKind combinedKind(DocField field) {
        String text = field.typeText().toLowerCase();
        if (text.contains("condition")) {
            return DocFieldKind.CONDITION;
        }
        if (text.contains("action")) {
            return DocFieldKind.ACTION;
        }
        if (text.contains("value")) {
            return DocFieldKind.VALUE;
        }
        return DocFieldKind.RAW_JSON;
    }

    private static String tabLabel(Tab tab) {
        return switch (tab) {
            case IDENTITY -> "Identity";
            case PROPERTIES -> "Props";
            case FIELDS -> "Fields";
            case STATE -> "State";
        };
    }
}
