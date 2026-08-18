package com.github.bandithelps.gui.tree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TreeEditorExporter {
    private static final String TYPE_ABILITY_UNLOCKED = "palladium:ability_unlocked";
    private static final String TYPE_BUYABLE = "palladium:buyable";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private TreeEditorExporter() {
    }

    public static String toJson(TreeEditorDraft draft, List<TreeEditorCostSchema> schemas) {
        JsonObject source = draft.getSourceJson();
        if (source == null) {
            throw new IllegalStateException("Original power JSON was not loaded; cannot export a full file.");
        }
        JsonObject root = source.deepCopy();
        JsonObject abilities = root.has("abilities") && root.get("abilities").isJsonObject()
                ? root.getAsJsonObject("abilities")
                : new JsonObject();
        if (!root.has("abilities") || !root.get("abilities").isJsonObject()) {
            root.add("abilities", abilities);
        }

        for (TreeEditorNode node : draft.getNodes()) {
            if (node.isCreated()) {
                abilities.add(node.getKey(), dummyJson(node, schemas));
                continue;
            }
            JsonObject ability = abilities.has(node.getOriginalKey()) && abilities.get(node.getOriginalKey()).isJsonObject()
                    ? abilities.getAsJsonObject(node.getOriginalKey())
                    : null;
            if (ability == null) {
                continue;
            }
            if (node.positionChanged() || node.metadataChanged() || node.connectionChanged()) {
                patchProperties(ability, node);
            }
            if (node.parentChanged() || node.costChanged()) {
                patchUnlocking(ability, node, schemas);
            }
        }
        return GSON.toJson(root);
    }

    public static Path writeToGameDir(Minecraft minecraft, TreeEditorDraft draft, String json) throws IOException {
        Path directory = minecraft.gameDirectory.toPath().resolve("yha_exports");
        Files.createDirectories(directory);
        Path file = directory.resolve(draft.getPowerId().getPath() + ".json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }

    private static void patchProperties(JsonObject ability, TreeEditorNode node) {
        JsonObject properties = ability.has("properties") && ability.get("properties").isJsonObject()
                ? ability.getAsJsonObject("properties")
                : new JsonObject();
        if (!ability.has("properties") || !ability.get("properties").isJsonObject()) {
            ability.add("properties", properties);
        }
        if (node.positionChanged()) {
            properties.add("gui_position", positionArray(node.getGridX(), node.getGridY()));
        }
        if (node.connectionChanged()) {
            if (node.getConnectionPath().isEmpty()) {
                properties.remove(TreeConnectionPath.JSON_KEY);
            } else {
                properties.add(TreeConnectionPath.JSON_KEY, node.getConnectionPath().toJson());
            }
        }
        if (node.metadataChanged()) {
            properties.addProperty("title", node.getTitle());
            if (!node.getDescription().isBlank() || node.hasSplitDescription()) {
                properties.add("description", descriptionJson(node));
            } else {
                properties.remove("description");
            }
            properties.addProperty("icon", node.getIconId());
        }
    }

    private static void patchUnlocking(JsonObject ability, TreeEditorNode node, List<TreeEditorCostSchema> schemas) {
        JsonObject state = ability.has("state") && ability.get("state").isJsonObject()
                ? ability.getAsJsonObject("state")
                : null;
        JsonObject unlocking = state != null && state.has("unlocking") && state.get("unlocking").isJsonObject()
                ? state.getAsJsonObject("unlocking")
                : null;
        boolean hasParent = node.getParentKey() != null && !node.getParentKey().isBlank();
        JsonObject costJson = node.getCost().toJson(schemas);
        String type = readType(unlocking);

        if (unlocking == null) {
            unlocking = createUnlocking(hasParent ? node.getParentKey() : null, costJson);
            if (unlocking == null) {
                return;
            }
            if (state == null) {
                state = new JsonObject();
                ability.add("state", state);
            }
            state.add("unlocking", unlocking);
            return;
        }

        if (TYPE_ABILITY_UNLOCKED.equals(type)) {
            if (node.costChanged() && costJson != null) {
                String parent = node.parentChanged()
                        ? (hasParent ? node.getParentKey() : null)
                        : readAbilityKey(unlocking);
                state.add("unlocking", createUnlocking(parent, costJson));
                return;
            }
            if (node.parentChanged()) {
                if (hasParent) {
                    unlocking.addProperty("ability", node.getParentKey());
                } else {
                    unlocking.remove("ability");
                    pruneUnlocking(ability, state, unlocking);
                }
            }
            return;
        }

        if (TYPE_BUYABLE.equals(type)) {
            if (node.costChanged()) {
                if (costJson == null) {
                    unlocking.remove("cost");
                } else {
                    unlocking.add("cost", costJson);
                }
            }
            String parent = node.parentChanged()
                    ? (hasParent ? node.getParentKey() : null)
                    : readBuyableParent(unlocking);
            if (!unlocking.has("cost")) {
                if (parent != null && !parent.isBlank()) {
                    state.add("unlocking", abilityUnlocked(parent));
                } else {
                    pruneUnlocking(ability, state, unlocking);
                }
                return;
            }
            if (node.parentChanged() || node.costChanged()) {
                setBuyableRequires(unlocking, parent);
            }
            pruneUnlocking(ability, state, unlocking);
            return;
        }

        if (node.parentChanged()) {
            if (hasParent) {
                attachParentToUnlocking(state, unlocking, node.getParentKey());
            } else {
                detachParentFromUnlocking(ability, state, unlocking);
            }
        }
    }

    @Nullable
    private static JsonObject createUnlocking(@Nullable String parentKey, @Nullable JsonObject costJson) {
        boolean hasParent = parentKey != null && !parentKey.isBlank();
        if (!hasParent && costJson == null) {
            return null;
        }
        if (costJson != null) {
            JsonObject buyable = new JsonObject();
            buyable.addProperty("type", TYPE_BUYABLE);
            buyable.add("cost", costJson);
            if (hasParent) {
                buyable.add("requires", requiresArray(parentKey));
            }
            return buyable;
        }
        return abilityUnlocked(parentKey);
    }

    private static void setBuyableRequires(JsonObject unlocking, @Nullable String parentKey) {
        boolean hasParent = parentKey != null && !parentKey.isBlank();
        JsonArray requires = existingRequiresArray(unlocking);
        int index = findAbilityUnlockedIndex(requires);
        if (!hasParent) {
            if (index >= 0) {
                requires.remove(index);
            }
            if (requires.isEmpty()) {
                unlocking.remove("requires");
            } else {
                unlocking.add("requires", requires);
            }
            unlocking.remove("conditions");
            return;
        }
        if (index >= 0 && requires.get(index).isJsonObject()) {
            requires.get(index).getAsJsonObject().addProperty("ability", parentKey);
        } else {
            requires.add(abilityUnlocked(parentKey));
        }
        unlocking.add("requires", requires);
        unlocking.remove("conditions");
    }

    private static JsonArray existingRequiresArray(JsonObject unlocking) {
        if (unlocking.has("requires")) {
            JsonElement element = unlocking.get("requires");
            if (element.isJsonArray()) {
                return element.getAsJsonArray();
            }
            JsonArray requires = new JsonArray();
            if (element.isJsonObject()) {
                requires.add(element.getAsJsonObject());
            }
            return requires;
        }
        if (unlocking.has("conditions") && unlocking.get("conditions").isJsonArray()) {
            return unlocking.getAsJsonArray("conditions").deepCopy();
        }
        return new JsonArray();
    }

    private static JsonArray requiresArray(String parentKey) {
        JsonArray requires = new JsonArray();
        requires.add(abilityUnlocked(parentKey));
        return requires;
    }

    @Nullable
    private static String readBuyableParent(JsonObject unlocking) {
        JsonArray requires = existingRequiresArray(unlocking);
        int index = findAbilityUnlockedIndex(requires);
        if (index < 0 || !requires.get(index).isJsonObject()) {
            return null;
        }
        return readAbilityKey(requires.get(index).getAsJsonObject());
    }

    private static void attachParentToUnlocking(JsonObject state, JsonObject unlocking, String parentKey) {
        String type = readType(unlocking);
        if ("palladium:and".equals(type)) {
            JsonArray conditions = conditionsArray(unlocking);
            int index = findAbilityUnlockedIndex(conditions);
            if (index >= 0 && conditions.get(index).isJsonObject()) {
                conditions.get(index).getAsJsonObject().addProperty("ability", parentKey);
            } else {
                conditions.add(abilityUnlocked(parentKey));
            }
            unlocking.add("conditions", conditions);
            return;
        }
        JsonObject and = new JsonObject();
        and.addProperty("type", "palladium:and");
        JsonArray conditions = new JsonArray();
        conditions.add(unlocking.deepCopy());
        conditions.add(abilityUnlocked(parentKey));
        and.add("conditions", conditions);
        state.add("unlocking", and);
    }

    private static void detachParentFromUnlocking(JsonObject ability, JsonObject state, JsonObject unlocking) {
        if (!"palladium:and".equals(readType(unlocking))) {
            return;
        }
        JsonArray conditions = conditionsArray(unlocking);
        int index = findAbilityUnlockedIndex(conditions);
        if (index >= 0) {
            conditions.remove(index);
        }
        if (conditions.size() == 1 && conditions.get(0).isJsonObject()) {
            state.add("unlocking", conditions.get(0).getAsJsonObject());
            return;
        }
        if (conditions.isEmpty()) {
            pruneUnlocking(ability, state, unlocking);
            return;
        }
        unlocking.add("conditions", conditions);
    }

    private static JsonArray conditionsArray(JsonObject object) {
        if (object.has("conditions") && object.get("conditions").isJsonArray()) {
            return object.getAsJsonArray("conditions");
        }
        JsonArray conditions = new JsonArray();
        if (object.has("conditions") && object.get("conditions").isJsonObject()) {
            conditions.add(object.getAsJsonObject("conditions"));
        }
        object.add("conditions", conditions);
        return conditions;
    }

    private static void pruneUnlocking(JsonObject ability, JsonObject state, JsonObject unlocking) {
        if (unlocking.size() > 1) {
            return;
        }
        state.remove("unlocking");
        if (state.size() == 0) {
            ability.remove("state");
        }
    }

    private static JsonObject dummyJson(TreeEditorNode node, List<TreeEditorCostSchema> schemas) {
        JsonObject ability = new JsonObject();
        ability.addProperty("type", "palladium:dummy");

        JsonObject properties = new JsonObject();
        properties.addProperty("title", node.getTitle());
        if (!node.getDescription().isBlank() || node.hasSplitDescription()) {
            properties.add("description", descriptionJson(node));
        }
        properties.addProperty("icon", node.getIconId());
        properties.addProperty("hidden_in_bar", true);
        properties.addProperty("hidden_in_gui", false);
        properties.add("gui_position", positionArray(node.getGridX(), node.getGridY()));
        if (!node.getConnectionPath().isEmpty()) {
            properties.add(TreeConnectionPath.JSON_KEY, node.getConnectionPath().toJson());
        }
        ability.add("properties", properties);

        JsonObject cost = node.getCost().toJson(schemas);
        boolean hasParent = node.getParentKey() != null && !node.getParentKey().isBlank();
        JsonObject unlocking = createUnlocking(hasParent ? node.getParentKey() : null, cost);
        if (unlocking != null) {
            JsonObject state = new JsonObject();
            state.add("unlocking", unlocking);
            ability.add("state", state);
        }
        return ability;
    }

    private static JsonObject abilityUnlocked(String abilityKey) {
        JsonObject requires = new JsonObject();
        requires.addProperty("type", TYPE_ABILITY_UNLOCKED);
        requires.addProperty("ability", abilityKey);
        return requires;
    }

    private static String readType(@Nullable JsonObject object) {
        if (object == null || !object.has("type") || !object.get("type").isJsonPrimitive()) {
            return "";
        }
        return object.get("type").getAsString();
    }

    @Nullable
    private static String readAbilityKey(JsonObject unlocking) {
        if (!unlocking.has("ability") || !unlocking.get("ability").isJsonPrimitive()) {
            return null;
        }
        return unlocking.get("ability").getAsString();
    }

    private static int findAbilityUnlockedIndex(JsonArray conditions) {
        for (int index = 0; index < conditions.size(); index++) {
            JsonElement element = conditions.get(index);
            if (element.isJsonObject() && TYPE_ABILITY_UNLOCKED.equals(readType(element.getAsJsonObject()))) {
                return index;
            }
        }
        return -1;
    }

    private static JsonElement descriptionJson(TreeEditorNode node) {
        if (node.hasSplitDescription()) {
            JsonObject description = new JsonObject();
            description.addProperty("unlocked", node.getDescription());
            description.addProperty("locked", node.getLockedDescription());
            return description;
        }
        return new JsonPrimitive(node.getDescription());
    }

    private static JsonArray positionArray(float x, float y) {
        JsonArray array = new JsonArray();
        array.add(TreeEditorDraft.gridNumber(x));
        array.add(TreeEditorDraft.gridNumber(y));
        return array;
    }
}
