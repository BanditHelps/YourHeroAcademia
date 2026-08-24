package com.github.bandithelps.gui.tree;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TreeEditorExports {
    public static final String FOLDER = "yha_exports";

    private TreeEditorExports() {
    }

    public static Path directory(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath().resolve(FOLDER);
    }

    public static List<Path> listJsonFiles(Minecraft minecraft) {
        Path directory = directory(minecraft);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    files.add(path);
                }
            }
        } catch (IOException ignored) {
            return List.of();
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()));
        return files;
    }

    public static TreeEditorDraft read(Minecraft minecraft, Path file) throws IOException {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        JsonElement element = JsonParser.parseString(json);
        if (element == null || !element.isJsonObject()) {
            throw new IOException("File is not a JSON object.");
        }
        JsonObject root = element.getAsJsonObject();
        String fileName = file.getFileName().toString();
        if (fileName.toLowerCase().endsWith(".json")) {
            fileName = fileName.substring(0, fileName.length() - 5);
        }
        Identifier powerId = Identifier.fromNamespaceAndPath("yha", TreeEditorDraft.sanitizeFileName(fileName));
        TreeEditorDraft draft = TreeEditorDraft.fromJson(powerId, root);
        draft.setExportFileName(fileName);
        return draft;
    }

    @Nullable
    public static TreeEditorDraft readOrNull(Minecraft minecraft, Path file) {
        try {
            return read(minecraft, file);
        } catch (Exception ignored) {
            return null;
        }
    }
}
