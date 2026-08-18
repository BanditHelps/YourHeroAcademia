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
            if (node.positionChanged() || node.metadataChanged()) {
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
        String type = readType(unlocking);
        if (!type.isEmpty() && !TYPE_ABILITY_UNLOCKED.equals(type) && !TYPE_BUYABLE.equals(type)) {
            return;
        }

        boolean hasParent = node.getParentKey() != null && !node.getParentKey().isBlank();
        JsonObject costJson = node.getCost().toJson(schemas);

        if (unlocking == null) {
            if (!hasParent && costJson == null) {
                return;
            }
            unlocking = new JsonObject();
            unlocking.addProperty("type", TYPE_BUYABLE);
            if (costJson != null) {
                unlocking.add("cost", costJson);
            }
            if (hasParent) {
                unlocking.add("requires", abilityUnlocked(node.getParentKey()));
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
                JsonObject buyable = new JsonObject();
                buyable.addProperty("type", TYPE_BUYABLE);
                buyable.add("cost", costJson);
                if (parent != null && !parent.isBlank()) {
                    buyable.add("requires", abilityUnlocked(parent));
                }
                state.add("unlocking", buyable);
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

        if (node.costChanged()) {
            if (costJson == null) {
                unlocking.remove("cost");
            } else {
                unlocking.add("cost", costJson);
            }
        }
        if (node.parentChanged()) {
            patchBuyableParent(unlocking, node.getParentKey());
        }
        pruneUnlocking(ability, state, unlocking);
    }

    private static void patchBuyableParent(JsonObject unlocking, @Nullable String parentKey) {
        boolean hasParent = parentKey != null && !parentKey.isBlank();
        if (unlocking.has("requires")) {
            if (!hasParent) {
                unlocking.remove("requires");
                return;
            }
            JsonObject requires = unlocking.get("requires").isJsonObject() ? unlocking.getAsJsonObject("requires") : null;
            if (requires != null && TYPE_ABILITY_UNLOCKED.equals(readType(requires))) {
                requires.addProperty("ability", parentKey);
            } else {
                unlocking.add("requires", abilityUnlocked(parentKey));
            }
            return;
        }
        if (unlocking.has("conditions") && unlocking.get("conditions").isJsonArray()) {
            JsonArray conditions = unlocking.getAsJsonArray("conditions");
            int index = findAbilityUnlockedIndex(conditions);
            if (!hasParent) {
                if (index >= 0) {
                    conditions.remove(index);
                }
                if (conditions.isEmpty()) {
                    unlocking.remove("conditions");
                }
                return;
            }
            if (index >= 0 && conditions.get(index).isJsonObject()) {
                conditions.get(index).getAsJsonObject().addProperty("ability", parentKey);
            } else {
                conditions.add(abilityUnlocked(parentKey));
            }
            return;
        }
        if (hasParent) {
            unlocking.add("requires", abilityUnlocked(parentKey));
        }
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
        ability.add("properties", properties);

        JsonObject cost = node.getCost().toJson(schemas);
        boolean hasParent = node.getParentKey() != null && !node.getParentKey().isBlank();
        if (cost != null || hasParent) {
            JsonObject unlocking = new JsonObject();
            unlocking.addProperty("type", TYPE_BUYABLE);
            if (cost != null) {
                unlocking.add("cost", cost);
            }
            if (hasParent) {
                unlocking.add("requires", abilityUnlocked(node.getParentKey()));
            }
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
        array.add(number(x));
        array.add(number(y));
        return array;
    }

    private static JsonPrimitive number(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001F) {
            return new JsonPrimitive(Math.round(value));
        }
        return new JsonPrimitive(value);
    }
}
