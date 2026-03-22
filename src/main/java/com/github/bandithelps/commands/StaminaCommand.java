package com.github.bandithelps.commands;

import com.github.bandithelps.network.StaminaDebugOverlayPayload;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StaminaCommand {
    private static final Map<UUID, Boolean> DEBUG_OVERLAY_STATE = new ConcurrentHashMap<>();

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
                                        )))))
                .then(Commands.literal("debug")
                        .executes(c -> toggleDebugOverlay(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.literal("toggle")
                                .executes(c -> toggleDebugOverlay(c.getSource(), c.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> toggleDebugOverlay(
                                                c.getSource(),
                                                EntityArgument.getPlayer(c, "player")
                                        ))))
                        .then(Commands.literal("on")
                                .executes(c -> setDebugOverlay(c.getSource(), c.getSource().getPlayerOrException(), true))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> setDebugOverlay(
                                                c.getSource(),
                                                EntityArgument.getPlayer(c, "player"),
                                                true
                                        ))))
                        .then(Commands.literal("off")
                                .executes(c -> setDebugOverlay(c.getSource(), c.getSource().getPlayerOrException(), false))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> setDebugOverlay(
                                                c.getSource(),
                                                EntityArgument.getPlayer(c, "player"),
                                                false
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

    private static int toggleDebugOverlay(CommandSourceStack source, ServerPlayer player) {
        boolean enabled = !DEBUG_OVERLAY_STATE.getOrDefault(player.getUUID(), false);
        return setDebugOverlay(source, player, enabled);
    }

    private static int setDebugOverlay(CommandSourceStack source, ServerPlayer player, boolean enabled) {
        DEBUG_OVERLAY_STATE.put(player.getUUID(), enabled);
        PacketDistributor.sendToPlayer(player, new StaminaDebugOverlayPayload(enabled));
        source.sendSuccess(() -> Component.literal(
                "Stamina debug overlay " + (enabled ? "enabled" : "disabled") + " for " + player.getName().getString() + "."
        ), true);
        return 1;
    }
}
