package com.github.bandithelps.entities;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A server-spawned, data-synced entity that represents one or more Blackwhip strands. Rendering is
 * fully driven by the synched data on the client, so the whips appear for every viewer, get recorded
 * by replay tools, and are culled like any other entity.
 *
 * <p>The entity itself holds no movement logic; it simply tracks an owner (and optionally a target
 * entity or a fixed world point) and exposes visual parameters. The actual ribbon geometry is built
 * in {@code BlackwhipEntityRenderer} based on {@link #getStyle()}.</p>
 */
public class BlackwhipEntity extends Entity {

    public static final int END_NONE = 0;
    public static final int END_ENTITY = 1;
    public static final int END_POINT = 2;

    private static final EntityDataAccessor<Integer> DATA_STYLE = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_OWNER_ID = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ANCHOR = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_END_MODE = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_END_ENTITY = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_END_X = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_END_Y = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_END_Z = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_CORE_COLOR = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GLOW_COLOR = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_THICKNESS = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CURVE = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_JAGGED = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LENGTH = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_STRANDS = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TRAVEL_TICKS = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RETRACT_TICKS = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ACTIVE = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_FORWARD_OFFSET = SynchedEntityData.defineId(BlackwhipEntity.class, EntityDataSerializers.FLOAT);

    // Server-side default canon colors (near-black core, teal glow). Overridden per-owner by helper.
    public static final int DEFAULT_CORE = 0xFF101A1A;
    public static final int DEFAULT_GLOW = 0xB325BE9C;

    /** Optional automatic discard countdown for transient whips (miss/lash). -1 = no auto-discard. */
    private int lifetime = -1;
    /** When deactivated, this records the server tick so we keep the entity alive for the retract animation. */
    private int retractCountdown = -1;

    public BlackwhipEntity(EntityType<? extends BlackwhipEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STYLE, BlackwhipStyle.TETHER.ordinal());
        builder.define(DATA_OWNER_ID, -1);
        builder.define(DATA_ANCHOR, BlackwhipAnchor.HAND.ordinal());
        builder.define(DATA_END_MODE, END_NONE);
        builder.define(DATA_END_ENTITY, -1);
        builder.define(DATA_END_X, 0.0f);
        builder.define(DATA_END_Y, 0.0f);
        builder.define(DATA_END_Z, 0.0f);
        builder.define(DATA_CORE_COLOR, DEFAULT_CORE);
        builder.define(DATA_GLOW_COLOR, DEFAULT_GLOW);
        builder.define(DATA_THICKNESS, 1.0f);
        builder.define(DATA_CURVE, 0.6f);
        builder.define(DATA_JAGGED, 0.3f);
        builder.define(DATA_LENGTH, 2.0f);
        builder.define(DATA_STRANDS, 1);
        builder.define(DATA_SEED, 0);
        builder.define(DATA_TRAVEL_TICKS, 6);
        builder.define(DATA_RETRACT_TICKS, 6);
        builder.define(DATA_ACTIVE, true);
        builder.define(DATA_FORWARD_OFFSET, 1.2f);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }

        // Owner gone -> clean up.
        Entity owner = getOwnerId() >= 0 ? this.level().getEntity(getOwnerId()) : null;
        if (getOwnerId() >= 0 && (owner == null || !owner.isAlive())) {
            this.discard();
            return;
        }

        // Follow the owner so the dispatcher render origin stays near the ribbon (precision + culling origin).
        BlackwhipStyle style = getStyle();
        if (owner != null && style != BlackwhipStyle.WRAP) {
            this.setPos(owner.getX(), owner.getY() + owner.getBbHeight() * 0.5, owner.getZ());
        } else if (style == BlackwhipStyle.WRAP && getEndMode() == END_ENTITY) {
            Entity target = this.level().getEntity(getEndEntity());
            if (target != null && target.isAlive()) {
                this.setPos(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
            }
        }

        // If the end is an entity that died, drop the whip.
        if (getEndMode() == END_ENTITY) {
            Entity target = this.level().getEntity(getEndEntity());
            if (target == null || !target.isAlive()) {
                deactivate();
            }
        }

        if (this.retractCountdown > 0) {
            this.retractCountdown--;
            if (this.retractCountdown == 0) {
                this.discard();
                return;
            }
        }

        if (this.lifetime > 0) {
            this.lifetime--;
        } else if (this.lifetime == 0) {
            deactivate();
        }
    }

    /** Begins the retract animation and schedules discard after the retract window. */
    public void deactivate() {
        if (!isActive() && this.retractCountdown >= 0) {
            return;
        }
        this.getEntityData().set(DATA_ACTIVE, false);
        this.retractCountdown = Math.max(1, getRetractTicks());
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 16384.0;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        // Transient entity (noSave); nothing persisted.
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        // Transient entity (noSave); nothing persisted.
    }

    // ---- Setters (server) ----

    public void setStyle(BlackwhipStyle style) {
        this.getEntityData().set(DATA_STYLE, style.ordinal());
    }

    public void setOwnerId(int id) {
        this.getEntityData().set(DATA_OWNER_ID, id);
    }

    public void setAnchor(BlackwhipAnchor anchor) {
        this.getEntityData().set(DATA_ANCHOR, anchor.ordinal());
    }

    public void setEndEntity(int entityId) {
        this.getEntityData().set(DATA_END_MODE, END_ENTITY);
        this.getEntityData().set(DATA_END_ENTITY, entityId);
    }

    public void setEndPoint(Vec3 point) {
        this.getEntityData().set(DATA_END_MODE, END_POINT);
        this.getEntityData().set(DATA_END_X, (float) point.x);
        this.getEntityData().set(DATA_END_Y, (float) point.y);
        this.getEntityData().set(DATA_END_Z, (float) point.z);
    }

    public void setEndNone() {
        this.getEntityData().set(DATA_END_MODE, END_NONE);
    }

    public void setColors(int core, int glow) {
        this.getEntityData().set(DATA_CORE_COLOR, core);
        this.getEntityData().set(DATA_GLOW_COLOR, glow);
    }

    public void setThickness(float thickness) {
        this.getEntityData().set(DATA_THICKNESS, thickness);
    }

    public void setCurve(float curve) {
        this.getEntityData().set(DATA_CURVE, curve);
    }

    public void setJaggedness(float jagged) {
        this.getEntityData().set(DATA_JAGGED, jagged);
    }

    public void setLength(float length) {
        this.getEntityData().set(DATA_LENGTH, length);
    }

    public void setStrands(int strands) {
        this.getEntityData().set(DATA_STRANDS, strands);
    }

    public void setSeed(int seed) {
        this.getEntityData().set(DATA_SEED, seed);
    }

    public void setTravelTicks(int ticks) {
        this.getEntityData().set(DATA_TRAVEL_TICKS, Math.max(1, ticks));
    }

    public void setRetractTicks(int ticks) {
        this.getEntityData().set(DATA_RETRACT_TICKS, Math.max(1, ticks));
    }

    public void setForwardOffset(float offset) {
        this.getEntityData().set(DATA_FORWARD_OFFSET, offset);
    }

    public void setLifetime(int lifetime) {
        this.lifetime = lifetime;
    }

    // ---- Getters ----

    public BlackwhipStyle getStyle() {
        return BlackwhipStyle.byOrdinal(this.getEntityData().get(DATA_STYLE));
    }

    public int getOwnerId() {
        return this.getEntityData().get(DATA_OWNER_ID);
    }

    public BlackwhipAnchor getAnchor() {
        return BlackwhipAnchor.byOrdinal(this.getEntityData().get(DATA_ANCHOR));
    }

    public int getEndMode() {
        return this.getEntityData().get(DATA_END_MODE);
    }

    public int getEndEntity() {
        return this.getEntityData().get(DATA_END_ENTITY);
    }

    public Vec3 getEndPoint() {
        return new Vec3(this.getEntityData().get(DATA_END_X), this.getEntityData().get(DATA_END_Y), this.getEntityData().get(DATA_END_Z));
    }

    public int getCoreColor() {
        return this.getEntityData().get(DATA_CORE_COLOR);
    }

    public int getGlowColor() {
        return this.getEntityData().get(DATA_GLOW_COLOR);
    }

    public float getThickness() {
        return this.getEntityData().get(DATA_THICKNESS);
    }

    public float getCurve() {
        return this.getEntityData().get(DATA_CURVE);
    }

    public float getJaggedness() {
        return this.getEntityData().get(DATA_JAGGED);
    }

    public float getLength() {
        return this.getEntityData().get(DATA_LENGTH);
    }

    public int getStrands() {
        return this.getEntityData().get(DATA_STRANDS);
    }

    public int getSeed() {
        return this.getEntityData().get(DATA_SEED);
    }

    public int getTravelTicks() {
        return this.getEntityData().get(DATA_TRAVEL_TICKS);
    }

    public int getRetractTicks() {
        return this.getEntityData().get(DATA_RETRACT_TICKS);
    }

    public boolean isActive() {
        return this.getEntityData().get(DATA_ACTIVE);
    }

    public float getForwardOffset() {
        return this.getEntityData().get(DATA_FORWARD_OFFSET);
    }
}
