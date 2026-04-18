package com.github.bandithelps.mixin;

import com.github.bandithelps.gui.actions.YhaDialogActions;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gotta tell the server about the custom button. Taken directly from Palladium's mixin
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {

    @Shadow
    protected abstract GameProfile playerProfile();

    @Shadow
    @Final
    protected MinecraftServer server;

    @Inject(
            method = "handleCustomClickAction",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;handleCustomClickAction(Lnet/minecraft/resources/Identifier;Ljava/util/Optional;)V"),
            cancellable = true
    )
    private void handleCustomClickAction(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        var id = packet.id();
        var tag = packet.payload().orElse(new CompoundTag());
        var player = this.server.getPlayerList().getPlayer(this.playerProfile().id());

        if (player != null && YhaDialogActions.handleCustom(player, id, tag)) {
            ci.cancel();
        }
    }

}
