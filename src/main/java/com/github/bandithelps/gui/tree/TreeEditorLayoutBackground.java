package com.github.bandithelps.gui.tree;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;

public final class TreeEditorLayoutBackground {
    public static final Identifier FALLBACK = Identifier.withDefaultNamespace("textures/block/cyan_concrete.png");

    private TreeEditorLayoutBackground() {
    }

    public static Identifier resolve(Minecraft minecraft, Identifier powerId) {
        Identifier layoutId = Identifier.fromNamespaceAndPath(
                powerId.getNamespace(),
                "palladium/ui_layouts/power/" + powerId.getPath() + ".json"
        );
        var resource = minecraft.getResourceManager().getResource(layoutId);
        if (resource.isEmpty()) {
            return FALLBACK;
        }
        try (Reader reader = resource.get().openAsReader()) {
            Identifier found = findRepeatingTexture(JsonParser.parseReader(reader));
            return found != null ? found : FALLBACK;
        } catch (Exception ignored) {
            return FALLBACK;
        }
    }

    @Nullable
    private static Identifier findRepeatingTexture(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("type")
                    && "palladium:repeating_texture".equals(object.get("type").getAsString())
                    && object.has("texture")) {
                try {
                    return Identifier.parse(object.get("texture").getAsString());
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
            for (var entry : object.entrySet()) {
                Identifier found = findRepeatingTexture(entry.getValue());
                if (found != null) {
                    return found;
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                Identifier found = findRepeatingTexture(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
