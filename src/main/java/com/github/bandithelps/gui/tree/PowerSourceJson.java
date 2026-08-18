package com.github.bandithelps.gui.tree;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;

import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public final class PowerSourceJson {
    private PowerSourceJson() {
    }

    public static String read(Minecraft minecraft, Identifier powerId) {
        String fromServer = readFromIntegratedServer(minecraft, powerId);
        if (!fromServer.isEmpty()) {
            return fromServer;
        }
        return readFromModJars(powerId);
    }

    private static String readFromIntegratedServer(Minecraft minecraft, Identifier powerId) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return "";
        }
        Identifier resourceId = Identifier.fromNamespaceAndPath(
                powerId.getNamespace(),
                "palladium/power/" + powerId.getPath() + ".json"
        );
        Resource resource = server.getResourceManager().getResource(resourceId).orElse(null);
        if (resource == null) {
            return "";
        }
        try (Reader reader = resource.openAsReader(); StringWriter writer = new StringWriter()) {
            reader.transferTo(writer);
            return writer.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private static String readFromModJars(Identifier powerId) {
        String namespace = powerId.getNamespace();
        String relativePath = "data/" + namespace + "/palladium/power/" + powerId.getPath() + ".json";
        IModFileInfo named = ModList.get().getModFileById(namespace);
        if (named != null) {
            String json = readFromModFile(named, relativePath);
            if (!json.isEmpty()) {
                return json;
            }
        }
        for (IModInfo mod : ModList.get().getMods()) {
            String json = readFromModFile(mod.getOwningFile(), relativePath);
            if (!json.isEmpty()) {
                return json;
            }
        }
        return "";
    }

    private static String readFromModFile(IModFileInfo modFileInfo, String relativePath) {
        if (modFileInfo == null) {
            return "";
        }
        try {
            byte[] bytes = modFileInfo.getFile().getContents().readFile(relativePath);
            if (bytes == null) {
                return "";
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return "";
        }
    }
}
