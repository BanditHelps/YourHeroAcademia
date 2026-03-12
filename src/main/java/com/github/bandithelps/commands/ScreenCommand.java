package com.github.bandithelps.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.github.bandithelps.network.OpenGeneExperimentScreenPayload;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class ScreenCommand {

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext context) {
        builder.then(Commands.literal("screen")
                .executes(c -> openScreen(c.getSource())));
    }

    private static int openScreen(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PacketDistributor.sendToPlayer(player, OpenGeneExperimentScreenPayload.INSTANCE);
        source.sendSuccess(() -> Component.literal("Opened YHA test screen."), false);
        return 1;
    }
}
