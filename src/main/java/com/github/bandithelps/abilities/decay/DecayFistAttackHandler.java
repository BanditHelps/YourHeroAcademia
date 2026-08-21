package com.github.bandithelps.abilities.decay;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.utils.decay.DecayHelper;
import com.github.bandithelps.utils.quirk.QuirkFactorUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * Bridges the {@link DecayFistAbility} toggle into vanilla melee combat: when a player who has the
 * decaying touch active strikes an entity with an empty main hand, the target receives the decay effect.
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public class DecayFistAttackHandler {

    private static final int EFFECT_DURATION = 140; // 7 seconds
    private static final int BASE_AMPLIFIER = 1;

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!DecayFistAbility.isActive(player.getUUID())) {
            return;
        }
        // Only an open (empty) main hand channels the decay.
        if (!player.getMainHandItem().isEmpty()) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity target) || !target.isAlive()) {
            return;
        }

        double quirkFactor = QuirkFactorUtil.getQuirkFactor(player);
        DecayHelper.applyDecayEffect(serverLevel, target, quirkFactor, BASE_AMPLIFIER, EFFECT_DURATION);
        DecayHelper.addInstability(player, 3.0f);

        serverLevel.playSound(null, target.blockPosition(), SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.6f, 1.2f);
    }
}
