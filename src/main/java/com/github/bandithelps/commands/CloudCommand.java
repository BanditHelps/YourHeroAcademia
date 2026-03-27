package com.github.bandithelps.commands;

import com.github.bandithelps.cloud.CloudMode;
import com.github.bandithelps.cloud.CloudVolume;
import com.github.bandithelps.cloud.CloudVolumeManager;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class CloudCommand {
    private CloudCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext context) {
        builder.then(Commands.literal("cloud")
                .then(Commands.literal("list").executes(c -> listClouds(c.getSource())))
                .then(Commands.literal("clear").executes(c -> clearClouds(c.getSource())))
                .then(Commands.literal("stats").executes(c -> printStats(c.getSource())))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("radius", FloatArgumentType.floatArg(0.5F, 64.0F))
                                .then(Commands.argument("density", FloatArgumentType.floatArg(0.05F, 1.0F))
                                        .executes(c -> spawnCloud(
                                                c.getSource(),
                                                FloatArgumentType.getFloat(c, "radius"),
                                                FloatArgumentType.getFloat(c, "density"),
                                                20 * 60
                                        ))
                                        .then(Commands.argument("lifetimeTicks", IntegerArgumentType.integer(20, 20 * 60 * 30))
                                                .executes(c -> spawnCloud(
                                                        c.getSource(),
                                                        FloatArgumentType.getFloat(c, "radius"),
                                                        FloatArgumentType.getFloat(c, "density"),
                                                        IntegerArgumentType.getInteger(c, "lifetimeTicks")
                                                ))))))
                .then(Commands.literal("disperse")
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.5D, 64.0D))
                                .then(Commands.argument("strength", FloatArgumentType.floatArg(0.05F, 2.0F))
                                        .executes(c -> disperseAroundPlayer(
                                                c.getSource(),
                                                DoubleArgumentType.getDouble(c, "radius"),
                                                FloatArgumentType.getFloat(c, "strength")
                                        ))))));
    }

    private static int listClouds(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        CloudVolumeManager manager = CloudVolumeManager.forLevel(level);
        if (manager.volumes().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active cloud volumes."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("Active cloud volumes: " + manager.volumes().size()), false);
        for (CloudVolume volume : manager.volumes()) {
            source.sendSuccess(() -> Component.literal(
                    "- " + volume.id() + " cells=" + volume.cellCount() + " ttl=" + volume.ttlTicks() + " mode=" + volume.mode().getId()
            ), false);
        }
        return 1;
    }

    private static int clearClouds(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        CloudVolumeManager manager = CloudVolumeManager.forLevel(level);
        int count = manager.volumes().size();
        manager.volumes().stream().map(CloudVolume::id).toList().forEach(manager::removeVolume);
        source.sendSuccess(() -> Component.literal("Cleared " + count + " cloud volume(s)."), true);
        return 1;
    }

    private static int printStats(CommandSourceStack source) {
        CloudVolumeManager manager = CloudVolumeManager.forLevel(source.getLevel());
        source.sendSuccess(() -> Component.literal(
                "Cloud stats: volumes=" + manager.metrics().simulatedVolumeCount()
                        + " changedCells=" + manager.metrics().changedCellCount()
                        + " pendingDeltas=" + manager.metrics().pendingDeltaCellCount()
                        + " packets=" + manager.metrics().sentPacketCount()
                        + " simMs=" + String.format("%.3f", manager.metrics().simulationNanos() / 1_000_000.0D)
        ), false);
        return 1;
    }

    private static int spawnCloud(CommandSourceStack source, float radius, float density, int lifetimeTicks) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command must be used by a player."));
            return 0;
        }
        ServerLevel level = (ServerLevel) player.level();
        Vec3 center = player.position().add(0.0D, 1.0D, 0.0D);
        CloudVolume volume = CloudVolumeManager.forLevel(level).createVolume(center, 1.0D, CloudMode.DIFFUSE, lifetimeTicks);
        volume.addSphereDensity(center, radius, density);
        source.sendSuccess(() -> Component.literal("Spawned test cloud volume " + volume.id() + " with lifetime=" + lifetimeTicks + " ticks"), true);
        return 1;
    }

    private static int disperseAroundPlayer(CommandSourceStack source, double radius, float strength) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command must be used by a player."));
            return 0;
        }

        CloudVolumeManager.forLevel((ServerLevel) player.level()).disperseDome(player.position(), radius, strength);
        source.sendSuccess(() -> Component.literal("Dispersed cloud in dome radius " + radius), true);
        return 1;
    }
}
