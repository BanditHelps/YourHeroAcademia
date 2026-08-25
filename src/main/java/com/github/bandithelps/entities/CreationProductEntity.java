package com.github.bandithelps.entities;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class CreationProductEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> DATA_ITEM =
            SynchedEntityData.defineId(CreationProductEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> DATA_GROW_TICKS =
            SynchedEntityData.defineId(CreationProductEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_AGE =
            SynchedEntityData.defineId(CreationProductEntity.class, EntityDataSerializers.INT);

    private int growTicks = 16;

    public CreationProductEntity(EntityType<? extends CreationProductEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public CreationProductEntity(Level level, ItemStack stack, Vec3 origin, int growTicks) {
        this(ModEntities.CREATION_PRODUCT.get(), level);
        this.growTicks = Math.max(1, growTicks);
        this.setItem(stack);
        this.getEntityData().set(DATA_GROW_TICKS, this.growTicks);
        this.setPos(origin.x, origin.y, origin.z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM, ItemStack.EMPTY);
        builder.define(DATA_GROW_TICKS, 16);
        builder.define(DATA_AGE, 0);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setItem(input.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        this.growTicks = Math.max(1, input.getIntOr("GrowTicks", 16));
        this.getEntityData().set(DATA_GROW_TICKS, this.growTicks);
        this.getEntityData().set(DATA_AGE, input.getIntOr("Age", 0));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (!getItem().isEmpty()) {
            output.store("Item", ItemStack.CODEC, getItem());
        }
        output.putInt("GrowTicks", this.growTicks);
        output.putInt("Age", getAge());
    }

    @Override
    public void tick() {
        super.tick();
        int age = getAge() + 1;
        this.getEntityData().set(DATA_AGE, age);
        this.growTicks = Math.max(1, this.getEntityData().get(DATA_GROW_TICKS));

        if (this.level().isClientSide()) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (age % 2 == 0 && !getItem().isEmpty()) {
            serverLevel.sendParticles(
                    new ItemParticleOption(ParticleTypes.ITEM, getItem().getItem()),
                    this.getX(), this.getY(), this.getZ(),
                    1, 0.05, 0.05, 0.05, 0.01
            );
        }
        if (age >= this.growTicks) {
            dropAndDiscard(serverLevel);
        }
    }

    private void dropAndDiscard(ServerLevel level) {
        ItemStack stack = getItem().copy();
        if (!stack.isEmpty()) {
            double yaw = this.random.nextDouble() * Math.PI * 2.0;
            ItemEntity dropped = new ItemEntity(level, this.getX(), this.getY(), this.getZ(), stack);
            dropped.setDeltaMovement(Math.cos(yaw) * 0.12, 0.18, Math.sin(yaw) * 0.12);
            dropped.setDefaultPickUpDelay();
            level.addFreshEntity(dropped);
            level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY(), this.getZ(), 8, 0.12, 0.08, 0.12, 0.02);
            level.playSound(null, this.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 0.6f);
        }
        this.discard();
    }

    public ItemStack getItem() {
        return this.getEntityData().get(DATA_ITEM);
    }

    public void setItem(ItemStack stack) {
        this.getEntityData().set(DATA_ITEM, stack == null ? ItemStack.EMPTY : stack.copy());
    }

    public int getAge() {
        return this.getEntityData().get(DATA_AGE);
    }

    public float growScale(float partialTick) {
        int duration = Math.max(1, this.getEntityData().get(DATA_GROW_TICKS));
        float progress = Mth.clamp((getAge() + partialTick) / duration, 0.0f, 1.0f);
        return 0.15f + progress * 0.85f;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
