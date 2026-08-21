package com.github.bandithelps.commands;

import com.github.bandithelps.network.OpenTreeEditorScreenPayload;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.threetag.palladium.power.Power;
import net.threetag.palladium.registry.PalladiumRegistryKeys;

public final class TreeEditorCommand {
    private static final DynamicCommandExceptionType UNKNOWN_POWER = new DynamicCommandExceptionType(value ->
            Component.literal("Unknown power '" + value + "'.")
    );

    private static final SuggestionProvider<CommandSourceStack> POWER_SUGGESTIONS = (context, builder) -> {
        var lookup = context.getSource().registryAccess().lookupOrThrow(PalladiumRegistryKeys.POWER);
        return SharedSuggestionProvider.suggest(
                lookup.listElementIds().map(key -> key.identifier().toString()).toList(),
                builder
        );
    };

    private TreeEditorCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext context) {
        builder.then(Commands.literal("tree")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("editor")
                        .then(Commands.argument("power", StringArgumentType.greedyString())
                                .suggests(POWER_SUGGESTIONS)
                                .executes(c -> openEditor(c.getSource(), StringArgumentType.getString(c, "power"))))));
    }

    private static int openEditor(CommandSourceStack source, String rawId) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Identifier powerId;
        try {
            powerId = Identifier.parse(rawId);
        } catch (RuntimeException exception) {
            throw UNKNOWN_POWER.create(rawId);
        }
        var lookup = source.registryAccess().lookupOrThrow(PalladiumRegistryKeys.POWER);
        ResourceKey<Power> key = ResourceKey.create(PalladiumRegistryKeys.POWER, powerId);
        Holder.Reference<Power> holder = lookup.get(key).orElseThrow(() -> UNKNOWN_POWER.create(rawId));
        PacketDistributor.sendToPlayer(player, new OpenTreeEditorScreenPayload(holder.key().identifier().toString()));
        source.sendSuccess(() -> Component.literal("Opened tree editor for " + powerId), false);
        return 1;
    }
}
