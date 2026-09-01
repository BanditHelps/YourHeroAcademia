package com.github.bandithelps.abilities.floatquirk;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Lets a floating player use a firework rocket as a weak look-direction boost.
 * Much slower than elytra fireworks.
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class FloatFireworkEvents {

    private FloatFireworkEvents() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!FloatAbility.isActive(player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof FireworkRocketItem)) {
            return;
        }

        Vec3 look = player.getLookAngle();
        FloatPhysics.applyFireworkImpulse(player, look.scale(FloatPhysics.FIREWORK_IMPULSE));

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.FIREWORK_ROCKET_LAUNCH,
                    SoundSource.PLAYERS,
                    0.55f,
                    1.15f);
        }

        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        player.swing(event.getHand() == InteractionHand.MAIN_HAND
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND,
                true);
    }
}
