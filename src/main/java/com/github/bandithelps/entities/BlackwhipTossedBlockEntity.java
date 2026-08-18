package com.github.bandithelps.entities;

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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Thrown Blackwhip cargo: hardness-scaled entity hits drop the block as an item; block hits place
 * it like a vanilla falling block.
 */
public class BlackwhipTossedBlockEntity extends ThrowableProjectile {

    private static final float MAX_DAMAGE = 20.0f;
    private static final int MAX_LIFE_TICKS = 200;

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
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BLOCK_STATE, Blocks.STONE.defaultBlockState());
        builder.define(DATA_HARDNESS, 1.5f);
        builder.define(DATA_BASE_DAMAGE, 1.0f);
        builder.define(DATA_DAMAGE_PER_HARDNESS, 1.5f);
        builder.define(DATA_KNOCKBACK, 0.35f);
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
        super.tick();
        if (!this.level().isClientSide() && this.tickCount > MAX_LIFE_TICKS) {
            dropAsItem();
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (entity instanceof BlackwhipChainEntity
                || entity instanceof BlackwhipSegmentEntity
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
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.read("BlockState", BlockState.CODEC).ifPresent(this::setBlockState);
        this.entityData.set(DATA_HARDNESS, input.getFloatOr("Hardness", 1.5f));
        this.entityData.set(DATA_BASE_DAMAGE, input.getFloatOr("BaseDamage", 1.0f));
        this.entityData.set(DATA_DAMAGE_PER_HARDNESS, input.getFloatOr("DamagePerHardness", 1.5f));
        this.entityData.set(DATA_KNOCKBACK, input.getFloatOr("Knockback", 0.35f));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity, Block.getId(this.getBlockState()));
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.setBlockState(Block.stateById(packet.getData()));
    }
}
