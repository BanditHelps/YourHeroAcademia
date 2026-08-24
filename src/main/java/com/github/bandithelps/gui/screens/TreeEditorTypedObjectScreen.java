package com.github.bandithelps.gui.screens;

import com.github.bandithelps.gui.tree.TreeEditorCostDraft;
import com.github.bandithelps.gui.tree.TreeEditorCostSchema;
import com.github.bandithelps.gui.tree.TreeEditorJson;
import com.github.bandithelps.gui.tree.TreeEditorStateSync;
import com.github.bandithelps.gui.tree.schema.DocField;
import com.github.bandithelps.gui.tree.schema.DocFieldKind;
import com.github.bandithelps.gui.tree.schema.DocSchema;
import com.github.bandithelps.gui.tree.schema.PalladiumDocCatalog;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TreeEditorTypedObjectScreen extends TreeEditorPopupScreen {
    public enum Kind {
        CONDITION,
        ACTION,
        VALUE
    }

    private final Kind kind;
    private final PalladiumDocCatalog catalog;
    private final List<TreeEditorCostSchema> costs;
    private final Consumer<JsonElement> onSave;
    @Nullable
    private JsonElement current;
    private boolean literalValue;

    public TreeEditorTypedObjectScreen(
            Screen parent,
            Kind kind,
            PalladiumDocCatalog catalog,
            List<TreeEditorCostSchema> costs,
            @Nullable JsonElement current,
            Consumer<JsonElement> onSave
    ) {
        super(parent, titleFor(kind));
        this.kind = kind;
        this.catalog = catalog;
        this.costs = costs;
        this.current = current == null ? null : current.deepCopy();
        this.onSave = onSave;
        this.literalValue = kind == Kind.VALUE && (current == null || current.isJsonPrimitive() || current.isJsonNull());
        this.panelW = 400;
    }

    @Override
    protected void init() {
        super.init();
        this.rows.clear();
        this.layoutPanel(400);
        int fieldW = this.fieldW();

        if (this.kind == Kind.VALUE) {
            Button mode = this.addRenderableWidget(Button.builder(Component.literal(this.literalValue ? "Mode: Literal" : "Mode: Typed"), button -> {
                this.literalValue = !this.literalValue;
                if (this.literalValue && this.current != null && this.current.isJsonObject()) {
                    this.current = new JsonPrimitive("");
                } else if (!this.literalValue && (this.current == null || this.current.isJsonPrimitive())) {
                    this.current = typedObject("palladium:molang_float");
                }
                this.init(this.width, this.height);
            })
                    .bounds(0, 0, fieldW, FIELD_H)
                    .build());
            this.addRow("Input", mode);
        }

        this.rebuildBody(fieldW);
        this.finishRows();
        this.addFooterButton("Save", this.panelX + 12, this::save);
        this.addRenderableWidget(new TreeEditorFlatButton(this.panelX + 110, this.panelY + this.panelH - 28, 80, 22, "Clear", () -> {
            this.current = null;
            this.onSave.accept(null);
            this.onClose();
        }));
        this.addFooterButton("Cancel", this.panelX + this.panelW - 102, this::onClose);
    }

    private void rebuildBody(int fieldW) {
        if (this.kind == Kind.VALUE && this.literalValue) {
            EditBox box = new EditBox(this.font, 0, 0, fieldW, FIELD_H, Component.literal("Literal"));
            box.setMaxLength(512);
            box.setValue(TreeEditorJson.asText(this.current));
            box.setResponder(value -> this.current = parseLiteral(value));
            this.addRow("Value", this.addRenderableWidget(box));
            return;
        }

        String type = TreeEditorJson.readType(this.current);
        this.addRow("Type", this.addRenderableWidget(Button.builder(Component.literal(type.isEmpty() ? "Choose..." : type), button -> this.openTypePicker())
                .bounds(0, 0, fieldW, FIELD_H)
                .build()));

        if (this.kind == Kind.CONDITION && TreeEditorStateSync.TYPE_BUYABLE.equals(type)) {
            this.addBuyableFields(fieldW);
            return;
        }
        if (this.kind == Kind.CONDITION && TreeEditorStateSync.TYPE_KEY_BIND.equals(type)) {
            this.addKeyBindFields(fieldW);
            return;
        }
        if (this.kind == Kind.CONDITION && isLogic(type)) {
            this.addLogicFields(fieldW);
            return;
        }
        if (this.kind == Kind.VALUE && "yha:upgrade_switch".equals(type)) {
            this.addUpgradeSwitchFields(fieldW);
            return;
        }

        DocSchema schema = this.schemaFor(type);
        if (schema == null) {
            this.addRow("Raw", this.addRenderableWidget(Button.builder(Component.literal("Edit JSON"), button -> this.openJson())
                    .bounds(0, 0, fieldW, FIELD_H)
                    .build()));
            return;
        }
        JsonObject object = TreeEditorJson.objectOrNew(this.current);
        object.addProperty("type", type);
        this.current = object;
        for (DocField field : schema.fields()) {
            this.addSchemaField(field, object, fieldW);
        }
    }

    private void addSchemaField(DocField field, JsonObject object, int fieldW) {
        DocFieldKind kind = effectiveKind(field);
        String label = field.label();
        if (kind == DocFieldKind.BOOLEAN) {
            boolean value = object.has(field.key()) && object.get(field.key()).isJsonPrimitive() && object.get(field.key()).getAsBoolean();
            this.addRow(label, this.addRenderableWidget(Button.builder(Component.literal(value ? "On" : "Off"), button -> {
                        boolean next = !value;
                        object.addProperty(field.key(), next);
                        this.current = object;
                        this.init(this.width, this.height);
                    })
                    .bounds(0, 0, fieldW, FIELD_H)
                    .build()));
            return;
        }
        if (kind == DocFieldKind.ENUM && !field.enumValues().isEmpty()) {
            String current = TreeEditorJson.asText(object.get(field.key()));
            if (current.isBlank()) {
                current = field.enumValues().getFirst();
                object.addProperty(field.key(), current);
            }
            String shown = current;
            this.addRow(label, this.addRenderableWidget(Button.builder(Component.literal(shown), button -> {
                        int index = field.enumValues().indexOf(shown);
                        String next = field.enumValues().get((Math.max(index, 0) + 1) % field.enumValues().size());
                        object.addProperty(field.key(), next);
                        this.current = object;
                        this.init(this.width, this.height);
                    })
                    .bounds(0, 0, fieldW, FIELD_H)
                    .build()));
            return;
        }
        if (kind == DocFieldKind.CONDITION) {
            this.addRow(label, this.addRenderableWidget(Button.builder(Component.literal(TreeEditorJson.summary(object.get(field.key()))), button ->
                            this.minecraft.setScreen(new TreeEditorTypedObjectScreen(this, Kind.CONDITION, this.catalog, this.costs, object.get(field.key()), value -> {
                                if (value == null) {
                                    object.remove(field.key());
                                } else {
                                    object.add(field.key(), value);
                                }
                                this.current = object;
                            })))
                    .bounds(0, 0, fieldW, FIELD_H)
                    .build()));
            return;
        }
        if (kind == DocFieldKind.ACTION) {
            this.addRow(label, this.addRenderableWidget(Button.builder(Component.literal(TreeEditorJson.summary(object.get(field.key()))), button ->
                            this.minecraft.setScreen(new TreeEditorTypedObjectScreen(this, Kind.ACTION, this.catalog, this.costs, object.get(field.key()), value -> {
                                if (value == null) {
                                    object.remove(field.key());
                                } else {
                                    object.add(field.key(), value);
                                }
                                this.current = object;
                            })))
                    .bounds(0, 0, fieldW, FIELD_H)
                    .build()));
            return;
        }
        if (kind == DocFieldKind.VALUE) {
            this.addRow(label, this.addRenderableWidget(Button.builder(Component.literal(TreeEditorJson.summary(object.get(field.key()))), button ->
                            this.minecraft.setScreen(new TreeEditorTypedObjectScreen(this, Kind.VALUE, this.catalog, this.costs, object.get(field.key()), value -> {
                                if (value == null) {
                                    object.remove(field.key());
                                } else {
                                    object.add(field.key(), value);
                                }
                                this.current = object;
                            })))
                    .bounds(0, 0, fieldW, FIELD_H)
                    .build()));
            return;
        }
        if (kind == DocFieldKind.STRING_LIST) {
            this.addRow(label, this.addRenderableWidget(Button.builder(Component.literal(TreeEditorJson.summary(object.get(field.key()))), button ->
                            this.minecraft.setScreen(new TreeEditorStringListScreen(this, field.key(), object.get(field.key()), value -> {
                                if (value == null) {
                                    object.remove(field.key());
                                } else {
                                    object.add(field.key(), value);
                                }
                                this.current = object;
                            })))
                    .bounds(0, 0, fieldW, FIELD_H)
                    .build()));
            return;
        }
        if (kind == DocFieldKind.RAW_JSON || kind == DocFieldKind.VEC2 || kind == DocFieldKind.VEC3) {
            this.addRow(label, this.addRenderableWidget(Button.builder(Component.literal(TreeEditorJson.summary(object.get(field.key()))), button ->
                            this.minecraft.setScreen(new TreeEditorJsonEditScreen(this, field.key(), object.get(field.key()), value -> {
                                if (value == null) {
                                    object.remove(field.key());
                                } else {
                                    object.add(field.key(), value);
                                }
                                this.current = object;
                            })))
                    .bounds(0, 0, fieldW, FIELD_H)
                    .build()));
            return;
        }
        EditBox box = new EditBox(this.font, 0, 0, fieldW, FIELD_H, Component.literal(field.key()));
        box.setMaxLength(512);
        box.setValue(TreeEditorJson.asText(object.get(field.key())));
        box.setResponder(value -> {
            if (kind == DocFieldKind.INTEGER) {
                TreeEditorJson.putNumber(object, field.key(), value, true);
            } else if (kind == DocFieldKind.FLOAT) {
                TreeEditorJson.putNumber(object, field.key(), value, false);
            } else if (value.isBlank()) {
                object.remove(field.key());
            } else {
                object.addProperty(field.key(), value);
            }
            this.current = object;
        });
        this.addRow(label, this.addRenderableWidget(box));
    }

    private void addBuyableFields(int fieldW) {
        JsonObject object = TreeEditorJson.objectOrNew(this.current);
        object.addProperty("type", TreeEditorStateSync.TYPE_BUYABLE);
        TreeEditorCostDraft draft = TreeEditorCostDraft.fromJson(object);
        this.addRow("Cost", this.addRenderableWidget(Button.builder(Component.literal(draft.summary(this.costs)), button -> {
                    List<TreeEditorCostSchema> options = this.costs;
                    int index = 0;
                    for (int i = 0; i < options.size(); i++) {
                        if (options.get(i).id().equals(draft.getTypeId())) {
                            index = i;
                            break;
                        }
                    }
                    draft.setTypeId(options.get((index + 1) % options.size()).id(), options);
                    JsonObject next = draft.toJson(options);
                    if (next == null) {
                        object.remove("cost");
                    } else {
                        object.add("cost", next);
                    }
                    this.current = object;
                    this.init(this.width, this.height);
                })
                .bounds(0, 0, fieldW, FIELD_H)
                .build()));
        TreeEditorCostSchema schema = TreeEditorCostDraft.find(this.costs, draft.getTypeId());
        if (schema != null) {
            for (TreeEditorCostSchema.Field field : schema.fields()) {
                EditBox box = new EditBox(this.font, 0, 0, fieldW, FIELD_H, Component.literal(field.key()));
                box.setMaxLength(64);
                box.setValue(draft.get(field.key()));
                box.setResponder(value -> {
                    draft.set(field.key(), value);
                    JsonObject next = draft.toJson(this.costs);
                    if (next != null) {
                        object.add("cost", next);
                    }
                    this.current = object;
                });
                this.addRow(field.key(), this.addRenderableWidget(box));
            }
        }
        this.addNestedConditionList(object, "requires", fieldW, "Requires", false);
        this.current = object;
    }

    private void addKeyBindFields(int fieldW) {
        JsonObject object = TreeEditorJson.objectOrNew(this.current);
        object.addProperty("type", TreeEditorStateSync.TYPE_KEY_BIND);
        String behaviour = object.has("behaviour") && object.get("behaviour").isJsonPrimitive()
                ? object.get("behaviour").getAsString()
                : "activation";
        this.addRow("Behaviour", this.addRenderableWidget(Button.builder(Component.literal(behaviour), button -> {
                    object.addProperty("behaviour", "held".equals(behaviour) ? "activation" : "held");
                    this.current = object;
                    this.init(this.width, this.height);
                })
                .bounds(0, 0, fieldW, FIELD_H)
                .build()));
        EditBox cooldown = new EditBox(this.font, 0, 0, fieldW, FIELD_H, Component.literal("Cooldown"));
        cooldown.setMaxLength(8);
        cooldown.setValue(object.has("cooldown") ? TreeEditorJson.asText(object.get("cooldown")) : "");
        cooldown.setResponder(value -> TreeEditorJson.putNumber(object, "cooldown", value, true));
        this.addRow("Cooldown", this.addRenderableWidget(cooldown));
        this.current = object;
    }

    private void addLogicFields(int fieldW) {
        JsonObject object = TreeEditorJson.objectOrNew(this.current);
        this.current = object;
        this.addNestedConditionList(object, "conditions", fieldW, logicHeading(TreeEditorJson.readType(object)), true);
        this.addRow("Fallback", this.addRenderableWidget(Button.builder(Component.literal("Edit raw JSON"), button -> this.openJson())
                .bounds(0, 0, fieldW, FIELD_H)
                .build()));
    }

    private void addNestedConditionList(JsonObject object, String key, int fieldW, String heading, boolean keepArray) {
        List<JsonElement> list = TreeEditorJson.asConditionList(object.get(key));
        this.writeNestedList(object, key, list, keepArray);
        for (int index = 0; index < list.size(); index++) {
            int captured = index;
            JsonElement entry = list.get(index);
            String type = TreeEditorJson.readType(entry);
            String label = type.isEmpty() ? heading + " " + (index + 1) : type;
            Button edit = this.addRenderableWidget(Button.builder(Component.literal(TreeEditorJson.summary(entry)), button ->
                            this.minecraft.setScreen(new TreeEditorTypedObjectScreen(this, Kind.CONDITION, this.catalog, this.costs, entry, value -> {
                                List<JsonElement> next = TreeEditorJson.asConditionList(object.get(key));
                                if (value == null) {
                                    if (captured < next.size()) {
                                        next.remove(captured);
                                    }
                                } else if (captured < next.size()) {
                                    next.set(captured, value);
                                } else {
                                    next.add(value);
                                }
                                this.writeNestedList(object, key, next, keepArray);
                                this.current = object;
                                this.init(this.width, this.height);
                            })))
                    .bounds(0, 0, fieldW - 40, FIELD_H)
                    .build());
            TreeEditorFlatButton remove = this.addRenderableWidget(new TreeEditorFlatButton(0, 0, 32, FIELD_H, "-", () -> {
                List<JsonElement> next = TreeEditorJson.asConditionList(object.get(key));
                if (captured < next.size()) {
                    next.remove(captured);
                }
                this.writeNestedList(object, key, next, keepArray);
                this.current = object;
                this.init(this.width, this.height);
            }));
            this.addSplitRow(label, edit, fieldW - 40, remove, 32);
        }
        this.addRow(heading, this.addRenderableWidget(Button.builder(Component.literal("+ Add condition"), button ->
                        this.openConditionPicker(value -> {
                            List<JsonElement> next = TreeEditorJson.asConditionList(object.get(key));
                            next.add(value);
                            this.writeNestedList(object, key, next, keepArray);
                            this.current = object;
                            this.init(this.width, this.height);
                        }))
                .bounds(0, 0, fieldW, FIELD_H)
                .build()));
    }

    private void writeNestedList(JsonObject object, String key, List<JsonElement> list, boolean keepArray) {
        if (keepArray) {
            object.add(key, TreeEditorJson.toJsonArray(list));
            return;
        }
        JsonElement written = TreeEditorJson.fromConditionList(list);
        if (written == null) {
            object.remove(key);
        } else {
            object.add(key, written);
        }
    }

    private void openConditionPicker(Consumer<JsonElement> onPicked) {
        this.minecraft.setScreen(new TreeEditorTypePickerScreen(this, this.conditionSchemas(), id -> {
            JsonObject initial = TreeEditorJson.typedObject(id, exampleFor(id));
            this.minecraft.setScreen(new TreeEditorTypedObjectScreen(this, Kind.CONDITION, this.catalog, this.costs, initial, value -> {
                if (value != null) {
                    onPicked.accept(value);
                }
            }));
        }));
    }

    private List<DocSchema> conditionSchemas() {
        List<DocSchema> schemas = new ArrayList<>(this.catalog.conditions());
        schemas.add(0, new DocSchema(TreeEditorStateSync.TYPE_KEY_BIND, "Key Bind", "Enables the ability from a key press.", List.of(), null));
        schemas.add(0, new DocSchema(TreeEditorStateSync.TYPE_BUYABLE, "Buyable", "Unlock by paying a cost.", List.of(), null));
        return schemas;
    }

    @Nullable
    private JsonElement exampleFor(String type) {
        DocSchema schema = this.schemaFor(type);
        return schema == null ? null : schema.example();
    }

    private static String logicHeading(String type) {
        if (TreeEditorStateSync.TYPE_AND.equals(type)) {
            return "AND";
        }
        if (TreeEditorStateSync.TYPE_OR.equals(type)) {
            return "OR";
        }
        if (TreeEditorStateSync.TYPE_NOT.equals(type)) {
            return "NOT";
        }
        return "Conditions";
    }

    private void addUpgradeSwitchFields(int fieldW) {
        JsonObject object = TreeEditorJson.objectOrNew(this.current);
        object.addProperty("type", "yha:upgrade_switch");
        JsonArray cases = TreeEditorJson.arrayOrNew(object.get("cases"));
        object.add("cases", cases);
        this.current = object;
        for (int index = 0; index < cases.size(); index++) {
            JsonObject entry = cases.get(index).isJsonObject() ? cases.get(index).getAsJsonObject() : new JsonObject();
            int captured = index;
            EditBox ability = new EditBox(this.font, 0, 0, fieldW, FIELD_H, Component.literal("Ability"));
            ability.setMaxLength(64);
            ability.setValue(TreeEditorJson.asText(entry.get("ability")));
            ability.setResponder(value -> {
                entry.addProperty("ability", value);
                cases.set(captured, entry);
                object.add("cases", cases);
                this.current = object;
            });
            this.addRow("Case " + (index + 1) + " ability", this.addRenderableWidget(ability));
            this.addRow("Case " + (index + 1) + " value", this.addRenderableWidget(Button.builder(Component.literal(TreeEditorJson.summary(entry.get("value"))), button ->
                            this.minecraft.setScreen(new TreeEditorTypedObjectScreen(this, Kind.VALUE, this.catalog, this.costs, entry.get("value"), value -> {
                                if (value == null) {
                                    entry.remove("value");
                                } else {
                                    entry.add("value", value);
                                }
                                cases.set(captured, entry);
                                object.add("cases", cases);
                                this.current = object;
                            })))
                    .bounds(0, 0, fieldW, FIELD_H)
                    .build()));
        }
        this.addRow("", this.addRenderableWidget(Button.builder(Component.literal("Add case"), button -> {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("ability", "upgrade");
                    entry.addProperty("value", 1);
                    cases.add(entry);
                    object.add("cases", cases);
                    this.current = object;
                    this.init(this.width, this.height);
                })
                .bounds(0, 0, fieldW, FIELD_H)
                .build()));
        this.addRow("Fallback", this.addRenderableWidget(Button.builder(Component.literal(TreeEditorJson.summary(object.get("fallback"))), button ->
                        this.minecraft.setScreen(new TreeEditorTypedObjectScreen(this, Kind.VALUE, this.catalog, this.costs, object.get("fallback"), value -> {
                            if (value == null) {
                                object.remove("fallback");
                            } else {
                                object.add("fallback", value);
                            }
                            this.current = object;
                        })))
                .bounds(0, 0, fieldW, FIELD_H)
                .build()));
    }

    private void openTypePicker() {
        List<DocSchema> schemas = new ArrayList<>(this.schemas());
        if (this.kind == Kind.CONDITION) {
            schemas.add(0, new DocSchema(TreeEditorStateSync.TYPE_KEY_BIND, "Key Bind", "Enables the ability from a key press.", List.of(), null));
            schemas.add(0, new DocSchema(TreeEditorStateSync.TYPE_BUYABLE, "Buyable", "Unlock by paying a cost.", List.of(), null));
        }
        this.minecraft.setScreen(new TreeEditorTypePickerScreen(this, schemas, id -> {
            this.literalValue = false;
            this.current = typedObject(id);
            this.init(this.width, this.height);
        }));
    }

    private void openJson() {
        this.minecraft.setScreen(new TreeEditorJsonEditScreen(this, "Raw JSON", this.current, value -> this.current = value));
    }

    private List<DocSchema> schemas() {
        return switch (this.kind) {
            case CONDITION -> this.catalog.conditions();
            case ACTION -> this.catalog.actions();
            case VALUE -> this.catalog.values();
        };
    }

    @Nullable
    private DocSchema schemaFor(String type) {
        return switch (this.kind) {
            case CONDITION -> this.catalog.findCondition(type);
            case ACTION -> this.catalog.findAction(type);
            case VALUE -> this.catalog.findValue(type);
        };
    }

    private JsonObject typedObject(String type) {
        return TreeEditorJson.typedObject(type, this.exampleFor(type));
    }

    private void save() {
        this.onSave.accept(this.current);
        this.onClose();
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

    private static boolean isLogic(String type) {
        return TreeEditorStateSync.isLogic(type);
    }

    private static DocFieldKind effectiveKind(DocField field) {
        if (field.kind() != DocFieldKind.COMBINED) {
            return field.kind();
        }
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

    private static JsonElement parseLiteral(String value) {
        if (value == null || value.isBlank()) {
            return new JsonPrimitive("");
        }
        try {
            if (value.indexOf('.') >= 0) {
                return new JsonPrimitive(Double.parseDouble(value));
            }
            return new JsonPrimitive(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return new JsonPrimitive(value);
        }
    }

    private static String titleFor(Kind kind) {
        return switch (kind) {
            case CONDITION -> "Edit Condition";
            case ACTION -> "Edit Action";
            case VALUE -> "Edit Value";
        };
    }
}
