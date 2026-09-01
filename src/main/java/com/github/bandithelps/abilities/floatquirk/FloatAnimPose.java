package com.github.bandithelps.abilities.floatquirk;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client pose bookkeeping for Float: smoothed horizontal lean (0..1) and the
 * delayed sitting state. Movement is unchanged; this is visual only.
 */
public final class FloatAnimPose {

    public static final double STILL_THRESHOLD = 0.02d;
    public static final double LEAN_START_SPEED = 0.02d;
    public static final double LEAN_FULL_SPEED = FloatPhysics.FIREWORK_MAX_SPEED * 2.0d;
    public static final double SIT_BREAK_SPEED = FloatPhysics.FIREWORK_IMPULSE;
    public static final int SIT_DELAY_TICKS = 50;
    private static final double LEAN_SMOOTH = 0.25d;

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private FloatAnimPose() {
    }

    public static double getLean(LivingEntity entity) {
        if (entity == null) {
            return 0.0d;
        }
        State state = STATES.get(entity.getUUID());
        if (state != null) {
            return state.lean;
        }
        return rawLean(entity);
    }

    public static boolean isSitting(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        State state = STATES.get(entity.getUUID());
        return state != null && state.sitting;
    }

    public static void tick(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        State state = STATES.computeIfAbsent(entity.getUUID(), id -> new State());
        double horiz = entity.getDeltaMovement().horizontalDistance();
        double target = rawLean(horiz);

        if (state.lastTick != entity.tickCount) {
            state.lastTick = entity.tickCount;
            state.lean += (target - state.lean) * LEAN_SMOOTH;

            if (entity.hurtTime > 0 || horiz > SIT_BREAK_SPEED) {
                state.sitting = false;
                state.stillSinceTick = -1;
            } else if (horiz < STILL_THRESHOLD) {
                if (state.stillSinceTick < 0) {
                    state.stillSinceTick = entity.tickCount;
                }
                if (!state.sitting && entity.tickCount - state.stillSinceTick >= SIT_DELAY_TICKS) {
                    state.sitting = true;
                }
            } else {
                state.stillSinceTick = -1;
            }
        }
    }

    public static void clear(UUID id) {
        if (id != null) {
            STATES.remove(id);
        }
    }

    public static double rawLean(LivingEntity entity) {
        if (entity == null) {
            return 0.0d;
        }
        return rawLean(entity.getDeltaMovement().horizontalDistance());
    }

    private static double rawLean(double horizontalSpeed) {
        double span = LEAN_FULL_SPEED - LEAN_START_SPEED;
        if (span <= 0.0d) {
            return 0.0d;
        }
        double t = Mth.clamp((horizontalSpeed - LEAN_START_SPEED) / span, 0.0d, 1.0d);
        return t * t * (3.0d - 2.0d * t);
    }

    private static final class State {
        double lean;
        boolean sitting;
        int stillSinceTick = -1;
        int lastTick = Integer.MIN_VALUE;
    }
}
