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
    private static final float STEP_MIN = 0.05F;
    private static final float STEP_MAX = 2.0F;
    private static final float STEP_TICK = 0.05F;
    private static Identifier background = TreeEditorLayoutBackground.FALLBACK;
    private static float step = TreeEditorDraft.GRID_SNAP;
    private static boolean loaded;

    private TreeEditorSettings() {
    }

    public static Identifier background(Minecraft minecraft) {
        load(minecraft);
        return background;
    }

    public static void setBackground(Minecraft minecraft, Identifier texture) {
        load(minecraft);
        background = texture == null ? TreeEditorLayoutBackground.FALLBACK : texture;
        loaded = true;
        save(minecraft);
    }

    public static float step(Minecraft minecraft) {
        load(minecraft);
        return step;
    }

    public static void setStep(Minecraft minecraft, float value) {
        load(minecraft);
        step = clampStep(value);
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
            if (root == null) {
                return;
            }
            if (root.has("background") && root.get("background").isJsonPrimitive()) {
                background = Identifier.parse(root.get("background").getAsString());
            }
            if (root.has("step") && root.get("step").isJsonPrimitive()) {
                step = clampStep(root.get("step").getAsFloat());
            }
        } catch (Exception ignored) {
            background = TreeEditorLayoutBackground.FALLBACK;
            step = TreeEditorDraft.GRID_SNAP;
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
            root.addProperty("step", step);
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static float clampStep(float value) {
        float clamped = Math.max(STEP_MIN, Math.min(STEP_MAX, value));
        float snapped = Math.round(clamped / STEP_TICK) * STEP_TICK;
        return Math.max(STEP_MIN, Math.min(STEP_MAX, snapped));
    }

    private static Path file(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }
}
