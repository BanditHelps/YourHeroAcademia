package com.github.bandithelps.mixin;

import com.github.bandithelps.abilities.floatquirk.FloatAbility;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Float is already a hover. Opening elytra from that state would swap into
 * glide; activating Float while already gliding is allowed and keeps the pose.
 */
@Mixin(Player.class)
public abstract class PlayerFallFlyingMixin {

    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    private void yha$blockElytraWhileFloating(CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        if (FloatAbility.isActive(player)) {
            cir.setReturnValue(false);
        }
    }
}
