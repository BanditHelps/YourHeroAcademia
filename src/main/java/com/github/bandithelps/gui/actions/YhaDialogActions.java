package com.github.bandithelps.gui.actions;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.capabilities.body.*;
import com.github.bandithelps.utils.stamina.StaminaUtil;
import com.github.bandithelps.values.StaminaConstants;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;


/**
 * Used for the button presses inside the powers menu/gui
 */
public class YhaDialogActions {

    public static final DeferredRegister<MapCodec<? extends Action>> ACTIONS;

    public static final DeferredHolder<MapCodec<? extends Action>, MapCodec<UpgradeStatAction>> UPGRADE_STAT_ACTION;

    public static boolean handleCustom(ServerPlayer player, Identifier id, Tag tag) {
        if (tag instanceof StringTag stringTag) {
            String key = stringTag.asString().orElse("");
            int keyIndex = validateKey(key);
            if (id.equals(UPGRADE_STAT_ACTION.getId()) && keyIndex != -1) {
                // Need to subtract the upgrade points, and increase the current value
                IBodyData body = BodyAttachments.get(player);
                float current = body.getCustomFloat(player, BodyPart.CHEST, key, 0);
                float max = body.getCustomFloat(player, BodyPart.CHEST, BodyData.UPGRADE_MAX_KEYS[keyIndex], 5);


                if (current < max) {
                    body.setCustomFloat(player, BodyPart.CHEST, key, current + 1);
                    BodySyncEvents.syncNow(player);
                    StaminaUtil.spendUpgradePoints(player, StaminaConstants.PHYSICAL_STAT_UPGRADE_COST);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Ensures that the passed in key actually corresponds to a statistic
     * @param key
     * @return
     */
    private static int validateKey(String key) {
        for (int i = 0; i < BodyData.UPGRADE_CURRENT_KEYS.length; i++) {
            if (BodyData.UPGRADE_CURRENT_KEYS[i].equals(key)) {
                return i;
            }
        }

        return -1;
    }



    static {
        ACTIONS = DeferredRegister.create(Registries.DIALOG_ACTION_TYPE, YourHeroAcademia.MODID);
        UPGRADE_STAT_ACTION = ACTIONS.register("upgrade_stat", () -> UpgradeStatAction.CODEC);
    }


}
