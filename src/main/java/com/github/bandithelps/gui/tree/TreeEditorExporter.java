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

public final class TreeEditorExporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private TreeEditorExporter() {
    }

    public static String toJson(TreeEditorDraft draft) {
        JsonObject root = new JsonObject();
        root.addProperty("power", draft.getPowerId().toString());

        JsonObject updated = new JsonObject();
        JsonObject parentChanges = new JsonObject();
        JsonObject newNodes = new JsonObject();

        for (TreeEditorNode node : draft.getNodes()) {
            if (node.isCreated()) {
                newNodes.add(node.getKey(), dummyJson(node));
                continue;
            }
            if (node.positionChanged()) {
                JsonObject update = new JsonObject();
                update.add("gui_position", positionArray(node.getGridX(), node.getGridY()));
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
        properties.addProperty("icon", "minecraft:paper");
        properties.addProperty("hidden_in_bar", true);
        properties.addProperty("hidden_in_gui", false);
        properties.add("gui_position", positionArray(node.getGridX(), node.getGridY()));
        ability.add("properties", properties);

        JsonObject unlocking = new JsonObject();
        unlocking.addProperty("type", "palladium:buyable");
        JsonObject cost = new JsonObject();
        cost.addProperty("type", "yha:upgrade_point");
        cost.addProperty("points", node.getCostPoints());
        unlocking.add("cost", cost);
        if (node.getParentKey() != null && !node.getParentKey().isBlank()) {
            JsonObject requires = new JsonObject();
            requires.addProperty("type", "palladium:ability_unlocked");
            requires.addProperty("ability", node.getParentKey());
            unlocking.add("requires", requires);
        }
        JsonObject state = new JsonObject();
        state.add("unlocking", unlocking);
        ability.add("state", state);
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
