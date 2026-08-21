package com.github.bandithelps.entities;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.function.Predicate;

/**
 * Hit-proxy link in a {@link BlackwhipChainEntity}. Positions are owned by the parent chain's IK;
 * this entity only exists so attacks/explosions can land along the whip.
 */
public class BlackwhipSegmentEntity extends Entity {

    /**
     * Set from client setup so owner/target click through without referencing client classes here.
     */
    public static Predicate<BlackwhipSegmentEntity> CLIENT_PICK_FILTER = segment -> true;

    private static final EntityDataAccessor<Integer> DATA_CHAIN_ID =
            SynchedEntityData.defineId(BlackwhipSegmentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_INDEX =
            SynchedEntityData.defineId(BlackwhipSegmentEntity.class, EntityDataSerializers.INT);

    public BlackwhipSegmentEntity(EntityType<? extends BlackwhipSegmentEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_CHAIN_ID, -1);
        builder.define(DATA_INDEX, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        BlackwhipChainEntity chain = getChain();
        if (chain == null || !chain.isAlive()) {
            this.discard();
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        BlackwhipChainEntity chain = getChain();
        if (chain == null || !chain.isAlive()) {
            return false;
        }
        return chain.damageFromSegment(source, damage, getIndex());
    }

    /**
     * Third parties can pick/attack the whip. On the client, owner and tagged target click through
     * so their normal interactions are not blocked.
     */
    @Override
    public boolean isPickable() {
        if (this.level().isClientSide()) {
            return CLIENT_PICK_FILTER.test(this);
        }
        return true;
    }

    /**
     * Segments must stay melee-pickable, but projectiles should never collide with them — otherwise
     * a latched skeleton/player shooting from inside the wrap band has arrows bounce off the tip
     * proxies in front of their face. Deploy tip-grab still finds projectiles via its own sweep.
     */
    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity entity) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    public void setChainId(int chainId) {
        this.getEntityData().set(DATA_CHAIN_ID, chainId);
    }

    public void setIndex(int index) {
        this.getEntityData().set(DATA_INDEX, index);
    }

    public int getChainId() {
        return this.getEntityData().get(DATA_CHAIN_ID);
    }

    public int getIndex() {
        return this.getEntityData().get(DATA_INDEX);
    }

    public BlackwhipChainEntity getChain() {
        int id = getChainId();
        if (id < 0) {
            return null;
        }
        Entity e = this.level().getEntity(id);
        return e instanceof BlackwhipChainEntity chain ? chain : null;
    }
}
