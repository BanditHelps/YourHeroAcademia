package com.github.bandithelps.gui.screens;

import com.github.bandithelps.gui.tree.TreeEditorNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.threetag.palladium.client.renderer.icon.IconRenderer;
import net.threetag.palladium.icon.Icon;
import net.threetag.palladium.logic.context.DataContext;
import org.jetbrains.annotations.Nullable;

public final class TreeEditorIcons {
    private TreeEditorIcons() {
    }

    public static void draw(GuiGraphicsExtractor graphics, @Nullable String iconId, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        if (iconId == null || iconId.isBlank()) {
            drawFallback(graphics, x, y);
            return;
        }
        Icon icon = TreeEditorNode.parseIcon(iconId);
        if (icon != null && minecraft.player != null) {
            try {
                IconRenderer.drawIcon(icon, minecraft, graphics, DataContext.forEntity(minecraft.player), x, y);
                return;
            } catch (RuntimeException ignored) {
                // Fall through to item / empty preview.
            }
        }
        ItemStack stack = itemStack(iconId);
        if (!stack.isEmpty()) {
            graphics.item(stack, x, y);
            return;
        }
        drawFallback(graphics, x, y);
    }

    public static ItemStack itemStack(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            Identifier identifier = Identifier.parse(id);
            return BuiltInRegistries.ITEM.get(identifier)
                    .map(holder -> new ItemStack(holder.value()))
                    .orElse(ItemStack.EMPTY);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static void drawFallback(GuiGraphicsExtractor graphics, int x, int y) {
        TreeEditorTheme.rect(graphics, x, y, 16, 16, TreeEditorTheme.INPUT, TreeEditorTheme.BORDER);
    }
}
