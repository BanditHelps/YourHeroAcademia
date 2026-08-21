package com.github.bandithelps.gui.tree;

import com.google.gson.JsonObject;
import com.github.bandithelps.conditions.cost.UpgradePointCost;
import net.threetag.palladium.logic.cost.Cost;
import net.threetag.palladium.power.ability.unlocking.BuyableUnlockingHandler;
import net.threetag.palladium.power.ability.unlocking.UnlockingHandler;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TreeEditorCostDraft {
    public static final String DEFAULT_TYPE = TreeEditorCostSchema.NONE_ID;

    private String typeId;
    private final Map<String, String> values = new LinkedHashMap<>();

    public TreeEditorCostDraft(String typeId) {
        this.typeId = typeId == null || typeId.isBlank() ? DEFAULT_TYPE : typeId;
    }

    public static TreeEditorCostDraft none() {
        return new TreeEditorCostDraft(DEFAULT_TYPE);
    }

    public TreeEditorCostDraft copy() {
        TreeEditorCostDraft copy = new TreeEditorCostDraft(this.typeId);
        copy.values.putAll(this.values);
        return copy;
    }

    public boolean sameAs(@Nullable TreeEditorCostDraft other) {
        if (other == null) {
            return false;
        }
        return Objects.equals(this.typeId, other.typeId) && this.values.equals(other.values);
    }

    public static TreeEditorCostDraft fromUnlocking(UnlockingHandler handler) {
        if (handler instanceof BuyableUnlockingHandler buyable && buyable.cost instanceof UpgradePointCost upgradeCost) {
            TreeEditorCostDraft draft = new TreeEditorCostDraft("yha:upgrade_point");
            draft.set("points", Integer.toString(upgradeCost.getPoints()));
            return draft;
        }
        return none();
    }

    public boolean isNone() {
        return TreeEditorCostSchema.NONE_ID.equals(this.typeId);
    }

    public String getTypeId() {
        return this.typeId;
    }

    public void setTypeId(String typeId, List<TreeEditorCostSchema> schemas) {
        this.typeId = typeId == null || typeId.isBlank() ? DEFAULT_TYPE : typeId;
        if (this.isNone()) {
            this.values.clear();
            return;
        }
        TreeEditorCostSchema schema = find(schemas, this.typeId);
        if (schema == null) {
            return;
        }
        Map<String, String> previous = new LinkedHashMap<>(this.values);
        this.values.clear();
        for (TreeEditorCostSchema.Field field : schema.fields()) {
            String fallback = field.numeric() ? "1" : (field.itemLike() ? "minecraft:diamond" : "");
            this.values.put(field.key(), previous.getOrDefault(field.key(), fallback));
        }
    }

    public String get(String key) {
        return this.values.getOrDefault(key, "");
    }

    public void set(String key, String value) {
        this.values.put(key, value == null ? "" : value);
    }

    public JsonObject toJson(List<TreeEditorCostSchema> schemas) {
        if (this.isNone()) {
            return null;
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", this.typeId);
        TreeEditorCostSchema schema = find(schemas, this.typeId);
        if (schema == null) {
            for (var entry : this.values.entrySet()) {
                if (entry.getValue().isBlank()) {
                    continue;
                }
                if (entry.getValue().chars().allMatch(Character::isDigit)) {
                    json.addProperty(entry.getKey(), parsePositiveInt(entry.getValue(), 1));
                } else {
                    json.addProperty(entry.getKey(), entry.getValue());
                }
            }
            return json;
        }
        for (TreeEditorCostSchema.Field field : schema.fields()) {
            String value = this.get(field.key());
            if (field.numeric()) {
                json.addProperty(field.key(), parsePositiveInt(value, 1));
            } else if (!value.isBlank()) {
                json.addProperty(field.key(), value);
            }
        }
        return json;
    }

    public String summary(List<TreeEditorCostSchema> schemas) {
        if (this.isNone()) {
            return "None";
        }
        TreeEditorCostSchema schema = find(schemas, this.typeId);
        String name = schema == null ? this.typeId : schema.name();
        String amount = firstNumeric(schema);
        return amount.isEmpty() ? name : name + " x" + amount;
    }

    private String firstNumeric(TreeEditorCostSchema schema) {
        if (schema == null) {
            return this.get("points");
        }
        for (TreeEditorCostSchema.Field field : schema.fields()) {
            if (field.numeric()) {
                return this.get(field.key());
            }
        }
        return "";
    }

    public static TreeEditorCostSchema find(List<TreeEditorCostSchema> schemas, String typeId) {
        for (TreeEditorCostSchema schema : schemas) {
            if (schema.id().equals(typeId)) {
                return schema;
            }
        }
        return null;
    }

    private static int parsePositiveInt(String value, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
