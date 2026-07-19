package com.github.bandithelps.commands;

import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.BodySyncEvents;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.entities.BlackwhipEntity;
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
 * {@code /yha blackwhip color <inner> <outer> <glow>} customizes a player's Blackwhip colors
 * (stored as body strings, so the choice persists and syncs to all viewers). A 2-arg form
 * {@code /yha blackwhip color <inner> <glow>} derives outer from glow. {@code reset} restores
 * the canon teal/black look. Active whips update immediately.
 */
public final class BlackwhipCommand {

    private static final DynamicCommandExceptionType INVALID_HEX = new DynamicCommandExceptionType(value ->
            Component.literal("Invalid hex color '" + value + "'. Use RRGGBB, #RRGGBB, or AARRGGBB.")
    );

    private BlackwhipCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext context) {
        LiteralArgumentBuilder<CommandSourceStack> blackwhip = Commands.literal("blackwhip");

        // Quick alias: /yha blackwhip reset [player]
        blackwhip.then(Commands.literal("reset")
                .executes(c -> resetColors(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> resetColors(c.getSource(), EntityArgument.getPlayer(c, "player")))));

        blackwhip.then(Commands.literal("color")
                .then(Commands.literal("reset")
                        .executes(c -> resetColors(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> resetColors(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(Commands.argument("inner", StringArgumentType.word())
                        .then(Commands.argument("outerOrGlow", StringArgumentType.word())
                                // 2-arg: /yha blackwhip color <inner> <glow>
                                .executes(c -> setColorsTwoArg(
                                        c.getSource(),
                                        c.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(c, "inner"),
                                        StringArgumentType.getString(c, "outerOrGlow")))
                                // 3-arg: /yha blackwhip color <inner> <outer> <glow> [player]
                                .then(Commands.argument("glow", StringArgumentType.word())
                                        .executes(c -> setColors(
                                                c.getSource(),
                                                c.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(c, "inner"),
                                                StringArgumentType.getString(c, "outerOrGlow"),
                                                StringArgumentType.getString(c, "glow")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(c -> setColors(
                                                        c.getSource(),
                                                        EntityArgument.getPlayer(c, "player"),
                                                        StringArgumentType.getString(c, "inner"),
                                                        StringArgumentType.getString(c, "outerOrGlow"),
                                                        StringArgumentType.getString(c, "glow"))))))));

        builder.then(blackwhip);
    }

    private static int setColorsTwoArg(CommandSourceStack source, ServerPlayer player, String innerHex, String glowHex)
            throws CommandSyntaxException {
        validateHex(innerHex);
        validateHex(glowHex);
        int glow = BlackwhipColors.resolve(glowHex, BlackwhipColors.DEFAULT_GLOW, 0xB3);
        String outerHex = String.format("%08X", BlackwhipColors.deriveOuter(glow));
        return applyColors(source, player, innerHex, outerHex, glowHex, true);
    }

    private static int setColors(CommandSourceStack source, ServerPlayer player,
                                 String innerHex, String outerHex, String glowHex) throws CommandSyntaxException {
        validateHex(innerHex);
        validateHex(outerHex);
        validateHex(glowHex);
        return applyColors(source, player, innerHex, outerHex, glowHex, false);
    }

    private static int applyColors(CommandSourceStack source, ServerPlayer player,
                                   String innerHex, String outerHex, String glowHex, boolean derivedOuter) {
        BodyAttachments.get(player).setCustomString(player, BlackwhipColors.PART, BlackwhipColors.CORE_KEY, innerHex);
        BodyAttachments.get(player).setCustomString(player, BlackwhipColors.PART, BlackwhipColors.OUTER_KEY, outerHex);
        BodyAttachments.get(player).setCustomString(player, BlackwhipColors.PART, BlackwhipColors.GLOW_KEY, glowHex);
        BodySyncEvents.syncNow(player);
        pushColorsToActiveWhips(player);
        String note = derivedOuter ? " (outer derived from glow)" : "";
        source.sendSuccess(() -> Component.literal("Set Blackwhip colors for " + player.getName().getString()
                + " (inner=" + innerHex + ", outer=" + outerHex + ", glow=" + glowHex + ")" + note
                + ". Active and new whips use these colors."), true);
        return 1;
    }

    private static int resetColors(CommandSourceStack source, ServerPlayer player) {
        BodyAttachments.get(player).removeCustomString(player, BlackwhipColors.PART, BlackwhipColors.CORE_KEY);
        BodyAttachments.get(player).removeCustomString(player, BlackwhipColors.PART, BlackwhipColors.OUTER_KEY);
        BodyAttachments.get(player).removeCustomString(player, BlackwhipColors.PART, BlackwhipColors.GLOW_KEY);
        BodySyncEvents.syncNow(player);
        pushColorsToActiveWhips(player);
        source.sendSuccess(() -> Component.literal("Reset Blackwhip colors for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static void pushColorsToActiveWhips(ServerPlayer player) {
        int core = BlackwhipColors.getCore(player);
        int outer = BlackwhipColors.getOuter(player);
        int glow = BlackwhipColors.getGlow(player);
        int ownerId = player.getId();
        for (BlackwhipChainEntity chain : BlackwhipChainEntity.activeServerChains()) {
            if (chain.getOwnerId() == ownerId) {
                chain.setColors(core, outer, glow);
            }
        }
        for (BlackwhipEntity whip : BlackwhipEntity.activeServerWhips()) {
            if (whip.getOwnerId() == ownerId) {
                whip.setColors(core, outer, glow);
            }
        }
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
