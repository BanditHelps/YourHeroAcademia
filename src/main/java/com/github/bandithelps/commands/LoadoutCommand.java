package com.github.bandithelps.commands;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.capabilities.loadout.AbilityLoadoutAttachments;
import com.github.bandithelps.capabilities.loadout.AbilityLoadoutSyncEvents;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.threetag.palladium.network.OpenScreenPacket;

public final class LoadoutCommand {
    public static final Identifier SCREEN_ID =
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "power/ability_loadout");

    private LoadoutCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext context) {
        builder.then(Commands.literal("loadout")
                .executes(c -> openLoadout(c.getSource()))
                .then(Commands.literal("clear")
                        .executes(c -> clearLoadout(c.getSource()))));
    }

    private static int openLoadout(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AbilityLoadoutSyncEvents.syncNow(player);
        PacketDistributor.sendToPlayer(player, new OpenScreenPacket(SCREEN_ID));
        source.sendSuccess(() -> Component.literal("Opened ability loadout."), false);
        return 1;
    }

    private static int clearLoadout(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AbilityLoadoutAttachments.get(player).clearAll();
        AbilityLoadoutSyncEvents.syncNow(player);
        source.sendSuccess(() -> Component.literal("Cleared ability loadout."), true);
        return 1;
    }
}
