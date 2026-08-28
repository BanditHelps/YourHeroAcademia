package com.github.bandithelps.commands;

import com.github.bandithelps.capabilities.creation.CreationAttachments;
import com.github.bandithelps.capabilities.creation.CreationData;
import com.github.bandithelps.capabilities.creation.CreationSyncEvents;
import com.github.bandithelps.creation.CreationCatalog;
import com.github.bandithelps.creation.CreationEnchantCatalog;
import com.github.bandithelps.creation.CreationEnchantEntry;
import com.github.bandithelps.creation.CreationEntry;
import com.github.bandithelps.creation.CreationPotionCatalog;
import com.github.bandithelps.creation.CreationPotionEntry;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class CreationCommand {
    private static final DynamicCommandExceptionType UNKNOWN_ITEM = new DynamicCommandExceptionType(value ->
            Component.translatable("commands.yha.creation.unknown_item", value)
    );

    private static final SuggestionProvider<CommandSourceStack> RECIPE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(allRecipeIds(), builder);

    private CreationCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext context) {
        builder.then(Commands.literal("creation")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(recipeAction("unlock", true))
                .then(recipeAction("lock", false)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> recipeAction(String name, boolean unlocking) {
        return Commands.literal(name)
                .then(Commands.literal("*")
                        .executes(c -> apply(c, unlocking, true, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> apply(c, unlocking, true, EntityArgument.getPlayer(c, "player")))))
                .then(Commands.argument("item", IdentifierArgument.id())
                        .suggests(RECIPE_SUGGESTIONS)
                        .executes(c -> apply(c, unlocking, false, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> apply(c, unlocking, false, EntityArgument.getPlayer(c, "player")))));
    }

    private static int apply(
            CommandContext<CommandSourceStack> context,
            boolean unlocking,
            boolean all,
            ServerPlayer player
    ) throws CommandSyntaxException {
        CreationData data = CreationAttachments.get(player);
        int changed;
        if (all) {
            changed = applyAll(data, unlocking);
        } else {
            changed = applyOne(data, IdentifierArgument.getId(context, "item"), unlocking);
        }
        CreationSyncEvents.syncNow(player);
        int count = changed;
        String key = unlocking ? "commands.yha.creation.unlock.success" : "commands.yha.creation.lock.success";
        context.getSource().sendSuccess(
                () -> Component.translatable(key, count, player.getName().getString()),
                true
        );
        return changed;
    }

    private static int applyAll(CreationData data, boolean unlocking) {
        int changed = 0;
        for (CreationEntry entry : CreationCatalog.getInstance().allEntries()) {
            changed += applyItem(data, entry.itemId(), unlocking);
        }
        for (CreationEnchantEntry entry : CreationEnchantCatalog.getInstance().allEntries()) {
            changed += applyEnchant(data, entry.enchantId(), unlocking);
        }
        for (CreationPotionEntry entry : CreationPotionCatalog.getInstance().allEntries()) {
            changed += applyPotion(data, entry.effectId(), unlocking);
        }
        return changed;
    }

    private static int applyOne(CreationData data, Identifier parsed, boolean unlocking) throws CommandSyntaxException {
        int changed = 0;
        boolean found = false;
        CreationEntry item = CreationCatalog.getInstance().parentOf(parsed).orElse(null);
        if (item != null) {
            found = true;
            changed += applyItem(data, item.itemId(), unlocking);
        }
        if (CreationEnchantCatalog.getInstance().get(parsed).isPresent()) {
            found = true;
            changed += applyEnchant(data, parsed, unlocking);
        }
        if (CreationPotionCatalog.getInstance().get(parsed).isPresent()) {
            found = true;
            changed += applyPotion(data, parsed, unlocking);
        }
        if (!found) {
            throw UNKNOWN_ITEM.create(parsed.toString());
        }
        return changed;
    }

    private static int applyItem(CreationData data, Identifier itemId, boolean unlocking) {
        if (unlocking) {
            if (data.isUnlocked(itemId)) {
                return 0;
            }
            data.unlock(itemId);
            return 1;
        }
        return data.lock(itemId) ? 1 : 0;
    }

    private static int applyEnchant(CreationData data, Identifier enchantId, boolean unlocking) {
        if (unlocking) {
            if (data.isEnchantUnlocked(enchantId)) {
                return 0;
            }
            data.unlockEnchant(enchantId);
            return 1;
        }
        return data.lockEnchant(enchantId) ? 1 : 0;
    }

    private static int applyPotion(CreationData data, Identifier effectId, boolean unlocking) {
        if (unlocking) {
            if (data.isPotionUnlocked(effectId)) {
                return 0;
            }
            data.unlockPotion(effectId);
            return 1;
        }
        return data.lockPotion(effectId) ? 1 : 0;
    }

    private static List<Identifier> allRecipeIds() {
        Set<Identifier> ids = new LinkedHashSet<>();
        for (CreationEntry entry : CreationCatalog.getInstance().allEntries()) {
            ids.add(entry.itemId());
        }
        for (CreationEnchantEntry entry : CreationEnchantCatalog.getInstance().allEntries()) {
            ids.add(entry.enchantId());
        }
        for (CreationPotionEntry entry : CreationPotionCatalog.getInstance().allEntries()) {
            ids.add(entry.effectId());
        }
        return new ArrayList<>(ids);
    }
}
