package com.github.bandithelps.client.loadout;

import com.github.bandithelps.capabilities.loadout.AbilityLoadoutData;
import com.github.bandithelps.network.AbilityLoadoutModeSelectPayload;
import com.github.bandithelps.utils.loadout.AbilityLoadoutUtil;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.threetag.palladium.client.gui.screen.abilitybar.AbilityBar;
import net.threetag.palladium.power.ability.AbilityInstance;

public final class AbilityModeSelectClient {
    private AbilityModeSelectClient() {
    }

    public static void cycleSlot(int slot) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || slot < 0 || slot >= AbilityLoadoutData.SLOT_COUNT) {
            return;
        }
        AbilityBar.AbilityList currentList = AbilityBar.INSTANCE.getCurrentList();
        if (currentList == null) {
            return;
        }
        AbilityInstance<?> current = currentList.getAbility(slot);
        if (current == null) {
            return;
        }
        List<AbilityInstance<?>> modes = AbilityLoadoutUtil.collectModes(player, current);
        if (modes.size() < 2) {
            return;
        }
        int index = indexOf(modes, current);
        AbilityInstance<?> next = modes.get((index + 1) % modes.size());
        ClientPacketDistributor.sendToServer(new AbilityLoadoutModeSelectPayload(
                AbilityLoadoutData.encodeReference(next.getReference())
        ));
        ClientAbilityLoadoutState.get().setSelectedMode(
                next.getPowerInstance().getPowerId(),
                AbilityLoadoutUtil.getListIndex(player, next),
                next.getAbility().getKey()
        );
    }

    private static int indexOf(List<AbilityInstance<?>> modes, AbilityInstance<?> current) {
        String key = current.getAbility().getKey();
        for (int i = 0; i < modes.size(); i++) {
            if (key.equals(modes.get(i).getAbility().getKey())) {
                return i;
            }
        }
        return 0;
    }
}
