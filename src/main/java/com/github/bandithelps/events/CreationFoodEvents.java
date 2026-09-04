package com.github.bandithelps.events;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.creation.CreationUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class CreationFoodEvents {
    private CreationFoodEvents() {
    }

    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!CreationUtil.hasCreation(player)) {
            return;
        }
        ItemStack stack = event.getItem();
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) {
            return;
        }
        float gained = CreationUtil.lipidsFromFood(player, food.saturation());
        if (gained <= 0.0f) {
            return;
        }
        CreationUtil.addLipids(player, gained);
    }
}
