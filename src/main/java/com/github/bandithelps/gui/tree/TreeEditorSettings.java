package com.github.bandithelps.gui.tree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TreeEditorSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "yha_tree_editor.json";
    private static Identifier background = TreeEditorLayoutBackground.FALLBACK;
    private static boolean loaded;

    private TreeEditorSettings() {
    }

    public static Identifier background(Minecraft minecraft) {
        load(minecraft);
        return background;
    }

    public static void setBackground(Minecraft minecraft, Identifier texture) {
        background = texture == null ? TreeEditorLayoutBackground.FALLBACK : texture;
        loaded = true;
        save(minecraft);
    }

    private static void load(Minecraft minecraft) {
        if (loaded || minecraft == null) {
            return;
        }
        loaded = true;
        Path file = file(minecraft);
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            if (root != null && root.has("background") && root.get("background").isJsonPrimitive()) {
                background = Identifier.parse(root.get("background").getAsString());
            }
        } catch (Exception ignored) {
            background = TreeEditorLayoutBackground.FALLBACK;
        }
    }

    private static void save(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        try {
            Path file = file(minecraft);
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("background", background.toString());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static Path file(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }
}
