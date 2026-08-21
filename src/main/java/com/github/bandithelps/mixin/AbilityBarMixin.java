package com.github.bandithelps.mixin;

import com.github.bandithelps.client.loadout.ClientAbilityLoadoutState;
import com.github.bandithelps.capabilities.loadout.AbilityLoadoutData;
import com.github.bandithelps.utils.loadout.AbilityLoadoutUtil;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.threetag.palladium.client.gui.screen.abilitybar.AbilityBar;
import net.threetag.palladium.power.PowerInstance;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityReference;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbilityBar.class)
public abstract class AbilityBarMixin {
    @Shadow
    private List<AbilityBar.AbilityList> lists;

    @Shadow
    private int selectedList;

    @Shadow
    private AbilityBar.AbilityList currentList;

    @Inject(method = "populate", at = @At("RETURN"))
    private void yha$prependCustomLoadout(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || this.lists == null) {
            return;
        }
        AbilityLoadoutData loadout = ClientAbilityLoadoutState.get();
        if (!loadout.hasAnyAssigned()) {
            return;
        }

        PowerInstance owner = null;
        AbilityBar.AbilityList loadoutList = null;
        for (int slot = 0; slot < AbilityLoadoutData.SLOT_COUNT; slot++) {
            AbilityReference reference = loadout.getSlot(slot);
            AbilityInstance<?> assigned = AbilityLoadoutUtil.resolveInstance(player, reference);
            if (assigned == null || !assigned.isUnlocked() || !AbilityLoadoutUtil.isBarAbility(assigned)) {
                continue;
            }
            if (owner == null) {
                owner = assigned.getPowerInstance();
                loadoutList = new AbilityBar.AbilityList(owner);
            }
            List<AbilityInstance<?>> modes = AbilityLoadoutUtil.collectModes(player, assigned);
            if (modes.isEmpty()) {
                loadoutList.addAbility(slot, assigned);
                continue;
            }
            for (AbilityInstance<?> mode : modes) {
                loadoutList.addAbility(slot, mode);
            }
        }
        if (loadoutList == null || loadoutList.isEmpty() || !loadoutList.hasUnlocked()) {
            return;
        }

        this.lists.add(0, loadoutList);
        loadoutList.build(this.lists.size() > 1);
        if (this.selectedList < 0) {
            this.selectedList = 0;
        }
        if (this.selectedList >= this.lists.size()) {
            this.selectedList = this.lists.size() - 1;
        }
        this.currentList = this.lists.get(this.selectedList);
    }
}
