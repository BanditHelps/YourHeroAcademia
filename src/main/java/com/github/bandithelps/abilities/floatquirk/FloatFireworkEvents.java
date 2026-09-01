package com.github.bandithelps.abilities.floatquirk;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Lets a floating player use a firework rocket as a weak look-direction boost.
 * Spawns the same attached rocket entity elytra uses so the trail matches.
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class FloatFireworkEvents {

    private FloatFireworkEvents() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!FloatAbility.isActive(player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof FireworkRocketItem)) {
            return;
        }

        if (player instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel serverLevel) {
            Vec3 look = serverPlayer.getLookAngle();
            FloatPhysics.applyFireworkImpulse(serverPlayer, look.scale(FloatPhysics.FIREWORK_IMPULSE));

            ItemStack rocketStack = stack.copyWithCount(1);
            FireworkRocketEntity rocket = new FireworkRocketEntity(serverLevel, rocketStack, serverPlayer);
            Vec3 hand = attachedRocketPos(serverPlayer);
            rocket.setPos(hand.x, hand.y, hand.z);
            serverLevel.addFreshEntity(rocket);

            serverLevel.playSound(
                    null,
                    serverPlayer.getX(),
                    serverPlayer.getY(),
                    serverPlayer.getZ(),
                    SoundEvents.FIREWORK_ROCKET_LAUNCH,
                    SoundSource.PLAYERS,
                    0.55f,
                    1.15f);

            serverPlayer.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            if (!serverPlayer.getAbilities().instabuild) {
                stack.shrink(1);
            }

            serverPlayer.swing(event.getHand() == InteractionHand.MAIN_HAND
                    ? InteractionHand.MAIN_HAND
                    : InteractionHand.OFF_HAND,
                    true);
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Matches the body-bone pivot in the player animation (12px / 16). */
    private static final double WAIST_Y = 0.75d;
    private static final double HAND_UP = -0.1d;
    private static final double HAND_FORWARD = -0.1d;
    private static final double HAND_SIDE = 0.32d;
    /** Same degrees as {@code float.hovering} body X: {@code 0.5 + lean * 80}. */
    private static final double LEAN_PITCH_BASE = 0.5d;
    private static final double LEAN_PITCH_SCALE = 10.0d;

    /**
     * Main-hand offset from the animation waist, pitched by Float lean so the
     * trail follows the body when they go horizontal. Vanilla attached rockets
     * sit on {@code entity.position()} (the feet).
     */
    public static Vec3 attachedRocketPos(LivingEntity entity) {
        float yawRad = entity.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yawRad), 0.0d, Mth.cos(yawRad));
        Vec3 right = new Vec3(-Mth.cos(yawRad), 0.0d, -Mth.sin(yawRad));
        Vec3 up = new Vec3(0.0d, 1.0d, 0.0d);
        double side = HAND_SIDE;
        if (entity instanceof Player player && player.getMainArm() == HumanoidArm.LEFT) {
            side = -side;
        }

        float pitchRad = (float) Math.toRadians(LEAN_PITCH_BASE + FloatAnimPose.getLean(entity) * LEAN_PITCH_SCALE);
        float cos = Mth.cos(pitchRad);
        float sin = Mth.sin(pitchRad);
        Vec3 pitchedUp = up.scale(cos).add(forward.scale(sin));
        Vec3 pitchedForward = forward.scale(cos).subtract(up.scale(sin));

        return entity.position()
                .add(0.0d, WAIST_Y, 0.0d)
                .add(right.scale(side))
                .add(pitchedUp.scale(HAND_UP))
                .add(pitchedForward.scale(HAND_FORWARD));
    }
}
