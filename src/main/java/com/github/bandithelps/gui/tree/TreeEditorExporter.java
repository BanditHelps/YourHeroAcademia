package com.github.bandithelps.gui.tree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TreeEditorExporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private TreeEditorExporter() {
    }

    public static String toJson(TreeEditorDraft draft) {
        JsonObject root = new JsonObject();
        root.addProperty("power", draft.getPowerId().toString());
        root.addProperty("background", draft.getBackgroundTexture().toString());

        JsonObject updated = new JsonObject();
        JsonObject parentChanges = new JsonObject();
        JsonObject newNodes = new JsonObject();

        for (TreeEditorNode node : draft.getNodes()) {
            if (node.isCreated()) {
                newNodes.add(node.getKey(), dummyJson(node));
                continue;
            }
            if (node.positionChanged() || node.metadataChanged()) {
                JsonObject update = new JsonObject();
                if (node.positionChanged()) {
                    update.add("gui_position", positionArray(node.getGridX(), node.getGridY()));
                }
                if (node.metadataChanged()) {
                    update.addProperty("title", node.getTitle());
                    update.addProperty("description", node.getDescription());
                    update.addProperty("icon", node.getIconId());
                    update.add("cost", node.getCost().toJson(List.of()));
                }
                updated.add(node.getOriginalKey(), update);
            }
            if (node.parentChanged()) {
                if (node.getParentKey() == null || node.getParentKey().isBlank()) {
                    parentChanges.add(node.getOriginalKey(), JsonNull.INSTANCE);
                } else {
                    parentChanges.addProperty(node.getOriginalKey(), node.getParentKey());
                }
            }
        }

        root.add("updated_nodes", updated);
        root.add("parent_changes", parentChanges);
        root.add("new_nodes", newNodes);
        return GSON.toJson(root);
    }

    public static Path writeToGameDir(Minecraft minecraft, TreeEditorDraft draft) throws IOException {
        Path directory = minecraft.gameDirectory.toPath().resolve("yha_exports");
        Files.createDirectories(directory);
        String fileName = draft.getPowerId().toString().replace(':', '_') + "_tree.json";
        Path file = directory.resolve(fileName);
        Files.writeString(file, toJson(draft), StandardCharsets.UTF_8);
        return file;
    }

    private static JsonObject dummyJson(TreeEditorNode node) {
        JsonObject ability = new JsonObject();
        ability.addProperty("type", "palladium:dummy");

        JsonObject properties = new JsonObject();
        properties.addProperty("title", node.getTitle());
        if (!node.getDescription().isBlank()) {
            properties.addProperty("description", node.getDescription());
        }
        properties.addProperty("icon", node.getIconId());
        properties.addProperty("hidden_in_bar", true);
        properties.addProperty("hidden_in_gui", false);
        properties.add("gui_position", positionArray(node.getGridX(), node.getGridY()));
        ability.add("properties", properties);

        JsonObject cost = node.getCost().toJson(List.of());
        boolean hasParent = node.getParentKey() != null && !node.getParentKey().isBlank();
        if (cost != null || hasParent) {
            JsonObject unlocking = new JsonObject();
            unlocking.addProperty("type", "palladium:buyable");
            if (cost != null) {
                unlocking.add("cost", cost);
            }
            if (hasParent) {
                JsonObject requires = new JsonObject();
                requires.addProperty("type", "palladium:ability_unlocked");
                requires.addProperty("ability", node.getParentKey());
                unlocking.add("requires", requires);
            }
            JsonObject state = new JsonObject();
            state.add("unlocking", unlocking);
            ability.add("state", state);
        }
        return ability;
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
