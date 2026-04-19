package com.github.bandithelps.utils.blockdisplays;

import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class BetterBlockDisplay extends Display.BlockDisplay{

    private int lifetime;
    private boolean idleRotationEnabled;
    private int idleRotationStartTick;
    private int idleRotationMinIntervalTicks;
    private int idleRotationMaxIntervalTicks;
    private float idleRotationMinDegrees;
    private float idleRotationMaxDegrees;
    private int nextIdleRotationTick;
    private Quaternionf idleBaseRotation;
    private float idleAccumulatedYawRadians;
    private float idleYawDirection;

    public BetterBlockDisplay(EntityType<?> type, Level level) {
        super(type, level);
        this.lifetime = -1;
        this.idleRotationEnabled = false;
        this.idleRotationStartTick = 0;
        this.idleRotationMinIntervalTicks = 20;
        this.idleRotationMaxIntervalTicks = 40;
        this.idleRotationMinDegrees = 1.5f;
        this.idleRotationMaxDegrees = 4.0f;
        this.nextIdleRotationTick = Integer.MAX_VALUE;
        this.idleBaseRotation = new Quaternionf();
        this.idleAccumulatedYawRadians = 0.0f;
        this.idleYawDirection = 1.0f;
    }

    public void setBlock(BlockState state) {
        this.getEntityData().set(DATA_BLOCK_STATE_ID, state);
    }

    public void setTranslation(Vector3f translation) { this.getEntityData().set(DATA_TRANSLATION_ID, translation); }

    public void setScale(Vector3f scale) {
        this.getEntityData().set(DATA_SCALE_ID, scale);
    }

    public void setRightRotation(Quaternionf rotation) { this.getEntityData().set(DATA_RIGHT_ROTATION_ID, rotation); }

    public void setLeftRotation(Quaternionf rotation) { this.getEntityData().set(DATA_LEFT_ROTATION_ID, rotation); }

    public void setInterpolation(int interpolation) { this.getEntityData().set(DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID, interpolation); }

    public void startInterpolation() { this.getEntityData().set(DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID, -1, true); }

    public void setLifetime(int lifetime) { this.lifetime = lifetime; }

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
        this.idleBaseRotation = new Quaternionf(this.getEntityData().get(DATA_RIGHT_ROTATION_ID));
        if (this.idleBaseRotation.lengthSquared() < 1.0E-6f) {
            this.idleBaseRotation.identity();
        } else {
            this.idleBaseRotation.normalize();
        }
        this.idleAccumulatedYawRadians = 0.0f;
        this.idleYawDirection = this.random.nextBoolean() ? 1.0f : -1.0f;
        this.nextIdleRotationTick = this.idleRotationStartTick;
    }

    private void tickIdleRotation() {
        if (!this.idleRotationEnabled || this.tickCount < this.nextIdleRotationTick) {
            return;
        }

        int intervalRange = this.idleRotationMaxIntervalTicks - this.idleRotationMinIntervalTicks + 1;
        int interval = this.idleRotationMinIntervalTicks + this.random.nextInt(intervalRange);

        float angleRange = this.idleRotationMaxDegrees - this.idleRotationMinDegrees;
        float angleDegrees = this.idleRotationMinDegrees + (angleRange <= 0.0f ? 0.0f : this.random.nextFloat() * angleRange);
        // Keep the idle animation strictly rotational and mist-like: slow yaw drift.
        if (this.random.nextFloat() < 0.12f) {
            this.idleYawDirection *= -1.0f;
        }
        float angleRadians = (float) Math.toRadians(angleDegrees * this.idleYawDirection);
        this.idleAccumulatedYawRadians += angleRadians;

        Quaternionf targetRotation = new Quaternionf(this.idleBaseRotation).rotateY(this.idleAccumulatedYawRadians);
        this.setRightRotation(targetRotation);
        this.setInterpolation(Math.max(1, interval));
        this.startInterpolation();

        this.nextIdleRotationTick = this.tickCount + interval;
    }

    @Override
    public void tick() {
        super.tick();

        this.tickIdleRotation();

        if (this.lifetime > 0) {
            this.lifetime--;
        } else if (this.lifetime != -1){
            this.discard();
        }
    }
}
