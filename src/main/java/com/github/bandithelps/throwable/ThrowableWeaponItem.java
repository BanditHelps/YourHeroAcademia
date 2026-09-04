package com.github.bandithelps.throwable;

import com.github.bandithelps.entities.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * Shared charge-and-throw item. Hold right-click to pull back (trident throw pose);
 * release throws a {@link ThrownWeaponEntity} whose behavior comes from {@link ThrowableWeaponSpec}.
 */
public class ThrowableWeaponItem extends Item {
    private final ThrowableWeaponSpec spec;

    public ThrowableWeaponItem(Properties properties, ThrowableWeaponSpec spec) {
        super(properties);
        this.spec = spec == null ? ThrowableWeaponSpec.DEFAULT : spec;
    }

    public ThrowableWeaponSpec spec() {
        return spec;
    }

    protected boolean canThrow(ItemStack stack, LivingEntity user) {
        return !stack.isEmpty();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!canThrow(stack, player)) {
            return InteractionResult.FAIL;
        }
        if (spec.isInstantThrow()) {
            throwWeapon(level, player, stack, spec.maxThrowSpeed());
            return InteractionResult.SUCCESS;
        }
        player.startUsingItem(usedHand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return spec.isInstantThrow() ? ItemUseAnimation.NONE : ItemUseAnimation.TRIDENT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return spec.isInstantThrow() ? 0 : Item.APPROXIMATELY_INFINITE_USE_DURATION;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        if (spec.isInstantThrow() || !canThrow(stack, entity)) {
            return false;
        }
        int usedTicks = getUseDuration(stack, entity) - remainingTime;
        if (usedTicks < spec.minChargeTicks()) {
            return false;
        }
        throwWeapon(level, entity, stack, spec.throwSpeedForCharge(usedTicks));
        return true;
    }

    private void throwWeapon(Level level, LivingEntity entity, ItemStack stack, float speed) {
        if (level instanceof ServerLevel serverLevel) {
            ItemStack thrownStack = stack.copyWithCount(1);
            ThrownWeaponEntity projectile = new ThrownWeaponEntity(serverLevel, entity, thrownStack);
            projectile.shootFromRotation(entity, entity.getXRot(), entity.getYRot(), 0.0f, speed, 1.0f);
            serverLevel.addFreshEntity(projectile);
            level.playSound(
                    null,
                    entity.getX(),
                    entity.getY(),
                    entity.getZ(),
                    SoundEvents.SNOWBALL_THROW,
                    SoundSource.PLAYERS,
                    0.5f,
                    0.7f + (entity.getRandom().nextFloat() * 0.2f)
            );
        }

        if (entity instanceof Player player) {
            if (spec.cooldownTicks() > 0) {
                player.getCooldowns().addCooldown(stack, spec.cooldownTicks());
            }
        }
        stack.consume(1, entity);
    }
}
