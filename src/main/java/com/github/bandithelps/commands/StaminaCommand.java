package com.github.bandithelps.commands;

import com.github.bandithelps.utils.stamina.StaminaUtil;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class StaminaCommand {

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext context) {
        builder.then(Commands.literal("stamina")
                .then(Commands.literal("use")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(-9999))
                                .executes(c -> useStamina(
                                        c.getSource(),
                                        IntegerArgumentType.getInteger(c, "amount"),
                                        c.getSource().getPlayerOrException()
                                ))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> useStamina(
                                                c.getSource(),
                                                IntegerArgumentType.getInteger(c, "amount"),
                                                EntityArgument.getPlayer(c, "player")
                                        )))))
                .then(Commands.literal("set")
                        .then(Commands.literal("regenAmount")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(c -> setStaminaValue(
                                                c.getSource(),
                                                IntegerArgumentType.getInteger(c, "amount"),
                                                c.getSource().getPlayerOrException(),
                                                "regenAmount"
                                        ))))
                        .then(Commands.literal("exhaustionLevel")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0, 4))
                                        .executes(c -> setStaminaValue(
                                                c.getSource(),
                                                IntegerArgumentType.getInteger(c, "amount"),
                                                c.getSource().getPlayerOrException(),
                                                "exhaustionLevel"
                                        ))))));
    }

    private static int useStamina(CommandSourceStack source, int amount, ServerPlayer player) throws CommandSyntaxException {
        StaminaUtil.forceUseStamina(player, amount);
        source.sendSuccess(() -> Component.literal("Used " + amount + " stamina for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static int setStaminaValue(CommandSourceStack source, int amount, ServerPlayer player, String dataName) throws CommandSyntaxException {
        StaminaUtil.forceSetStaminaData(player, amount, dataName);
        source.sendSuccess(() -> Component.literal("Set the value of " + dataName + " for " + player.getName().getString() + " to " + amount), true);
        return 1;
    }
}
