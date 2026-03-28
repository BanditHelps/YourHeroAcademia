package com.github.bandithelps.commands;

import com.github.bandithelps.utils.blockdisplays.BlockDisplaySummoner;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.Arrays;

public class BdCommand {

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext context) {
        builder.then(Commands.literal("bd")
                .then(Commands.literal("summon")
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.1))
                                .then(Commands.argument("num", DoubleArgumentType.doubleArg(1))
                                        .executes(c -> summonBd(
                                                c.getSource(),
                                                c.getSource().getPlayerOrException(),
                                                DoubleArgumentType.getDouble(c, "radius"),
                                                DoubleArgumentType.getDouble(c, "num")
                                        ))
                                ))
                ));

    }

    private static int summonBd(CommandSourceStack source, ServerPlayer player, double radius, double num) throws CommandSyntaxException {
        BlockState[] palette = {
            Blocks.DIRT.defaultBlockState(),
            Blocks.MANGROVE_ROOTS.defaultBlockState(),
            Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState(),
            Blocks.BLACK_STAINED_GLASS.defaultBlockState()
        };

        BlockDisplaySummoner.summonShockwave(player.level(), player, (float)radius, 40, num, Arrays.asList(palette), new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), new Vector3f(0.6f, 0.6f, 0.6f), 40, true, true);
        return 1;
    }
}
