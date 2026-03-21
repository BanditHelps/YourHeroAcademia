package com.github.bandithelps.commands;

import com.github.bandithelps.utils.StaminaUtil;
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
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
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
                                        ))))));
    }

    private static int useStamina(CommandSourceStack source, int amount, ServerPlayer player) throws CommandSyntaxException {
        StaminaUtil.forceUseStamina(player, amount);
        source.sendSuccess(() -> Component.literal("Used " + amount + " stamina for " + player.getName().getString() + "."), true);
        return 1;
    }
}
