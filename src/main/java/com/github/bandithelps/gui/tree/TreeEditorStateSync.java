package com.github.bandithelps.gui.tree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TreeEditorStateSync {
    public static final String TYPE_ABILITY_UNLOCKED = "palladium:ability_unlocked";
    public static final String TYPE_BUYABLE = "palladium:buyable";
    public static final String TYPE_AND = "palladium:and";
    public static final String TYPE_OR = "palladium:or";
    public static final String TYPE_NOT = "palladium:not";
    public static final String TYPE_KEY_BIND = "palladium:key_bind";

    private TreeEditorStateSync() {
    }

    public static void applyParentAndCost(TreeEditorNode node, List<TreeEditorCostSchema> schemas) {
        applyCost(node, schemas);
    }

    public static void applyCost(TreeEditorNode node, List<TreeEditorCostSchema> schemas) {
        JsonObject costJson = node.getCost().toJson(schemas);
        List<JsonElement> list = TreeEditorJson.asConditionList(node.getUnlocking());
        int buyableIndex = findBuyableIndex(list);
        if (costJson != null) {
            JsonObject buyable = buyableIndex >= 0 && list.get(buyableIndex).isJsonObject()
                    ? list.get(buyableIndex).getAsJsonObject()
                    : new JsonObject();
            buyable.addProperty("type", TYPE_BUYABLE);
            buyable.add("cost", costJson);
            if (buyableIndex >= 0) {
                list.set(buyableIndex, buyable);
            } else {
                list.add(0, buyable);
            }
        } else if (buyableIndex >= 0) {
            list.remove(buyableIndex);
        }
        node.setUnlocking(TreeEditorJson.fromConditionList(list));
    }

    public static void addParent(TreeEditorNode node, String parentKey, List<TreeEditorCostSchema> schemas) {
        if (parentKey == null || parentKey.isBlank()) {
            return;
        }
        List<JsonElement> list = TreeEditorJson.asConditionList(node.getUnlocking());
        if (containsAbilityUnlocked(node.getUnlocking(), parentKey)) {
            node.setUnlocking(TreeEditorJson.fromConditionList(list));
            applyCost(node, schemas);
            return;
        }
        list.add(abilityUnlocked(parentKey));
        node.setUnlocking(TreeEditorJson.fromConditionList(list));
        applyCost(node, schemas);
    }

    public static void removeParent(TreeEditorNode node, String parentKey) {
        if (parentKey == null || parentKey.isBlank()) {
            return;
        }
        List<JsonElement> list = TreeEditorJson.asConditionList(node.getUnlocking());
        List<JsonElement> cleaned = new ArrayList<>();
        for (JsonElement element : list) {
            JsonElement next = stripAbilityUnlocked(element, parentKey);
            if (next != null) {
                cleaned.add(next);
            }
        }
        node.setUnlocking(TreeEditorJson.fromConditionList(cleaned));
    }

    public static void removeAllParents(TreeEditorNode node) {
        for (String parentKey : List.copyOf(node.getParentKeys())) {
            removeParent(node, parentKey);
        }
    }

    public static List<String> parentKeysFromUnlocking(@Nullable JsonElement unlocking) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        collectAbilityUnlocked(unlocking, keys);
        return new ArrayList<>(keys);
    }

    @Nullable
    public static String parentKeyFromUnlocking(@Nullable JsonElement unlocking) {
        List<String> keys = parentKeysFromUnlocking(unlocking);
        return keys.isEmpty() ? null : keys.getFirst();
    }

    public static void replaceAbilityRefs(@Nullable JsonElement element, String oldKey, String newKey) {
        if (element == null || oldKey == null || newKey == null || oldKey.equals(newKey)) {
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("ability") && object.get("ability").isJsonPrimitive()) {
                String raw = object.get("ability").getAsString();
                String local = localAbilityKey(raw);
                if (oldKey.equals(local)) {
                    int hash = raw.indexOf('#');
                    object.addProperty("ability", hash >= 0 ? raw.substring(0, hash + 1) + newKey : newKey);
                }
            }
            for (var entry : object.entrySet()) {
                replaceAbilityRefs(entry.getValue(), oldKey, newKey);
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                replaceAbilityRefs(child, oldKey, newKey);
            }
        }
    }

    public static JsonObject abilityUnlocked(String abilityKey) {
        JsonObject requires = new JsonObject();
        requires.addProperty("type", TYPE_ABILITY_UNLOCKED);
        requires.addProperty("ability", abilityKey);
        return requires;
    }

    public static String readType(@Nullable JsonObject object) {
        if (object == null || !object.has("type") || !object.get("type").isJsonPrimitive()) {
            return "";
        }
        return object.get("type").getAsString();
    }

    @Nullable
    public static String readAbilityKey(@Nullable JsonObject object) {
        if (object == null || !object.has("ability") || !object.get("ability").isJsonPrimitive()) {
            return null;
        }
        return localAbilityKey(object.get("ability").getAsString());
    }

    @Nullable
    public static String localAbilityKey(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int hash = raw.indexOf('#');
        return hash >= 0 ? raw.substring(hash + 1) : raw;
    }

    public static boolean isLogic(String type) {
        return TYPE_AND.equals(type) || TYPE_OR.equals(type) || TYPE_NOT.equals(type);
    }

    private static void collectAbilityUnlocked(@Nullable JsonElement element, Set<String> keys) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectAbilityUnlocked(child, keys);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (TYPE_ABILITY_UNLOCKED.equals(readType(object))) {
            String key = readAbilityKey(object);
            if (key != null) {
                keys.add(key);
            }
        }
        collectAbilityUnlocked(object.get("requires"), keys);
        collectAbilityUnlocked(object.get("conditions"), keys);
    }

    private static boolean containsAbilityUnlocked(@Nullable JsonElement element, String parentKey) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        collectAbilityUnlocked(element, keys);
        return keys.contains(parentKey);
    }

    @Nullable
    private static JsonElement stripAbilityUnlocked(@Nullable JsonElement element, String parentKey) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                JsonElement cleaned = stripAbilityUnlocked(child, parentKey);
                if (cleaned != null) {
                    result.add(cleaned);
                }
            }
            return result.isEmpty() ? null : result;
        }
        if (!element.isJsonObject()) {
            return element.deepCopy();
        }
        JsonObject object = element.getAsJsonObject().deepCopy();
        if (TYPE_ABILITY_UNLOCKED.equals(readType(object)) && parentKey.equals(readAbilityKey(object))) {
            return null;
        }
        stripField(object, "requires", parentKey);
        stripField(object, "conditions", parentKey);
        return object;
    }

    private static void stripField(JsonObject object, String key, String parentKey) {
        if (!object.has(key)) {
            return;
        }
        JsonElement cleaned = stripAbilityUnlocked(object.get(key), parentKey);
        if (cleaned == null) {
            object.remove(key);
        } else {
            object.add(key, cleaned);
        }
    }

    private static int findBuyableIndex(List<JsonElement> list) {
        for (int index = 0; index < list.size(); index++) {
            JsonObject object = TreeEditorJson.asObject(list.get(index));
            if (object != null && TYPE_BUYABLE.equals(readType(object))) {
                return index;
            }
        }
        return -1;
    }
}
