package com.github.bandithelps.throwable;

import com.github.bandithelps.entities.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shared projectile for every {@link ThrowableWeaponItem}. Behavior (fuse, bounce, detonation)
 * is read from the thrown item's {@link ThrowableWeaponSpec}.
 */
public class ThrownWeaponEntity extends ThrowableItemProjectile {
    private static final int MAX_LIFE_TICKS = 400;
    private static final int COLLIDE_GRACE_TICKS = 2;

    private boolean armed;
    private int armedTicks;
    private int lifeTicks;
    private int collideGraceTicks;

    public ThrownWeaponEntity(EntityType<? extends ThrownWeaponEntity> entityType, Level level) {
        super(entityType, level);
    }

    public ThrownWeaponEntity(Level level, LivingEntity thrower, ItemStack stack) {
        super(ModEntities.THROWN_WEAPON.get(), thrower, level, stack);
        this.armIfFromThrow();
    }

    public ThrowableWeaponSpec spec() {
        ItemStack stack = this.getItem();
        if (stack.getItem() instanceof ThrowableWeaponItem item) {
            return item.spec();
        }
        return ThrowableWeaponSpec.DEFAULT;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.AIR;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.collideGraceTicks > 0) {
            this.collideGraceTicks--;
        }

        this.lifeTicks++;
        if (this.lifeTicks >= MAX_LIFE_TICKS) {
            if (!this.level().isClientSide()) {
                this.discard();
            }
            return;
        }

        if (this.level().isClientSide() || !this.armed) {
            return;
        }

        this.armedTicks++;
        if (this.armedTicks >= this.spec().fuseTicks()) {
            this.detonate();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (this.level().isClientSide() || this.collideGraceTicks > 0) {
            return;
        }

        ThrowableWeaponSpec spec = this.spec();
        if (spec.stickOnImpact()) {
            super.onHitBlock(result);
            this.stick();
            this.armFromImpact();
            return;
        }

        if (spec.bounce()) {
            this.bounceOff(Vec3.atLowerCornerOf(result.getDirection().getUnitVec3i()));
            this.armFromImpact();
            return;
        }

        super.onHitBlock(result);
        this.armFromImpact();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (this.level().isClientSide() || this.collideGraceTicks > 0) {
            return;
        }

        ThrowableWeaponSpec spec = this.spec();
        Entity hit = result.getEntity();
        if (spec.stickOnImpact()) {
            super.onHitEntity(result);
            this.stick();
            this.armFromImpact();
            return;
        }

        if (spec.bounce()) {
            Vec3 away = this.position().subtract(hit.position());
            if (away.lengthSqr() < 1.0E-6) {
                away = this.getDeltaMovement().scale(-1.0);
            }
            this.bounceOff(away.normalize());
            this.armFromImpact();
            return;
        }

        super.onHitEntity(result);
        this.armFromImpact();
    }

    private void armIfFromThrow() {
        if (this.spec().fuseMode() == FuseMode.FROM_THROW) {
            this.arm();
        }
    }

    private void armFromImpact() {
        if (this.spec().fuseMode() == FuseMode.FROM_IMPACT) {
            this.arm();
        }
    }

    private void arm() {
        if (this.armed || this.level().isClientSide()) {
            return;
        }
        this.armed = true;
        this.armedTicks = 0;
        if (this.spec().fuseTicks() <= 0) {
            this.detonate();
        }
    }

    private void stick() {
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.level().playSound(null, this.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.35f, 1.5f);
    }

    private void bounceOff(Vec3 normal) {
        Vec3 n = normal.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : normal.normalize();
        Vec3 vel = this.getDeltaMovement();
        Vec3 reflected = vel.subtract(n.scale(2.0 * vel.dot(n))).scale(this.spec().bounceDamping());
        this.setDeltaMovement(reflected);
        this.setPos(this.position().add(n.scale(0.05)));
        this.collideGraceTicks = COLLIDE_GRACE_TICKS;
        this.level().playSound(null, this.blockPosition(), SoundEvents.SLIME_BLOCK_HIT, SoundSource.PLAYERS, 0.25f, 1.4f);
    }

    private void detonate() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            this.discard();
            return;
        }
        this.spec().detonation().detonate(this, serverLevel, this.spec());
        if (!this.isRemoved()) {
            this.discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Armed", this.armed);
        output.putInt("ArmedTicks", this.armedTicks);
        output.putInt("LifeTicks", this.lifeTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.armed = input.getBooleanOr("Armed", false);
        this.armedTicks = input.getIntOr("ArmedTicks", 0);
        this.lifeTicks = input.getIntOr("LifeTicks", 0);
        if (!this.armed) {
            this.armIfFromThrow();
        }
    }
}
