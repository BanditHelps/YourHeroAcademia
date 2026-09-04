package com.github.bandithelps.utils;

import net.minecraft.network.chat.Component;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.logic.value.holder.TextComponentValueHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the new TextComponentValue from palladium that allows power and ability names to be dynamic.
 * Used primarily for the power tree editor
 */
public final class TextComponentHolders {
    private TextComponentHolders() {
    }

    public static Component resolve(@Nullable TextComponentValueHolder holder, @Nullable DataContext context) {
        if (holder == null) {
            return Component.empty();
        }
        Component component = holder.get(context == null ? DataContext.create() : context);
        return component == null ? Component.empty() : component;
    }

    public static String resolveString(@Nullable TextComponentValueHolder holder, @Nullable DataContext context) {
        return resolve(holder, context).getString();
    }
}
