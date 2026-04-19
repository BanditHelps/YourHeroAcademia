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
import com.github.bandithelps.utils.blockdisplays.RgbaBlendMode;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class RgbaDisplayEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_ARGB_COLOR = SynchedEntityData.defineId(RgbaDisplayEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SCALE_X = SynchedEntityData.defineId(RgbaDisplayEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SCALE_Y = SynchedEntityData.defineId(RgbaDisplayEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SCALE_Z = SynchedEntityData.defineId(RgbaDisplayEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROT_X = SynchedEntityData.defineId(RgbaDisplayEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROT_Y = SynchedEntityData.defineId(RgbaDisplayEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROT_Z = SynchedEntityData.defineId(RgbaDisplayEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROT_W = SynchedEntityData.defineId(RgbaDisplayEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_BLEND_MODE = SynchedEntityData.defineId(RgbaDisplayEntity.class, EntityDataSerializers.INT);

    private int lifetime = -1;
    private int animationTicksRemaining;
    private double motionXPerTick;
    private double motionYPerTick;
    private double motionZPerTick;
    private float scaleXPerTick;
    private float scaleYPerTick;
    private float scaleZPerTick;
    private boolean idleRotationEnabled;
    private int idleRotationStartTick;
    private int idleRotationMinIntervalTicks;
    private int idleRotationMaxIntervalTicks;
    private float idleRotationMinDegrees;
    private float idleRotationMaxDegrees;
    private int nextIdleRotationTick;
    private int idleRotationTicksRemaining;
    private float idleYawStepRadians;
    private float idleYawDirection;

    public RgbaDisplayEntity(EntityType<? extends RgbaDisplayEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.idleRotationEnabled = false;
        this.idleRotationStartTick = 0;
        this.idleRotationMinIntervalTicks = 20;
        this.idleRotationMaxIntervalTicks = 40;
        this.idleRotationMinDegrees = 1.5f;
        this.idleRotationMaxDegrees = 4.0f;
        this.nextIdleRotationTick = Integer.MAX_VALUE;
        this.idleRotationTicksRemaining = 0;
        this.idleYawStepRadians = 0.0f;
        this.idleYawDirection = 1.0f;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ARGB_COLOR, 0xFFFFFFFF);
        builder.define(DATA_SCALE_X, 1.0f);
        builder.define(DATA_SCALE_Y, 1.0f);
        builder.define(DATA_SCALE_Z, 1.0f);
        builder.define(DATA_ROT_X, 0.0f);
        builder.define(DATA_ROT_Y, 0.0f);
        builder.define(DATA_ROT_Z, 0.0f);
        builder.define(DATA_ROT_W, 1.0f);
        builder.define(DATA_BLEND_MODE, RgbaBlendMode.NORMAL.ordinal());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.lifetime = input.getIntOr("Lifetime", -1);
        this.getEntityData().set(DATA_ARGB_COLOR, input.getIntOr("Argb", 0xFFFFFFFF));
        this.getEntityData().set(DATA_SCALE_X, input.getFloatOr("ScaleX", 1.0f));
        this.getEntityData().set(DATA_SCALE_Y, input.getFloatOr("ScaleY", 1.0f));
        this.getEntityData().set(DATA_SCALE_Z, input.getFloatOr("ScaleZ", 1.0f));
        this.getEntityData().set(DATA_ROT_X, input.getFloatOr("RotX", 0.0f));
        this.getEntityData().set(DATA_ROT_Y, input.getFloatOr("RotY", 0.0f));
        this.getEntityData().set(DATA_ROT_Z, input.getFloatOr("RotZ", 0.0f));
        this.getEntityData().set(DATA_ROT_W, input.getFloatOr("RotW", 1.0f));
        this.getEntityData().set(DATA_BLEND_MODE, input.getIntOr("BlendMode", RgbaBlendMode.NORMAL.ordinal()));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Lifetime", this.lifetime);
        output.putInt("Argb", this.getArgbColor());
        output.putFloat("ScaleX", this.getScaleXValue());
        output.putFloat("ScaleY", this.getScaleYValue());
        output.putFloat("ScaleZ", this.getScaleZValue());
        Quaternionf rotation = this.getRotationQuaternion();
        output.putFloat("RotX", rotation.x);
        output.putFloat("RotY", rotation.y);
        output.putFloat("RotZ", rotation.z);
        output.putFloat("RotW", rotation.w);
        output.putInt("BlendMode", this.getEntityData().get(DATA_BLEND_MODE));
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide()) {
            if (this.animationTicksRemaining > 0) {
                this.setPos(this.getX() + this.motionXPerTick, this.getY() + this.motionYPerTick, this.getZ() + this.motionZPerTick);
                this.setScale(new Vector3f(
                        this.getScaleXValue() + this.scaleXPerTick,
                        this.getScaleYValue() + this.scaleYPerTick,
                        this.getScaleZValue() + this.scaleZPerTick
                ));
                this.animationTicksRemaining--;
            }

            this.tickIdleRotation();

            if (this.lifetime > 0) {
                this.lifetime--;
            } else if (this.lifetime == 0) {
                this.discard();
            }
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    public void setArgbColor(int argb) {
        this.getEntityData().set(DATA_ARGB_COLOR, argb);
    }

    public int getArgbColor() {
        return this.getEntityData().get(DATA_ARGB_COLOR);
    }

    public void setScale(Vector3f scale) {
        this.getEntityData().set(DATA_SCALE_X, scale.x);
        this.getEntityData().set(DATA_SCALE_Y, scale.y);
        this.getEntityData().set(DATA_SCALE_Z, scale.z);
    }

    public Vector3f getScaleVector() {
        return new Vector3f(this.getScaleXValue(), this.getScaleYValue(), this.getScaleZValue());
    }

    public void setRightRotation(Quaternionf rotation) {
        this.getEntityData().set(DATA_ROT_X, rotation.x);
        this.getEntityData().set(DATA_ROT_Y, rotation.y);
        this.getEntityData().set(DATA_ROT_Z, rotation.z);
        this.getEntityData().set(DATA_ROT_W, rotation.w);
    }

    public Quaternionf getRotationQuaternion() {
        return new Quaternionf(
                this.getEntityData().get(DATA_ROT_X),
                this.getEntityData().get(DATA_ROT_Y),
                this.getEntityData().get(DATA_ROT_Z),
                this.getEntityData().get(DATA_ROT_W)
        );
    }

    public void setLifetime(int lifetime) {
        this.lifetime = lifetime;
    }

    public void setBlendMode(RgbaBlendMode blendMode) {
        this.getEntityData().set(DATA_BLEND_MODE, blendMode.ordinal());
    }

    public RgbaBlendMode getBlendMode() {
        int ordinal = this.getEntityData().get(DATA_BLEND_MODE);
        RgbaBlendMode[] values = RgbaBlendMode.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return RgbaBlendMode.NORMAL;
        }
        return values[ordinal];
    }

    public void startAnimation(Vector3f translation, Vector3f finalScale, int durationTicks) {
        int duration = Math.max(1, durationTicks);
        this.motionXPerTick = translation.x / duration;
        this.motionYPerTick = translation.y / duration;
        this.motionZPerTick = translation.z / duration;

        Vector3f currentScale = this.getScaleVector();
        this.scaleXPerTick = (finalScale.x - currentScale.x) / duration;
        this.scaleYPerTick = (finalScale.y - currentScale.y) / duration;
        this.scaleZPerTick = (finalScale.z - currentScale.z) / duration;
        this.animationTicksRemaining = duration;
    }

    public void enableIdleRotation(
            int startDelayTicks,
            int minIntervalTicks,
            int maxIntervalTicks,
            float minDegrees,
            float maxDegrees) {
        this.idleRotationEnabled = true;
        this.idleRotationStartTick = Math.max(0, startDelayTicks);
        this.idleRotationMinIntervalTicks = Math.max(2, minIntervalTicks);
        this.idleRotationMaxIntervalTicks = Math.max(this.idleRotationMinIntervalTicks, maxIntervalTicks);
        this.idleRotationMinDegrees = Math.max(0.1f, minDegrees);
        this.idleRotationMaxDegrees = Math.max(this.idleRotationMinDegrees, maxDegrees);
        this.nextIdleRotationTick = this.idleRotationStartTick;
        this.idleRotationTicksRemaining = 0;
        this.idleYawStepRadians = 0.0f;
        this.idleYawDirection = this.random.nextBoolean() ? 1.0f : -1.0f;
    }

    private void tickIdleRotation() {
        if (!this.idleRotationEnabled) {
            return;
        }

        if (this.idleRotationTicksRemaining > 0) {
            Quaternionf nextRotation = this.getRotationQuaternion().rotateY(this.idleYawStepRadians);
            this.setRightRotation(nextRotation);
            this.idleRotationTicksRemaining--;
        }

        if (this.tickCount < this.nextIdleRotationTick) {
            return;
        }

        int intervalRange = this.idleRotationMaxIntervalTicks - this.idleRotationMinIntervalTicks + 1;
        int interval = this.idleRotationMinIntervalTicks + this.random.nextInt(intervalRange);
        float angleRange = this.idleRotationMaxDegrees - this.idleRotationMinDegrees;
        float angleDegrees = this.idleRotationMinDegrees + (angleRange <= 0.0f ? 0.0f : this.random.nextFloat() * angleRange);
        if (this.random.nextFloat() < 0.12f) {
            this.idleYawDirection *= -1.0f;
        }

        float angleRadians = (float) Math.toRadians(angleDegrees * this.idleYawDirection);
        this.idleYawStepRadians = angleRadians / interval;
        this.idleRotationTicksRemaining = interval;
        this.nextIdleRotationTick = this.tickCount + interval;
    }

    private float getScaleXValue() {
        return this.getEntityData().get(DATA_SCALE_X);
    }

    private float getScaleYValue() {
        return this.getEntityData().get(DATA_SCALE_Y);
    }

    private float getScaleZValue() {
        return this.getEntityData().get(DATA_SCALE_Z);
    }
}
