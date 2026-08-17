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
    @Inject(method = "extractContent", at = @At("RETURN"))
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
