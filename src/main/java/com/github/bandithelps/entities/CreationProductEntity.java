package com.github.bandithelps.entities;

import com.github.bandithelps.creation.CreationGrowthAnchors;
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
import net.minecraft.world.entity.LivingEntity;
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
    private static final EntityDataAccessor<Integer> DATA_OWNER_ID =
            SynchedEntityData.defineId(CreationProductEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACH_SLOT =
            SynchedEntityData.defineId(CreationProductEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_JITTER_SIDE =
            SynchedEntityData.defineId(CreationProductEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_JITTER_UP =
            SynchedEntityData.defineId(CreationProductEntity.class, EntityDataSerializers.FLOAT);

    private int growTicks = 16;

    public CreationProductEntity(EntityType<? extends CreationProductEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public CreationProductEntity(
            Level level,
            LivingEntity owner,
            ItemStack stack,
            int attachSlot,
            float jitterSide,
            float jitterUp,
            int growTicks
    ) {
        this(ModEntities.CREATION_PRODUCT.get(), level);
        this.growTicks = Math.max(1, growTicks);
        this.setItem(stack);
        this.getEntityData().set(DATA_GROW_TICKS, this.growTicks);
        this.setOwner(owner);
        this.setAttachSlot(attachSlot);
        this.setJitter(jitterSide, jitterUp);
        Vec3 origin = CreationGrowthAnchors.visualPos(owner, attachSlot, jitterSide, jitterUp, 1.0f);
        this.setPos(origin.x, origin.y, origin.z);
        if (owner != null) {
            this.setDeltaMovement(owner.getDeltaMovement());
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM, ItemStack.EMPTY);
        builder.define(DATA_GROW_TICKS, 16);
        builder.define(DATA_AGE, 0);
        builder.define(DATA_OWNER_ID, -1);
        builder.define(DATA_ATTACH_SLOT, CreationGrowthAnchors.SLOT_BACK);
        builder.define(DATA_JITTER_SIDE, 0.0f);
        builder.define(DATA_JITTER_UP, 0.0f);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setItem(input.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        this.growTicks = Math.max(1, input.getIntOr("GrowTicks", 16));
        this.getEntityData().set(DATA_GROW_TICKS, this.growTicks);
        this.getEntityData().set(DATA_AGE, input.getIntOr("Age", 0));
        this.getEntityData().set(DATA_OWNER_ID, input.getIntOr("OwnerId", -1));
        this.getEntityData().set(DATA_ATTACH_SLOT, input.getIntOr("AttachSlot", CreationGrowthAnchors.SLOT_BACK));
        this.getEntityData().set(DATA_JITTER_SIDE, input.getFloatOr("JitterSide", 0.0f));
        this.getEntityData().set(DATA_JITTER_UP, input.getFloatOr("JitterUp", 0.0f));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (!getItem().isEmpty()) {
            output.store("Item", ItemStack.CODEC, getItem());
        }
        output.putInt("GrowTicks", this.growTicks);
        output.putInt("Age", getAge());
        output.putInt("OwnerId", getOwnerId());
        output.putInt("AttachSlot", getAttachSlot());
        output.putFloat("JitterSide", getJitterSide());
        output.putFloat("JitterUp", getJitterUp());
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

        Entity owner = resolveOwner();
        if (owner == null || !owner.isAlive()) {
            dropAndDiscard(serverLevel);
            return;
        }
        snapToOwner(owner);

        if (age % 2 == 0 && !getItem().isEmpty()) {
            Vec3 at = visualPos(1.0f);
            serverLevel.sendParticles(
                    new ItemParticleOption(ParticleTypes.ITEM, getItem().getItem()),
                    at.x, at.y, at.z,
                    1, 0.05, 0.05, 0.05, 0.01
            );
        }
        if (age >= this.growTicks) {
            dropAndDiscard(serverLevel);
        }
    }

    private void snapToOwner(Entity owner) {
        Vec3 attach = visualPos(1.0f);
        this.setDeltaMovement(owner.getDeltaMovement());
        this.setPos(attach.x, attach.y, attach.z);
    }

    private void dropAndDiscard(ServerLevel level) {
        ItemStack stack = getItem().copy();
        if (!stack.isEmpty()) {
            Vec3 at = visualPos(1.0f);
            Vec3 push = outwardNormal(1.0f).scale(0.18).add(0.0, 0.16, 0.0);
            ItemEntity dropped = new ItemEntity(level, at.x, at.y, at.z, stack);
            dropped.setDeltaMovement(push.x, push.y, push.z);
            dropped.setDefaultPickUpDelay();
            level.addFreshEntity(dropped);
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 8, 0.12, 0.08, 0.12, 0.02);
            level.playSound(null, this.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 0.6f);
        }
        this.discard();
    }

    public void setOwner(Entity owner) {
        this.getEntityData().set(DATA_OWNER_ID, owner == null ? -1 : owner.getId());
    }

    public int getOwnerId() {
        return this.getEntityData().get(DATA_OWNER_ID);
    }

    public Entity resolveOwner() {
        int id = getOwnerId();
        if (id < 0) {
            return null;
        }
        return this.level().getEntity(id);
    }

    public int getAttachSlot() {
        return this.getEntityData().get(DATA_ATTACH_SLOT);
    }

    public void setAttachSlot(int slot) {
        this.getEntityData().set(DATA_ATTACH_SLOT, CreationGrowthAnchors.wrapSlot(slot));
    }

    public float getJitterSide() {
        return this.getEntityData().get(DATA_JITTER_SIDE);
    }

    public float getJitterUp() {
        return this.getEntityData().get(DATA_JITTER_UP);
    }

    public void setJitter(float side, float up) {
        this.getEntityData().set(DATA_JITTER_SIDE, side);
        this.getEntityData().set(DATA_JITTER_UP, up);
    }

    public Vec3 visualPos(float partialTick) {
        Entity owner = resolveOwner();
        if (owner == null || !owner.isAlive()) {
            return this.getPosition(partialTick);
        }
        return CreationGrowthAnchors.visualPos(owner, getAttachSlot(), getJitterSide(), getJitterUp(), partialTick);
    }

    public Vec3 outwardNormal(float partialTick) {
        Entity owner = resolveOwner();
        if (owner == null || !owner.isAlive()) {
            return new Vec3(0.0, 0.0, 1.0);
        }
        return CreationGrowthAnchors.outwardNormal(owner, getAttachSlot(), partialTick);
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

    public float growProgress(float partialTick) {
        int duration = Math.max(1, this.getEntityData().get(DATA_GROW_TICKS));
        return Mth.clamp((getAge() + partialTick) / duration, 0.0f, 1.0f);
    }

    public float growScale(float partialTick) {
        float progress = growProgress(partialTick);
        float eased = 1.0f - (1.0f - progress) * (1.0f - progress);
        return 0.05f + eased * 0.25f; // .25
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
