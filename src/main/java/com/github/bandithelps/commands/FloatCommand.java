package com.github.bandithelps.commands;

import com.github.bandithelps.abilities.floatquirk.FloatPhysics;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /yha float debug} toggles action-bar + log dumps of Float velocity/coast.
 */
public final class FloatCommand {

    private FloatCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext context) {
        builder.then(Commands.literal("float")
                .then(Commands.literal("debug")
                        .executes(c -> toggleDebug(c.getSource(), c.getSource().getPlayerOrException()))));
    }

    private static int toggleDebug(CommandSourceStack source, ServerPlayer player) {
        boolean enabled = FloatPhysics.toggleDebug(player.getUUID());
        source.sendSuccess(() -> Component.literal(enabled
                ? "Float debug ON. Sprint, toggle Float, and watch the action bar."
                : "Float debug OFF."), false);
        return enabled ? 1 : 0;
    }
}
