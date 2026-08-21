package com.github.bandithelps.utils.blackwhip;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;

/**
 * Strength-roll disarm: rip a held item into the owner's inventory.
 */
public final class BlackwhipChainDisarmLogic {

    public static final double BLOCKING_DEFENSE_MULT = 1.75;
    public static final double CHANCE_BASE = 0.5;
    public static final double CHANCE_PER_STRENGTH = 0.12;
    public static final float CHANCE_MIN = 0.08f;
    public static final float CHANCE_MAX = 0.92f;

    private BlackwhipChainDisarmLogic() {
    }

    public static double disarmDefensePower(LivingEntity target) {
        double defense = BlackwhipChainLeadPhysics.contestPower(target);
        if (target.isBlocking()) {
            defense *= BLOCKING_DEFENSE_MULT;
        }
        return defense;
    }

    public static float disarmChance(LivingEntity owner, LivingEntity target) {
        double ownerPow = BlackwhipChainLeadPhysics.readStrength(owner);
        double defensePow = disarmDefensePower(target);
        return Mth.clamp((float) (CHANCE_BASE + CHANCE_PER_STRENGTH * (ownerPow - defensePow)), CHANCE_MIN, CHANCE_MAX);
    }

    public static boolean rollDisarm(LivingEntity owner, LivingEntity target, RandomSource random) {
        return random.nextFloat() < disarmChance(owner, target);
    }

    public static boolean isShield(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof ShieldItem || stack.is(Items.SHIELD));
    }

    /**
     * Prefer shield (blocking or offhand), then mainhand, then offhand.
     *
     * @return hand to rip, or {@code null} if both hands empty
     */
    public static InteractionHand selectDisarmHand(LivingEntity target) {
        ItemStack main = target.getMainHandItem();
        ItemStack off = target.getOffhandItem();
        boolean prioritizeShield = target.isBlocking() || isShield(off);
        if (prioritizeShield) {
            if (isShield(off)) {
                return InteractionHand.OFF_HAND;
            }
            if (isShield(main)) {
                return InteractionHand.MAIN_HAND;
            }
            if (target.isBlocking() && target.isUsingItem()) {
                InteractionHand used = target.getUsedItemHand();
                if (!target.getItemInHand(used).isEmpty()) {
                    return used;
                }
            }
        }
        if (!main.isEmpty()) {
            return InteractionHand.MAIN_HAND;
        }
        if (!off.isEmpty()) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    /**
     * Roll and, on success, rip the selected hand item (caller owns the stack for tip visuals).
     *
     * @return stolen stack, or empty if the roll failed / nothing to take
     */
    public static ItemStack tryDisarm(ServerPlayer owner, LivingEntity target) {
        if (owner == null || target == null || !target.isAlive()) {
            return ItemStack.EMPTY;
        }
        InteractionHand hand = selectDisarmHand(target);
        if (hand == null) {
            return ItemStack.EMPTY;
        }
        if (!rollDisarm(owner, target, owner.getRandom())) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = target.getItemInHand(hand);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stolen = stack.copy();
        target.setItemInHand(hand, ItemStack.EMPTY);
        return stolen;
    }
}
