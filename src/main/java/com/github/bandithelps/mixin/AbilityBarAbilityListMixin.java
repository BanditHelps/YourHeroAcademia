package com.github.bandithelps.mixin;

import com.github.bandithelps.client.loadout.ClientAbilityLoadoutState;
import com.github.bandithelps.utils.loadout.AbilityLoadoutUtil;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.threetag.palladium.client.gui.screen.abilitybar.AbilityBar;
import net.threetag.palladium.power.ability.AbilityInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import io.netty.util.collection.IntObjectHashMap;

@Mixin(AbilityBar.AbilityList.class)
public abstract class AbilityBarAbilityListMixin {
    @Shadow
    @Final
    private IntObjectHashMap<List<AbilityInstance<?>>> abilities;

    @Inject(method = "getDisplayedAbilities", at = @At("RETURN"))
    private void yha$preferSelectedModes(CallbackInfoReturnable<AbilityInstance<?>[]> cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || this.abilities == null) {
            return;
        }
        AbilityInstance<?>[] displayed = cir.getReturnValue();
        if (displayed == null) {
            return;
        }
        for (int slot = 0; slot < displayed.length; slot++) {
            List<AbilityInstance<?>> candidates = this.abilities.get(slot);
            if (candidates == null || candidates.size() < 2) {
                continue;
            }
            displayed[slot] = AbilityLoadoutUtil.pickSelectedMode(
                    player,
                    candidates,
                    displayed[slot],
                    ClientAbilityLoadoutState.get()
            );
        }
    }
}
