package com.github.bandithelps.client;

import com.github.bandithelps.commands.TreeEditorCommand;
import com.github.bandithelps.gui.screens.BodyDebugScreen;
import com.github.bandithelps.gui.screens.TreeEditorScreen;
import com.github.bandithelps.gui.tree.PowerSourceJson;
import com.github.bandithelps.gui.tree.TreeEditorDraft;
import com.github.bandithelps.gui.tree.TreeEditorExports;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.threetag.palladium.power.Power;
import net.threetag.palladium.registry.PalladiumRegistryKeys;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientScreenOpener {
    private ClientScreenOpener() {
    }

    public static void openBodyDebugScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new BodyDebugScreen());
    }

    public static void openTreeEditorScreen(String rawPowerId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (TreeEditorCommand.NEW_TOKEN.equals(rawPowerId)) {
            minecraft.setScreen(new TreeEditorScreen(TreeEditorDraft.blank()));
            return;
        }
        if (rawPowerId.startsWith(TreeEditorCommand.DRAFT_PREFIX)) {
            String name = rawPowerId.substring(TreeEditorCommand.DRAFT_PREFIX.length());
            Path file = TreeEditorExports.resolve(minecraft, name);
            if (file == null || !Files.isRegularFile(file)) {
                minecraft.player.sendSystemMessage(Component.literal("Unknown draft '" + name + "'."));
                return;
            }
            try {
                minecraft.setScreen(new TreeEditorScreen(TreeEditorExports.read(minecraft, file)));
            } catch (Exception exception) {
                minecraft.player.sendSystemMessage(Component.literal("Could not open draft '" + name + "': " + exception.getMessage()));
            }
            return;
        }
        Identifier powerId;
        try {
            powerId = Identifier.parse(rawPowerId);
        } catch (RuntimeException exception) {
            minecraft.player.sendSystemMessage(Component.literal("Invalid power id: " + rawPowerId));
            return;
        }
        var lookup = minecraft.level.registryAccess().lookupOrThrow(PalladiumRegistryKeys.POWER);
        ResourceKey<Power> key = ResourceKey.create(PalladiumRegistryKeys.POWER, powerId);
        Holder.Reference<Power> holder = lookup.get(key).orElse(null);
        if (holder == null) {
            minecraft.player.sendSystemMessage(Component.literal("Unknown power: " + powerId));
            return;
        }
        String sourceJson = PowerSourceJson.read(minecraft, powerId);
        minecraft.setScreen(new TreeEditorScreen(TreeEditorDraft.fromPower(powerId, holder.value(), sourceJson)));
    }
}
