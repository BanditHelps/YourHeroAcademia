package com.github.bandithelps.entities;

import com.github.bandithelps.utils.blackwhip.BlackwhipBlockTossStore;
import com.github.bandithelps.utils.blockdisplays.BetterBlockDisplay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Blackwhip block cargo. Hovers as a normal interpolating entity (BlockDisplay snaps, which looks
 * like teleporting), then flies with hardness-scaled hits that drop an item or place like a falling
 * block.
 */
public class BlackwhipTossedBlockEntity extends ThrowableProjectile {

    private static final float MAX_DAMAGE = 20.0f;
    private static final int MAX_LIFE_TICKS = 200;
    /** Skip clip for a moment so the wrap, other cargo, and any overlapped blocks don't eat the throw. */
    private static final int THROW_COLLIDE_GRACE_TICKS = 2;

    private int throwCollideGraceTicks;

    private static final EntityDataAccessor<BlockState> DATA_BLOCK_STATE =
            SynchedEntityData.defineId(BlackwhipTossedBlockEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Float> DATA_HARDNESS =
            SynchedEntityData.defineId(BlackwhipTossedBlockEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BASE_DAMAGE =
            SynchedEntityData.defineId(BlackwhipTossedBlockEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DAMAGE_PER_HARDNESS =
            SynchedEntityData.defineId(BlackwhipTossedBlockEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_KNOCKBACK =
            SynchedEntityData.defineId(BlackwhipTossedBlockEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_HOVERING =
            SynchedEntityData.defineId(BlackwhipTossedBlockEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ORBIT_SLOT =
            SynchedEntityData.defineId(BlackwhipTossedBlockEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ORBIT_COUNT =
            SynchedEntityData.defineId(BlackwhipTossedBlockEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_OWNER_ID =
            SynchedEntityData.defineId(BlackwhipTossedBlockEntity.class, EntityDataSerializers.INT);

    public BlackwhipTossedBlockEntity(EntityType<? extends BlackwhipTossedBlockEntity> type, Level level) {
        super(type, level);
    }

    public BlackwhipTossedBlockEntity(Level level, LivingEntity thrower, BlockState state, float hardness,
                                      float baseDamage, float damagePerHardness, float knockback) {
        super(ModEntities.BLACKWHIP_TOSSED_BLOCK.get(), level);
        this.setOwner(thrower);
        this.setBlockState(state);
        this.entityData.set(DATA_HARDNESS, Math.max(0.0f, hardness));
        this.entityData.set(DATA_BASE_DAMAGE, baseDamage);
        this.entityData.set(DATA_DAMAGE_PER_HARDNESS, damagePerHardness);
        this.entityData.set(DATA_KNOCKBACK, knockback);
        this.setHovering(false);
    }

    public static BlackwhipTossedBlockEntity createHovering(Level level, LivingEntity owner, BlockState state,
                                                            float hardness) {
        BlackwhipTossedBlockEntity cargo = new BlackwhipTossedBlockEntity(level, owner, state, hardness, 0.0f, 0.0f, 0.0f);
        cargo.setHovering(true);
        cargo.setDeltaMovement(Vec3.ZERO);
        return cargo;
    }

    public void armThrowGrace() {
        this.throwCollideGraceTicks = THROW_COLLIDE_GRACE_TICKS;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BLOCK_STATE, Blocks.STONE.defaultBlockState());
        builder.define(DATA_HARDNESS, 1.5f);
        builder.define(DATA_BASE_DAMAGE, 1.0f);
        builder.define(DATA_DAMAGE_PER_HARDNESS, 1.5f);
        builder.define(DATA_KNOCKBACK, 0.35f);
        builder.define(DATA_HOVERING, false);
        builder.define(DATA_ORBIT_SLOT, 0);
        builder.define(DATA_ORBIT_COUNT, 1);
        builder.define(DATA_OWNER_ID, -1);
    }

    @Override
    public void setOwner(Entity owner) {
        super.setOwner(owner);
        this.entityData.set(DATA_OWNER_ID, owner == null ? -1 : owner.getId());
    }

    public Entity resolveHoverOwner() {
        int id = this.entityData.get(DATA_OWNER_ID);
        if (id >= 0) {
            Entity byId = this.level().getEntity(id);
            if (byId != null) {
                return byId;
            }
        }
        return this.getOwner();
    }

    public boolean isHovering() {
        return this.entityData.get(DATA_HOVERING);
    }

    public void setHovering(boolean hovering) {
        this.entityData.set(DATA_HOVERING, hovering);
        this.noPhysics = hovering;
        this.setNoGravity(hovering);
    }

    public void setOrbitSlot(int slot, int count) {
        this.entityData.set(DATA_ORBIT_SLOT, slot);
        this.entityData.set(DATA_ORBIT_COUNT, Math.max(1, count));
    }

    public int getOrbitSlot() {
        return this.entityData.get(DATA_ORBIT_SLOT);
    }

    public int getOrbitCount() {
        return Math.max(1, this.entityData.get(DATA_ORBIT_COUNT));
    }

    /**
     * Client/render cube center that follows the owner's interpolated pose, matching chain smoothness.
     */
    public Vec3 hoverVisualCenter(float partialTick) {
        Entity owner = resolveHoverOwner();
        if (!isHovering() || owner == null || !owner.isAlive()) {
            return super.getPosition(partialTick).add(0.0, this.getBbHeight() * 0.5, 0.0);
        }
        return BlackwhipBlockTossStore.orbitVisualCenter(
                owner, getOrbitSlot(), getOrbitCount(), owner.level().getGameTime(), partialTick);
    }

    public BlockState getBlockState() {
        return this.entityData.get(DATA_BLOCK_STATE);
    }

    public void setBlockState(BlockState state) {
        BlockState stored = state == null ? Blocks.STONE.defaultBlockState() : state;
        if (stored.hasProperty(BlockStateProperties.WATERLOGGED)) {
            stored = stored.setValue(BlockStateProperties.WATERLOGGED, false);
        }
        this.entityData.set(DATA_BLOCK_STATE, stored);
    }

    public float getHardness() {
        return this.entityData.get(DATA_HARDNESS);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04;
    }

    @Override
    public void tick() {
        if (isHovering()) {
            tickHovering();
            return;
        }
        if (this.throwCollideGraceTicks > 0) {
            this.throwCollideGraceTicks--;
            tickThrownGrace();
            return;
        }
        super.tick();
        if (!this.level().isClientSide() && this.tickCount > MAX_LIFE_TICKS) {
            dropAsItem();
            this.discard();
        }
    }

    private void tickThrownGrace() {
        this.applyGravity();
        Vec3 movement = this.getDeltaMovement();
        float inertia = this.isInWater() ? 0.8f : 0.99f;
        movement = movement.scale(inertia);
        this.setDeltaMovement(movement);
        this.setPos(this.getX() + movement.x, this.getY() + movement.y, this.getZ() + movement.z);
        this.updateRotation();
        this.baseTick();
    }

    private void tickHovering() {
        this.baseTick();
        Entity owner = resolveHoverOwner();
        if (this.level().isClientSide()) {
            snapToOwnerOrbit(owner);
            return;
        }
        if (owner == null || !owner.isAlive()) {
            dropAsItem();
            this.discard();
        }
    }

    private void snapToOwnerOrbit(Entity owner) {
        if (owner == null || !owner.isAlive()) {
            return;
        }
        Vec3 center = BlackwhipBlockTossStore.orbitVisualCenter(
                owner, getOrbitSlot(), getOrbitCount(), owner.level().getGameTime(), 0.0f);
        this.setPos(center.x, center.y - this.getBbHeight() * 0.5, center.z);
        this.setDeltaMovement(owner.getDeltaMovement());
    }

    @Override
    public void snapTo(Vec3 pos, float yRot, float xRot) {
        if (isHovering() && this.level().isClientSide() && this.tickCount > 0) {
            return;
        }
        super.snapTo(pos, yRot, xRot);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        if (isHovering()) {
            return distance < 4096.0;
        }
        return super.shouldRenderAtSqrDistance(distance);
    }

    @Override
    public boolean isPickable() {
        return !isHovering() && super.isPickable();
    }

    @Override
    public boolean canBeHitByProjectile() {
        return !isHovering() && super.canBeHitByProjectile();
    }

    @Override
    public AABB makeBoundingBox(Vec3 position) {
        if (isHovering()) {
            return new AABB(position, position);
        }
        return super.makeBoundingBox(position);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (this.throwCollideGraceTicks > 0
                || isHovering()
                || entity instanceof BlackwhipChainEntity
                || entity instanceof BlackwhipSegmentEntity
                || entity instanceof BlackwhipEntity
                || entity instanceof BetterBlockDisplay
                || entity instanceof BlackwhipTossedBlockEntity) {
            return false;
        }
        Entity owner = this.getOwner();
        if (owner != null && entity == owner) {
            return false;
        }
        return super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide()) {
            return;
        }
        Entity hit = result.getEntity();
        if (hit instanceof LivingEntity living && living.isAlive()) {
            float hardness = getHardness();
            float damage = Mth.clamp(
                    this.entityData.get(DATA_BASE_DAMAGE) + hardness * this.entityData.get(DATA_DAMAGE_PER_HARDNESS),
                    0.0f, MAX_DAMAGE);
            Entity owner = this.getOwner();
            living.hurt(this.damageSources().mobAttack(owner instanceof LivingEntity livingOwner ? livingOwner : living),
                    damage);
            Vec3 motion = this.getDeltaMovement();
            double kb = this.entityData.get(DATA_KNOCKBACK) + Math.min(hardness, 8.0f) * 0.04;
            if (motion.lengthSqr() > 1.0e-6) {
                Vec3 push = motion.normalize().scale(kb);
                living.push(push.x, Math.max(0.25, push.y), push.z);
            } else {
                living.knockback(kb, this.getX() - living.getX(), this.getZ() - living.getZ());
            }
        }
        this.level().playSound(null, this.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.8f, 0.9f);
        dropAsItem();
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level().isClientSide() || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos hitPos = result.getBlockPos();
        BlockPos placePos = hitPos.relative(result.getDirection());
        if (!tryPlace(serverLevel, placePos, result.getDirection())
                && !tryPlace(serverLevel, hitPos, Direction.UP)) {
            dropAsItem();
        }
        this.level().playSound(null, this.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.7f, 1.0f);
        this.discard();
    }

    private boolean tryPlace(ServerLevel level, BlockPos pos, Direction clickedFace) {
        BlockState toPlace = getBlockState();
        if (toPlace.isAir()) {
            return false;
        }
        boolean mayReplace = level.getBlockState(pos).canBeReplaced(
                new DirectionalPlaceContext(level, pos, clickedFace.getOpposite(), ItemStack.EMPTY, clickedFace));
        if (!mayReplace || !toPlace.canSurvive(level, pos)) {
            return false;
        }
        if (toPlace.hasProperty(BlockStateProperties.WATERLOGGED) && level.getFluidState(pos).is(Fluids.WATER)) {
            toPlace = toPlace.setValue(BlockStateProperties.WATERLOGGED, true);
        }
        return level.setBlock(pos, toPlace, 3);
    }

    private void dropAsItem() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack stack = new ItemStack(getBlockState().getBlock().asItem());
        if (stack.isEmpty()) {
            return;
        }
        Vec3 at = this.position();
        level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, stack));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("BlockState", BlockState.CODEC, getBlockState());
        output.putFloat("Hardness", getHardness());
        output.putFloat("BaseDamage", this.entityData.get(DATA_BASE_DAMAGE));
        output.putFloat("DamagePerHardness", this.entityData.get(DATA_DAMAGE_PER_HARDNESS));
        output.putFloat("Knockback", this.entityData.get(DATA_KNOCKBACK));
        output.putBoolean("Hovering", isHovering());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.read("BlockState", BlockState.CODEC).ifPresent(this::setBlockState);
        this.entityData.set(DATA_HARDNESS, input.getFloatOr("Hardness", 1.5f));
        this.entityData.set(DATA_BASE_DAMAGE, input.getFloatOr("BaseDamage", 1.0f));
        this.entityData.set(DATA_DAMAGE_PER_HARDNESS, input.getFloatOr("DamagePerHardness", 1.5f));
        this.entityData.set(DATA_KNOCKBACK, input.getFloatOr("Knockback", 0.35f));
        this.setHovering(input.getBooleanOr("Hovering", false));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_HOVERING.equals(accessor)) {
            boolean hovering = isHovering();
            this.noPhysics = hovering;
            this.setNoGravity(hovering);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity, packedSpawnData());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        int data = packet.getData();
        this.setHovering((data & 0x80000000) != 0);
        this.setBlockState(Block.stateById(data & 0x7FFFFFFF));
    }

    private int packedSpawnData() {
        int id = Block.getId(this.getBlockState()) & 0x7FFFFFFF;
        return isHovering() ? id | 0x80000000 : id;
    }
}
