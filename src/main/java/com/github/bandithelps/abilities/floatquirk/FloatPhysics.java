package com.github.bandithelps.abilities.floatquirk;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zero-G hover physics for Float. Horizontal speed is owned by a stored coast
 * vector (activation momentum, fireworks, knockback, zip). Floor contact must
 * not replace that coast; a wall hit may.
 */
public final class FloatPhysics {

    /** Just enough to unstick from the floor. Larger values launch and steal XZ. */
    public static final double GROUND_NUDGE = 0.08d;
    public static final double HORIZONTAL_KEEP = 0.9995d;
    public static final double UPWARD_KEEP = 0.997d;
    public static final double FALL_DECEL = 0.10d;
    public static final double VERTICAL_SELF_SPEED = 0.06d;
    public static final double VERTICAL_ACCEL = 0.012d;
    public static final double FIREWORK_IMPULSE = 0.4d;
    public static final double FIREWORK_MAX_SPEED = 0.35d;
    public static final double DEFAULT_MAX_HEIGHT = 8.0d;
    public static final double DEFAULT_MAX_SPEED = 8.0d;
    /** Blocks above the jump ceiling where self-ascent falls to 1/e. */
    public static final double HEIGHT_FALLOFF = 8.0d;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> DEBUG = new ConcurrentHashMap<>();

    private FloatPhysics() {
    }

    public static Session begin(LivingEntity entity) {
        Session session = new Session(entity.getY());
        SESSIONS.put(entity.getUUID(), session);
        return session;
    }

    public static Session sessionOf(LivingEntity entity) {
        return entity == null ? null : SESSIONS.get(entity.getUUID());
    }

    public static void end(LivingEntity entity) {
        if (entity != null) {
            SESSIONS.remove(entity.getUUID());
        }
    }

    public static boolean toggleDebug(UUID playerId) {
        boolean enabled = !DEBUG.getOrDefault(playerId, false);
        DEBUG.put(playerId, enabled);
        return enabled;
    }

    public static boolean isDebug(UUID playerId) {
        return DEBUG.getOrDefault(playerId, false);
    }

    public static void activate(LivingEntity entity) {
        boolean wasOnGround = entity.onGround();
        Vec3 vel = entity.getDeltaMovement();
        Session session = begin(entity);
        session.rememberCoast(vel);

        entity.resetFallDistance();
        entity.setOnGround(false);
        entity.setNoGravity(true);

        if (wasOnGround && vel.y <= 0.0d) {
            applyVelocity(entity, new Vec3(vel.x, vel.y + GROUND_NUDGE, vel.z), session.resolvedMaxSpeed, false);
        }

        FloatActivateEffects.play(entity, wasOnGround, vel);

        session.lastPhysicsTick = entity.tickCount;
        debug(entity, session, "activate wasOnGround=" + wasOnGround);
    }

    public static void deactivate(LivingEntity entity) {
        end(entity);
        if (entity != null) {
            entity.setNoGravity(false);
        }
    }

    public static void tick(LivingEntity entity, double maxHeight, double maxSpeed) {
        if (entity == null || !entity.isAlive()) {
            return;
        }
        if (entity instanceof Player player && player.getAbilities().flying) {
            return;
        }

        Session session = SESSIONS.get(entity.getUUID());
        if (session == null && !entity.level().isClientSide()) {
            session = begin(entity);
            session.rememberCoast(entity.getDeltaMovement());
        }
        if (session != null && session.lastPhysicsTick == entity.tickCount) {
            return;
        }
        if (session != null) {
            session.lastPhysicsTick = entity.tickCount;
            session.ticks++;
            session.resolvedMaxHeight = maxHeight;
            session.resolvedMaxSpeed = maxSpeed;
        }

        entity.setNoGravity(true);
        entity.resetFallDistance();
        entity.setOnGround(false);

        double overshoot = sampleGroundCeiling(entity, maxHeight, session);

        Control control = readControl(entity);
        Vec3 current = entity.getDeltaMovement();
        double y = adjustVertical(current.y, control, overshoot);
        applyVelocity(entity, new Vec3(current.x, y, current.z), maxSpeed, false);
    }

    /**
     * Firework look-boost. Caps the result at {@link #FIREWORK_MAX_SPEED} unless
     * they were already faster (zip, knockback), in which case speed is not reduced.
     */
    public static void applyFireworkImpulse(LivingEntity entity, Vec3 impulse) {
        if (entity == null || impulse == null) {
            return;
        }
        Vec3 before = entity.getDeltaMovement();
        double fireworkCap = Math.max(FIREWORK_MAX_SPEED, before.length());
        Vec3 boosted = clampSpeed(before.add(impulse), fireworkCap);
        applyVelocity(entity, boosted, true);
    }

    /**
     * Runs after {@code travel()}. Coast XZ is restored unless they hit a wall
     * or an impulse made them faster horizontally.
     */
    public static void afterMovement(LivingEntity entity) {
        Session session = sessionOf(entity);
        if (session == null || entity == null || !entity.isAlive()) {
            return;
        }
        if (entity instanceof Player player && player.getAbilities().flying) {
            return;
        }

        Vec3 vel = entity.getDeltaMovement();
        double currentHoriz = horizontal(vel);
        double coastHoriz = horizontal(session.coastX, session.coastZ);

        if (entity.horizontalCollision) {
            session.rememberCoast(vel);
            entity.setOnGround(false);
            entity.resetFallDistance();
            debug(entity, session, "after wall horiz=" + fmt(currentHoriz));
            return;
        }

        if (currentHoriz > coastHoriz + 0.01d) {
            session.rememberCoast(vel);
            debug(entity, session, "after boost horiz=" + fmt(currentHoriz));
            return;
        }

        session.coastX *= HORIZONTAL_KEEP;
        session.coastZ *= HORIZONTAL_KEEP;
        applyVelocity(entity, new Vec3(session.coastX, vel.y, session.coastZ), session.resolvedMaxSpeed, false);
        debug(entity, session, "after restore onGround=" + entity.onGround()
                + " vertCol=" + entity.verticalCollision
                + " stolen=" + (currentHoriz + 0.01d < coastHoriz));
    }

    public static void clampCurrentSpeed(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        applyVelocity(entity, entity.getDeltaMovement(), false);
    }

    public static void applyImpulse(LivingEntity entity, Vec3 impulse) {
        if (entity == null || impulse == null) {
            return;
        }
        Session session = sessionOf(entity);
        double maxSpeed = session != null ? session.resolvedMaxSpeed : DEFAULT_MAX_SPEED;
        applyVelocity(entity, entity.getDeltaMovement().add(impulse), maxSpeed, true);
    }

    public static void applyVelocity(LivingEntity entity, Vec3 velocity) {
        applyVelocity(entity, velocity, true);
    }

    public static void applyVelocity(LivingEntity entity, Vec3 velocity, boolean updateCoast) {
        Session session = sessionOf(entity);
        double maxSpeed = session != null ? session.resolvedMaxSpeed : DEFAULT_MAX_SPEED;
        applyVelocity(entity, velocity, maxSpeed, updateCoast);
    }

    public static void applyVelocity(LivingEntity entity, Vec3 velocity, double maxSpeed, boolean updateCoast) {
        Vec3 clamped = clampSpeed(velocity, maxSpeed);
        entity.setDeltaMovement(clamped);
        entity.hurtMarked = true;
        entity.setOnGround(false);
        entity.resetFallDistance();
        if (updateCoast) {
            Session session = sessionOf(entity);
            if (session != null) {
                session.rememberCoast(clamped);
            }
        }
    }

    /**
     * Caps total speed without converting horizontal coast into vertical launch.
     * If XZ already uses the cap, Y is cut first.
     */
    public static Vec3 clampSpeed(Vec3 velocity, double maxSpeed) {
        double cap = Math.max(0.0d, maxSpeed);
        if (cap <= 0.0d) {
            return Vec3.ZERO;
        }
        double horiz = horizontal(velocity);
        if (horiz >= cap) {
            double scale = cap / horiz;
            return new Vec3(velocity.x * scale, 0.0d, velocity.z * scale);
        }
        double maxY = Math.sqrt(cap * cap - horiz * horiz);
        double y = Math.max(-maxY, Math.min(maxY, velocity.y));
        return new Vec3(velocity.x, y, velocity.z);
    }

    public static void rememberLimits(LivingEntity entity, double maxHeight, double maxSpeed) {
        Session session = sessionOf(entity);
        if (session != null) {
            session.resolvedMaxHeight = maxHeight;
            session.resolvedMaxSpeed = maxSpeed;
        }
    }

    private static double sampleGroundCeiling(LivingEntity entity, double maxHeight, Session session) {
        double groundY = raycastGround(entity);
        double ceilingY = groundY + Math.max(0.0d, maxHeight);
        double overshoot = entity.getY() - ceilingY;
        if (session != null) {
            session.lastGroundY = groundY;
            session.lastCeilingY = ceilingY;
            session.lastOvershoot = overshoot;
        }
        return overshoot;
    }

    private static double raycastGround(LivingEntity entity) {
        double minY = entity.level().getMinY();
        Vec3 start = entity.position().add(0.0d, 0.05d, 0.0d);
        Vec3 end = new Vec3(start.x, minY, start.z);
        BlockHitResult hit = entity.level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation().y;
        }
        return minY;
    }

    /**
     * Jump self-ascent fades exponentially once above the ground-relative ceiling.
     * Vertical speed already above self-ascent (fireworks, zip, knockback) is left alone.
     */
    private static double adjustVertical(double y, Control control, double overshoot) {
        if (control.jump) {
            double factor = overshoot > 0.0d ? Math.exp(-overshoot / HEIGHT_FALLOFF) : 1.0d;
            double maxSelfY = VERTICAL_SELF_SPEED * factor;
            if (y < maxSelfY) {
                return y + VERTICAL_ACCEL * factor;
            }
            if (y <= VERTICAL_SELF_SPEED + 1.0e-4d) {
                return Math.min(y, maxSelfY);
            }
            return y;
        }
        if (control.sneak) {
            if (y > -VERTICAL_SELF_SPEED) {
                y -= VERTICAL_ACCEL;
            }
            return y;
        }
        if (y < 0.0d) {
            return Math.min(0.0d, y + FALL_DECEL);
        }
        return y * UPWARD_KEEP;
    }

    private static double horizontal(Vec3 vel) {
        return horizontal(vel.x, vel.z);
    }

    private static double horizontal(double x, double z) {
        return Math.sqrt(x * x + z * z);
    }

    public static ControlReader clientInput = entity -> Control.NONE;

    private static Control readControl(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return clientInput.read(entity);
        }
        if (entity instanceof ServerPlayer serverPlayer) {
            Input input = serverPlayer.getLastClientInput();
            return new Control(input.jump(), input.shift());
        }
        return new Control(false, entity.isShiftKeyDown());
    }

    private static void debug(LivingEntity entity, Session session, String phase) {
        if (entity == null || session == null || !isDebug(entity.getUUID())) {
            return;
        }
        boolean important = phase.startsWith("activate") || phase.startsWith("after wall") || phase.startsWith("after boost");
        if (!important && session.ticks % 20 != 0) {
            return;
        }
        Vec3 vel = entity.getDeltaMovement();
        String line = String.format(
                "Float %s side=%s vel=%.3f,%.3f,%.3f coast=%.3f,%.3f horiz=%.3f/%.3f ground=%.1f ceil=%.1f over=%.1f onG=%s hCol=%s vCol=%s ticks=%d",
                phase,
                entity.level().isClientSide() ? "C" : "S",
                vel.x, vel.y, vel.z,
                session.coastX, session.coastZ,
                horizontal(vel), horizontal(session.coastX, session.coastZ),
                session.lastGroundY, session.lastCeilingY, session.lastOvershoot,
                entity.onGround(), entity.horizontalCollision, entity.verticalCollision,
                session.ticks);
        YourHeroAcademia.LOGGER.info(line);
        if (entity instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal(line));
        }
    }

    private static String fmt(double value) {
        return String.format("%.3f", value);
    }

    @FunctionalInterface
    public interface ControlReader {
        Control read(LivingEntity entity);
    }

    public static final class Session {
        public final double activationY;
        public int ticks;
        public int lastPhysicsTick = Integer.MIN_VALUE;
        public double resolvedMaxHeight = DEFAULT_MAX_HEIGHT;
        public double resolvedMaxSpeed = DEFAULT_MAX_SPEED;
        public double lastGroundY;
        public double lastCeilingY;
        public double lastOvershoot;
        public double coastX;
        public double coastZ;

        private Session(double activationY) {
            this.activationY = activationY;
        }

        public void rememberCoast(Vec3 velocity) {
            if (velocity == null) {
                return;
            }
            this.coastX = velocity.x;
            this.coastZ = velocity.z;
        }
    }

    public record Control(boolean jump, boolean sneak) {
        public static final Control NONE = new Control(false, false);
    }
}
