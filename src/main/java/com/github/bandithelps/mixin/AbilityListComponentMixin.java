package com.github.bandithelps.mixin;

import com.github.bandithelps.client.loadout.AbilityModeOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.threetag.palladium.client.gui.screen.abilitybar.AbilityBarAlignment;
import net.threetag.palladium.client.gui.screen.abilitybar.AbilityListComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbilityListComponent.class)
public abstract class AbilityListComponentMixin {
    @Inject(method = "extractContent", at = @At("HEAD"))
    private void yha$beginModeSlideout(
            Minecraft minecraft,
            GuiGraphicsExtractor gui,
            int x,
            int y,
            AbilityBarAlignment alignment,
            CallbackInfo ci
    ) {
        AbilityModeOverlay.beginPass();
    }

    @Inject(
            method = "extractContent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/threetag/palladium/client/gui/screen/abilitybar/AbilityListComponent;renderAbility(Lnet/minecraft/client/Minecraft;Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/threetag/palladium/client/gui/screen/abilitybar/AbilityBarAlignment;Lnet/threetag/palladium/power/ability/AbilityInstance;I)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void yha$renderModeSlideout(
            Minecraft minecraft,
            GuiGraphicsExtractor gui,
            int x,
            int y,
            AbilityBarAlignment alignment,
            CallbackInfo ci
    ) {
        AbilityModeOverlay.render((AbilityListComponent) (Object) this, minecraft, gui, x, y, alignment);
    }
}
