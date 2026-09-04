package com.github.bandithelps.gui.screens;

import com.github.bandithelps.gui.tree.TreeEditorCostDraft;
import com.github.bandithelps.gui.tree.TreeEditorCostSchema;
import com.github.bandithelps.gui.tree.TreeEditorDraft;
import com.github.bandithelps.gui.tree.TreeEditorJson;
import com.github.bandithelps.gui.tree.TreeEditorNode;
import com.github.bandithelps.gui.tree.TreeEditorStateSync;
import com.github.bandithelps.gui.tree.schema.DocField;
import com.github.bandithelps.gui.tree.schema.DocFieldKind;
import com.github.bandithelps.gui.tree.schema.DocSchema;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public final class TreeEditorInspector {
    private static final int PAD = 10;
    private static final int SCROLLBAR = 5;

    private final TreeEditorScreen screen;
    private final List<Row> rows = new ArrayList<>();
    private final Set<String> collapsed = new HashSet<>();
    private final List<SectionHit> sectionHits = new ArrayList<>();
    private String currentSection = "";
    private int scroll;
    private int maxScroll;
    private int contentHeight;

    public TreeEditorInspector(TreeEditorScreen screen) {
        this.screen = screen;
    }

    public void rebuild() {
        this.rows.clear();
        List<TreeEditorNode> selected = this.screen.selectedNodes();
        if (selected.isEmpty()) {
            this.buildPower();
        } else if (selected.size() == 1) {
            this.buildNode(selected.getFirst());
        } else {
            this.buildNodes(selected);
        }
        this.layout();
    }

    public void draw(GuiGraphicsExtractor graphics) {
        int x = this.screen.inspectorX();
        int y = this.screen.inspectorY();
        int w = this.screen.inspectorW();
        int h = this.screen.inspectorH();
        TreeEditorTheme.panel(graphics, x, y, w, h);
        TreeEditorTheme.fill(graphics, x + 1, y + 1, w - 2, 22, TreeEditorTheme.HEADER);
        TreeEditorTheme.fill(graphics, x + 1, y + 22, w - 2, 1, TreeEditorTheme.ACCENT_DIM);
        String title = this.screen.selectedNode() == null
                ? "Power"
                : this.screen.selectionSize() > 1 ? "Inspector (" + this.screen.selectionSize() + ")" : "Inspector";
        graphics.text(this.screen.getFont(), title, x + PAD, y + 8, TreeEditorTheme.TEXT, false);

        int clipLeft = this.clipLeft();
        int clipTop = this.clipTop();
        int clipRight = this.clipRight();
        int clipBottom = this.clipBottom();
        int textW = this.fieldWidth();
        this.measure(textW);
        this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll));

        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        int relY = clipTop + 8 - this.scroll;
        for (Row row : this.rows) {
            if (row.kind == RowKind.SECTION) {
                TreeEditorTheme.section(
                        graphics,
                        this.screen.getFont(),
                        x + 6,
                        relY,
                        w - 12 - (this.maxScroll > 0 ? SCROLLBAR : 0),
                        row.label,
                        this.collapsed.contains(row.section)
                );
                relY += TreeEditorTheme.SECTION_H + 6;
                continue;
            }
            if (this.collapsed.contains(row.section)) {
                continue;
            }
            if (row.kind == RowKind.TEXT) {
                relY = TreeEditorTheme.walkWrapped(graphics, this.screen.getFont(), row.label, x + PAD, relY, textW, TreeEditorTheme.TEXT_MUTED) + 6;
                continue;
            }
            if (row.label != null && !row.label.isBlank()) {
                graphics.text(this.screen.getFont(), row.label, x + PAD, relY, TreeEditorTheme.TEXT_MUTED, false);
                relY += 11;
            }
            relY += row.height + 8;
        }
        graphics.disableScissor();
        if (this.maxScroll > 0) {
            TreeEditorTheme.scrollbar(graphics, x + w - 7, clipTop + 4, clipBottom - clipTop - 8, this.contentHeight, this.scroll, this.maxScroll);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!this.screen.isInInspector((int) mouseX, (int) mouseY) || this.maxScroll <= 0) {
            return false;
        }
        this.scroll = Math.max(0, Math.min(this.maxScroll, this.scroll - (int) Math.signum(scrollY) * 28));
        this.layout();
        return true;
    }

    public void resetScroll() {
        this.scroll = 0;
    }

    public boolean mouseClicked(int mouseX, int mouseY) {
        for (SectionHit hit : this.sectionHits) {
            if (mouseX >= hit.x && mouseX < hit.x + hit.width && mouseY >= hit.y && mouseY < hit.y + hit.height) {
                if (this.collapsed.contains(hit.id)) {
                    this.collapsed.remove(hit.id);
                } else {
                    this.collapsed.add(hit.id);
                }
                this.screen.refreshWidgets();
                return true;
            }
        }
        return false;
    }

    private void buildPower() {
        TreeEditorDraft draft = this.screen.draft();
        this.section("FILE");
        this.textField("Name", draft.getPowerName(), 64, value -> draft.setPowerName(value));
        this.textField("Parent power", draft.getParentPower() == null ? "" : draft.getParentPower(), 64, value -> draft.setParentPower(value));
        this.iconField("Icon", draft.getPowerIcon(), () -> this.screen.openPowerIconPicker());
        this.note(draft.getNodes().size() + " abilities  ·  " + draft.getPowerId());
        this.note("Select a node to edit it. Right-click the tree to add abilities.");
    }

    private void buildNode(TreeEditorNode node) {
        TreeEditorDraft draft = this.screen.draft();
        this.section("PROPERTIES");
        this.textField("Title", node.getTitle(), 64, value -> {
            node.setTitle(value);
            draft.markDirty();
        });
        this.textField("Id", node.getKey(), 48, value -> {
            if (draft.rename(node, value)) {
                this.screen.setStatus("Renamed to " + node.getKey());
            }
        });
        this.textField("Description", node.getDescription(), 1024, value -> {
            node.setDescription(value);
            draft.markDirty();
        });
        this.textField("Locked text", node.getLockedDescription(), 1024, value -> {
            node.setLockedDescription(value);
            draft.markDirty();
        });
        this.iconField("Icon", node.getIconId(), () -> this.screen.openNodeIconPicker(node));
        this.toggle("Hidden in tree", node.isHiddenInGui(), () -> this.screen.toggleHidden(node));
        this.toggle("Hidden in bar", node.isHiddenInBar(), () -> {
            node.setHiddenInBar(!node.isHiddenInBar());
            draft.markDirty();
            this.screen.refreshWidgets();
        });
        this.numberField("List index", Integer.toString(node.getListIndex()), value -> {
            node.setListIndex(parseInt(value, 0));
            draft.markDirty();
        });
        if (node.hasCustomActivationStamina()) {
            this.note("Activation stamina  (dynamic Value)");
        } else {
            this.numberField("Activation stamina", Integer.toString(node.getActivationStamina()), value -> {
                node.setActivationStamina(parseInt(value, 0));
                draft.markDirty();
            });
        }
        if (node.hasCustomStaminaInterval()) {
            this.note("Stamina interval  (dynamic Value)");
        } else {
            this.numberField("Stamina interval", Integer.toString(node.getStaminaInterval()), value -> {
                node.setStaminaInterval(parseInt(value, 0));
                draft.markDirty();
            });
        }
        if (node.hasCustomStaminaIntervalCost()) {
            this.note("Interval cost  (dynamic Value)");
        } else {
            this.numberField("Interval cost", Integer.toString(node.getStaminaIntervalCost()), value -> {
                node.setStaminaIntervalCost(parseInt(value, 0));
                draft.markDirty();
            });
        }
        this.note("Position  " + node.getGridX() + ", " + node.getGridY());

        this.section("ABILITY");
        DocSchema schema = this.screen.catalog().findAbility(node.getTypeId());
        String typeLabel = schema == null ? node.getTypeId() : schema.name();
        this.button("Type", typeLabel, () -> this.screen.openAbilityTypePicker(node));
        if (schema != null && !schema.description().isBlank()) {
            this.note(schema.description());
        }
        if (schema == null || schema.fields().isEmpty()) {
            this.button("Fields", "Edit raw JSON", () -> this.screen.openTypeFieldsJson(node));
        } else {
            for (DocField field : schema.fields()) {
                this.addAbilityField(List.of(node), field);
            }
        }

        this.section("STATE");
        this.conditionList("Unlocking", TreeEditorJson.asConditionList(node.getUnlocking()),
                index -> this.screen.openUnlockingCondition(node, index),
                index -> this.screen.removeUnlockingCondition(node, index),
                () -> this.screen.addUnlockingCondition(node),
                () -> this.screen.openUnlockingJson(node));
        this.conditionList("Enabling", TreeEditorJson.asEnablingList(node.getEnabling()),
                index -> this.screen.openEnablingCondition(node, index),
                index -> this.screen.removeEnablingCondition(node, index),
                () -> this.screen.addEnablingCondition(node),
                () -> this.screen.openEnablingJson(node));
        this.button("Cost", node.getCost().summary(this.screen.costSchemas()), () -> this.screen.cycleCost(node));
        TreeEditorCostSchema costSchema = TreeEditorCostDraft.find(this.screen.costSchemas(), node.getCost().getTypeId());
        if (costSchema != null) {
            for (TreeEditorCostSchema.Field field : costSchema.fields()) {
                if (field.itemLike()) {
                    String key = field.key();
                    this.iconField(field.key(), node.getCost().get(key), () -> this.screen.openCostItemPicker(node, key));
                } else {
                    this.textField(field.key(), node.getCost().get(field.key()), 64, value -> {
                        node.getCost().set(field.key(), value);
                        TreeEditorStateSync.applyParentAndCost(node, this.screen.costSchemas());
                        draft.markDirty();
                    });
                }
            }
        }
    }

    private void buildNodes(List<TreeEditorNode> nodes) {
        TreeEditorNode primary = nodes.getFirst();
        this.section("PROPERTIES");
        this.sharedTextField("Title", nodes, TreeEditorNode::getTitle, 64, (node, value) -> node.setTitle(value));
        this.sharedTextField("Description", nodes, TreeEditorNode::getDescription, 1024, (node, value) -> node.setDescription(value));
        this.sharedTextField("Locked text", nodes, TreeEditorNode::getLockedDescription, 1024, (node, value) -> node.setLockedDescription(value));
        SharedValue<String> icon = SharedValue.of(nodes, TreeEditorNode::getIconId);
        this.iconField("Icon", icon.mixed ? "" : icon.value, () -> this.screen.openNodeIconPicker(primary));
        this.sharedToggle("Hidden in tree", nodes, TreeEditorNode::isHiddenInGui, value -> this.screen.setNodesHiddenInTree(nodes, value));
        this.sharedToggle("Hidden in bar", nodes, TreeEditorNode::isHiddenInBar, value -> this.screen.setNodesHiddenInBar(nodes, value));
        this.sharedTextField("List index", nodes, node -> Integer.toString(node.getListIndex()), 8, (node, value) -> node.setListIndex(parseInt(value, 0)));
        this.sharedTextField("Activation stamina", nodes, node -> node.hasCustomActivationStamina() ? "Value" : Integer.toString(node.getActivationStamina()), 8, (node, value) -> {
            if (!node.hasCustomActivationStamina()) {
                node.setActivationStamina(parseInt(value, 0));
            }
        });
        this.sharedTextField("Stamina interval", nodes, node -> node.hasCustomStaminaInterval() ? "Value" : Integer.toString(node.getStaminaInterval()), 8, (node, value) -> {
            if (!node.hasCustomStaminaInterval()) {
                node.setStaminaInterval(parseInt(value, 0));
            }
        });
        this.sharedTextField("Interval cost", nodes, node -> node.hasCustomStaminaIntervalCost() ? "Value" : Integer.toString(node.getStaminaIntervalCost()), 8, (node, value) -> {
            if (!node.hasCustomStaminaIntervalCost()) {
                node.setStaminaIntervalCost(parseInt(value, 0));
            }
        });
        this.note(nodes.size() + " nodes selected");

        this.section("ABILITY");
        String typeId = primary.getTypeId();
        boolean sameType = true;
        for (TreeEditorNode node : nodes) {
            if (!typeId.equals(node.getTypeId())) {
                sameType = false;
                break;
            }
        }
        if (!sameType) {
            this.note("Multiple ability types");
            return;
        }
        DocSchema schema = this.screen.catalog().findAbility(typeId);
        String typeLabel = schema == null ? typeId : schema.name();
        this.button("Type", typeLabel, () -> this.screen.openAbilityTypePicker(primary));
        if (schema != null && !schema.description().isBlank()) {
            this.note(schema.description());
        }
        if (schema == null || schema.fields().isEmpty()) {
            this.button("Fields", "Edit raw JSON", () -> this.screen.openTypeFieldsJson(primary));
        } else {
            for (DocField field : schema.fields()) {
                this.addAbilityField(nodes, field);
            }
        }
    }

    @FunctionalInterface
    private interface NodeStringSetter {
        void accept(TreeEditorNode node, String value);
    }

    private void addAbilityField(List<TreeEditorNode> nodes, DocField field) {
        DocFieldKind kind = field.kind() == DocFieldKind.COMBINED ? combinedKind(field) : field.kind();
        TreeEditorNode primary = nodes.getFirst();
        if (kind == DocFieldKind.BOOLEAN) {
            this.sharedToggle(field.label(), nodes, node -> sharedForNodeBoolean(node, field.key()), value -> {
                for (TreeEditorNode node : nodes) {
                    node.getTypeFields().addProperty(field.key(), value);
                }
                this.screen.draft().markDirty();
                this.screen.refreshWidgets();
            });
            return;
        }
        if (kind == DocFieldKind.ENUM && !field.enumValues().isEmpty()) {
            SharedValue<String> shared = SharedValue.of(nodes, node -> TreeEditorJson.asText(node.getTypeFields().get(field.key())));
            if (!shared.mixed && (shared.value == null || shared.value.isBlank()) && nodes.size() == 1) {
                String first = field.enumValues().getFirst();
                primary.getTypeFields().addProperty(field.key(), first);
                shared = new SharedValue<>(false, first);
            }
            String shown = shared.mixed ? "Mixed" : (shared.value == null || shared.value.isBlank() ? field.enumValues().getFirst() : shared.value);
            boolean mixed = shared.mixed;
            this.button(field.label(), shown, () -> {
                int index = field.enumValues().indexOf(shown);
                String next = mixed
                        ? field.enumValues().getFirst()
                        : field.enumValues().get((Math.max(index, 0) + 1) % field.enumValues().size());
                for (TreeEditorNode node : nodes) {
                    node.getTypeFields().addProperty(field.key(), next);
                }
                this.screen.draft().markDirty();
                this.screen.refreshWidgets();
            });
            return;
        }
        if (kind == DocFieldKind.VALUE || kind == DocFieldKind.CONDITION || kind == DocFieldKind.ACTION || kind == DocFieldKind.RAW_JSON
                || kind == DocFieldKind.VEC2 || kind == DocFieldKind.VEC3 || kind == DocFieldKind.STRING_LIST) {
            SharedValue<JsonElement> shared = SharedValue.of(nodes, node -> node.getTypeFields().get(field.key()));
            this.button(field.label(), shared.mixed ? "Mixed" : TreeEditorJson.summary(shared.value), () ->
                    this.screen.openTypeFieldEditor(primary, field, kind));
            return;
        }
        if (kind == DocFieldKind.ITEM || kind == DocFieldKind.ICON) {
            SharedValue<String> shared = SharedValue.of(nodes, node -> TreeEditorJson.asText(node.getTypeFields().get(field.key())));
            this.iconField(field.label(), shared.mixed ? "" : shared.value, () ->
                    this.screen.openTypeFieldItemPicker(primary, field.key()));
            return;
        }
        SharedValue<String> shared = SharedValue.of(nodes, node -> TreeEditorJson.asText(node.getTypeFields().get(field.key())));
        this.textField(field.label(), shared.mixed ? "" : shared.value, 512, value -> {
            for (TreeEditorNode node : nodes) {
                JsonObject typeFields = node.getTypeFields();
                if (kind == DocFieldKind.INTEGER) {
                    TreeEditorJson.putNumber(typeFields, field.key(), value, true);
                } else if (kind == DocFieldKind.FLOAT) {
                    TreeEditorJson.putNumber(typeFields, field.key(), value, false);
                } else if (value.isBlank()) {
                    typeFields.remove(field.key());
                } else {
                    typeFields.addProperty(field.key(), value);
                }
            }
            this.screen.draft().markDirty();
        }, shared.mixed);
    }

    private static boolean sharedForNodeBoolean(TreeEditorNode node, String key) {
        JsonObject typeFields = node.getTypeFields();
        return typeFields.has(key)
                && typeFields.get(key).isJsonPrimitive()
                && typeFields.get(key).getAsBoolean();
    }

    private void section(String title) {
        this.currentSection = title;
        this.rows.add(new Row(RowKind.SECTION, title, title, null, TreeEditorTheme.SECTION_H));
    }

    private void note(String text) {
        this.rows.add(new Row(RowKind.TEXT, text, this.currentSection, null, 0));
    }

    private void textField(String label, String value, int maxLength, java.util.function.Consumer<String> setter) {
        this.textField(label, value, maxLength, setter, false);
    }

    private void textField(String label, String value, int maxLength, java.util.function.Consumer<String> setter, boolean mixed) {
        TreeEditorFieldBox box = new TreeEditorFieldBox(this.screen.getFont(), this.fieldWidth(), TreeEditorTheme.FIELD_H, label);
        box.setMaxLength(maxLength);
        box.setValue(mixed || value == null ? "" : value);
        if (mixed) {
            box.setHint(Component.literal("Mixed"));
        }
        box.setResponder(setter);
        box.showStart();
        box.setExpand(() -> this.screen.openTextEditor(label, box.getValue(), maxLength, next -> {
            box.setValue(next);
            setter.accept(next);
            box.showStart();
        }));
        this.rows.add(new Row(RowKind.WIDGET, label, this.currentSection, this.screen.addEditorWidget(box), null, TreeEditorTheme.FIELD_H));
    }

    private void sharedTextField(String label, List<TreeEditorNode> nodes, Function<TreeEditorNode, String> getter, int maxLength, NodeStringSetter setter) {
        SharedValue<String> shared = SharedValue.of(nodes, getter);
        this.textField(label, shared.mixed ? "" : shared.value, maxLength, value -> {
            for (TreeEditorNode node : nodes) {
                setter.accept(node, value);
            }
            this.screen.draft().markDirty();
        }, shared.mixed);
    }

    private void sharedToggle(String label, List<TreeEditorNode> nodes, Function<TreeEditorNode, Boolean> getter, java.util.function.Consumer<Boolean> setter) {
        SharedValue<Boolean> shared = SharedValue.of(nodes, getter);
        String text = shared.mixed ? "Mixed" : (Boolean.TRUE.equals(shared.value) ? "On" : "Off");
        TreeEditorFlatButton button = this.screen.addEditorWidget(
                new TreeEditorFlatButton(0, 0, 56, TreeEditorTheme.FIELD_H, text, TreeEditorFlatButton.Style.TOGGLE, () -> {
                    boolean next = shared.mixed || !Boolean.TRUE.equals(shared.value);
                    setter.accept(next);
                })
        );
        this.rows.add(new Row(RowKind.TOGGLE, label, this.currentSection, button, null, TreeEditorTheme.FIELD_H));
    }

    private void iconField(String label, String iconId, Runnable onPress) {
        TreeEditorIconButton button = this.screen.addEditorWidget(
                new TreeEditorIconButton(this.fieldWidth(), 22, iconId, onPress)
        );
        this.rows.add(new Row(RowKind.WIDGET, label, this.currentSection, button, null, 22));
    }

    private void numberField(String label, String value, java.util.function.Consumer<String> setter) {
        this.textField(label, value, 8, setter);
    }

    private void button(String label, String text, Runnable onPress) {
        TreeEditorFlatButton button = this.screen.addEditorWidget(
                new TreeEditorFlatButton(0, 0, this.fieldWidth(), TreeEditorTheme.FIELD_H, text == null || text.isBlank() ? "None" : text, onPress)
        );
        this.rows.add(new Row(RowKind.WIDGET, label, this.currentSection, button, null, TreeEditorTheme.FIELD_H));
    }

    private void conditionList(
            String label,
            List<JsonElement> list,
            java.util.function.IntConsumer onEdit,
            java.util.function.IntConsumer onRemove,
            Runnable onAdd,
            Runnable onRaw
    ) {
        if (list.isEmpty()) {
            this.button(label, "None", onAdd);
        } else {
            for (int index = 0; index < list.size(); index++) {
                int captured = index;
                String rowLabel = index == 0 ? label : "";
                TreeEditorFlatButton edit = this.screen.addEditorWidget(
                        new TreeEditorFlatButton(0, 0, this.fieldWidth() - 28, TreeEditorTheme.FIELD_H, TreeEditorJson.summary(list.get(index)), () -> onEdit.accept(captured))
                );
                TreeEditorFlatButton remove = this.screen.addEditorWidget(
                        new TreeEditorFlatButton(0, 0, 24, TreeEditorTheme.FIELD_H, "-", TreeEditorFlatButton.Style.DANGER, () -> onRemove.accept(captured))
                );
                this.rows.add(new Row(RowKind.LIST, rowLabel, this.currentSection, edit, remove, TreeEditorTheme.FIELD_H));
            }
        }
        this.button("", "+ Add", onAdd);
        this.button("", "Raw JSON", onRaw);
    }

    private void toggle(String label, boolean value, Runnable onPress) {
        TreeEditorFlatButton button = this.screen.addEditorWidget(
                new TreeEditorFlatButton(0, 0, 56, TreeEditorTheme.FIELD_H, value ? "On" : "Off", TreeEditorFlatButton.Style.TOGGLE, onPress)
        );
        this.rows.add(new Row(RowKind.TOGGLE, label, this.currentSection, button, null, TreeEditorTheme.FIELD_H));
    }

    private void measure(int textW) {
        int height = 8;
        for (Row row : this.rows) {
            if (row.kind == RowKind.SECTION) {
                height += TreeEditorTheme.SECTION_H + 6;
                continue;
            }
            if (this.collapsed.contains(row.section)) {
                continue;
            }
            if (row.kind == RowKind.TEXT) {
                height += TreeEditorTheme.measureWrapped(this.screen.getFont(), row.label, textW) + 6;
            } else {
                if (row.label != null && !row.label.isBlank()) {
                    height += 11;
                }
                height += row.height + 8;
            }
        }
        this.contentHeight = height + 8;
        int viewport = Math.max(0, this.clipBottom() - this.clipTop());
        this.maxScroll = Math.max(0, 8 + this.contentHeight - viewport);
    }

    private void layout() {
        int textW = this.fieldWidth();
        this.measure(textW);
        this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll));
        this.sectionHits.clear();
        int x = this.screen.inspectorX() + PAD;
        int sectionX = this.screen.inspectorX() + 6;
        int sectionW = this.screen.inspectorW() - 12 - (this.maxScroll > 0 ? SCROLLBAR : 0);
        int top = this.clipTop();
        int bottom = this.clipBottom();
        int relY = top + 8 - this.scroll;
        for (Row row : this.rows) {
            if (row.kind == RowKind.SECTION) {
                this.sectionHits.add(new SectionHit(row.section, sectionX, relY, sectionW, TreeEditorTheme.SECTION_H));
                relY += TreeEditorTheme.SECTION_H + 6;
                continue;
            }
            if (this.collapsed.contains(row.section)) {
                hideWidget(row.widget);
                hideWidget(row.trailing);
                continue;
            }
            if (row.kind == RowKind.TEXT) {
                relY += TreeEditorTheme.measureWrapped(this.screen.getFont(), row.label, textW) + 6;
                continue;
            }
            if (row.label != null && !row.label.isBlank()) {
                relY += 11;
            }
            if (row.widget != null) {
                int widgetX = row.kind == RowKind.TOGGLE ? x + textW - 56 : x;
                int widgetW = row.kind == RowKind.TOGGLE ? 56 : (row.kind == RowKind.LIST ? textW - 28 : textW);
                row.widget.setRectangle(widgetW, row.height, widgetX, relY);
                boolean inView = relY >= top && relY + row.height <= bottom;
                applyVisibility(row.widget, inView);
                if (row.trailing != null) {
                    row.trailing.setRectangle(24, row.height, x + textW - 24, relY);
                    applyVisibility(row.trailing, inView);
                }
            }
            relY += row.height + 8;
        }
    }

    public int clipLeft() {
        return this.screen.inspectorX() + 1;
    }

    public int clipTop() {
        return this.screen.inspectorY() + 23;
    }

    public int clipRight() {
        return this.screen.inspectorX() + this.screen.inspectorW() - 1;
    }

    public int clipBottom() {
        return this.screen.inspectorY() + this.screen.inspectorH() - 1;
    }

    public boolean isInspectorWidget(AbstractWidget widget) {
        return widget.getX() >= this.screen.inspectorX()
                && widget.getY() >= this.screen.inspectorY()
                && widget.getY() < this.screen.inspectorY() + this.screen.inspectorH();
    }

    private int fieldWidth() {
        int w = this.screen.inspectorW() - PAD * 2;
        if (this.maxScroll > 0) {
            w -= SCROLLBAR + 2;
        }
        return Math.max(80, w);
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

    private static void hideWidget(@Nullable AbstractWidget widget) {
        applyVisibility(widget, false);
    }

    private static void applyVisibility(@Nullable AbstractWidget widget, boolean inView) {
        if (widget == null) {
            return;
        }
        widget.visible = inView;
        widget.active = inView;
        if (!inView && widget.isFocused()) {
            widget.setFocused(false);
        }
    }

    private enum RowKind {
        SECTION,
        TEXT,
        WIDGET,
        TOGGLE,
        LIST
    }

    private record Row(RowKind kind, String label, String section, @Nullable AbstractWidget widget, @Nullable AbstractWidget trailing, int height) {
        private Row(RowKind kind, String label, String section, @Nullable AbstractWidget widget, int height) {
            this(kind, label, section, widget, null, height);
        }
    }

    private record SharedValue<T>(boolean mixed, @Nullable T value) {
        static <T> SharedValue<T> of(List<TreeEditorNode> nodes, Function<TreeEditorNode, T> getter) {
            T first = getter.apply(nodes.getFirst());
            for (int index = 1; index < nodes.size(); index++) {
                if (!Objects.equals(first, getter.apply(nodes.get(index)))) {
                    return new SharedValue<>(true, null);
                }
            }
            return new SharedValue<>(false, first);
        }
    }

    private record SectionHit(String id, int x, int y, int width, int height) {
    }
}
