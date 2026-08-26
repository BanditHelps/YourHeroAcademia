package com.github.bandithelps.events;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.creation.CreationUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class CreationPotionEvents {
    private CreationPotionEvents() {
    }

    @SubscribeEvent
    public static void onMobEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getOldEffectInstance() != null || event.getEffectInstance() == null) {
            return;
        }
        Identifier effectId = BuiltInRegistries.MOB_EFFECT.getKey(event.getEffectInstance().getEffect().value());
        CreationUtil.tryProgressPotionFromEffect(player, effectId);
    }
}
