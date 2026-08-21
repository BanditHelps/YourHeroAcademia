package com.github.bandithelps.capabilities.body;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Maps vanilla combat damage onto the body system. Currently only fall damage hits legs/feet.
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class BodyCombatEvents {
    public static final float FALL_TO_BODY = 1.0f;

    private static final BodyPart[] FALL_PARTS = {
            BodyPart.LEFT_LEG,
            BodyPart.RIGHT_LEG,
            BodyPart.LEFT_FOOT,
            BodyPart.RIGHT_FOOT
    };

    private BodyCombatEvents() {
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.isAlive() || player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
            return;
        }
        if (!event.getSource().is(DamageTypeTags.IS_FALL)) {
            return;
        }

        float bodyDamage = event.getOriginalDamage() * FALL_TO_BODY;
        if (bodyDamage <= 0.0f) {
            return;
        }

        for (BodyPart part : FALL_PARTS) {
            BodyDamageHelper.damage(player, part, bodyDamage);
        }
        BodySyncEvents.syncNow(player);
    }
}
