package com.github.bandithelps.commands;

import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.BodySyncEvents;
import com.github.bandithelps.utils.blackwhip.BlackwhipColors;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /yha blackwhip color <core> <glow>} customizes a player's Blackwhip colors (stored as body
 * strings, so the choice persists and syncs to all viewers). {@code /yha blackwhip color reset}
 * restores the canon teal/black look.
 */
public final class BlackwhipCommand {

    private static final DynamicCommandExceptionType INVALID_HEX = new DynamicCommandExceptionType(value ->
            Component.literal("Invalid hex color '" + value + "'. Use RRGGBB, #RRGGBB, or AARRGGBB.")
    );

    private BlackwhipCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext context) {
        LiteralArgumentBuilder<CommandSourceStack> blackwhip = Commands.literal("blackwhip");

        blackwhip.then(Commands.literal("color")
                .then(Commands.literal("reset")
                        .executes(c -> resetColors(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> resetColors(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(Commands.argument("core", StringArgumentType.word())
                        .then(Commands.argument("glow", StringArgumentType.word())
                                .executes(c -> setColors(
                                        c.getSource(),
                                        c.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(c, "core"),
                                        StringArgumentType.getString(c, "glow")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> setColors(
                                                c.getSource(),
                                                EntityArgument.getPlayer(c, "player"),
                                                StringArgumentType.getString(c, "core"),
                                                StringArgumentType.getString(c, "glow")))))));

        builder.then(blackwhip);
    }

    private static int setColors(CommandSourceStack source, ServerPlayer player, String coreHex, String glowHex) throws CommandSyntaxException {
        validateHex(coreHex);
        validateHex(glowHex);
        BodyAttachments.get(player).setCustomString(player, BlackwhipColors.PART, BlackwhipColors.CORE_KEY, coreHex);
        BodyAttachments.get(player).setCustomString(player, BlackwhipColors.PART, BlackwhipColors.GLOW_KEY, glowHex);
        BodySyncEvents.syncNow(player);
        source.sendSuccess(() -> Component.literal("Set Blackwhip colors for " + player.getName().getString()
                + " (core=" + coreHex + ", glow=" + glowHex + "). New whips will use these colors."), true);
        return 1;
    }

    private static int resetColors(CommandSourceStack source, ServerPlayer player) {
        BodyAttachments.get(player).removeCustomString(player, BlackwhipColors.PART, BlackwhipColors.CORE_KEY);
        BodyAttachments.get(player).removeCustomString(player, BlackwhipColors.PART, BlackwhipColors.GLOW_KEY);
        BodySyncEvents.syncNow(player);
        source.sendSuccess(() -> Component.literal("Reset Blackwhip colors for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static void validateHex(String hex) throws CommandSyntaxException {
        String normalized = hex.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        } else if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        if (!(normalized.length() == 6 || normalized.length() == 8) || !normalized.matches("[0-9A-Fa-f]+")) {
            throw INVALID_HEX.create(hex);
        }
    }
}
