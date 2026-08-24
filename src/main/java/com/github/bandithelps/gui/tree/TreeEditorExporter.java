package com.github.bandithelps.gui.tree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
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

    public static String toJson(TreeEditorDraft draft, List<TreeEditorCostSchema> schemas) {
        JsonObject root = draft.getExtraRoot() == null ? new JsonObject() : draft.getExtraRoot().deepCopy();
        root.addProperty("name", draft.getPowerName());
        if (draft.getParentPower() != null && !draft.getParentPower().isBlank()) {
            root.addProperty("parent", draft.getParentPower());
        } else {
            root.remove("parent");
        }
        if (draft.getPowerIcon() != null && !draft.getPowerIcon().isBlank()) {
            root.addProperty("icon", draft.getPowerIcon());
        }
        root.addProperty("gui_display_type", draft.getGuiDisplayType());

        JsonObject abilities = new JsonObject();
        for (TreeEditorNode node : draft.getNodes()) {
            TreeEditorStateSync.applyParentAndCost(node, schemas);
            abilities.add(node.getKey(), node.toAbilityJson());
        }
        root.add("abilities", abilities);
        return GSON.toJson(root);
    }

    public static Path writeToGameDir(Minecraft minecraft, TreeEditorDraft draft, String json) throws IOException {
        return writeToGameDir(minecraft, draft.getExportFileName(), json);
    }

    public static Path writeToGameDir(Minecraft minecraft, String fileName, String json) throws IOException {
        Path directory = TreeEditorExports.directory(minecraft);
        Files.createDirectories(directory);
        String sanitized = TreeEditorDraft.sanitizeFileName(fileName);
        if (sanitized.isBlank()) {
            throw new IOException("Export file name is empty.");
        }
        Path file = directory.resolve(sanitized + ".json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }
}
