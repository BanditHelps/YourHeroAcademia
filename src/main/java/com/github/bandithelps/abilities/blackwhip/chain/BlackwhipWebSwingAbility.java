package com.github.bandithelps.abilities.blackwhip.chain;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.network.BlackwhipWebSwingPayload;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainHelper;
import com.github.bandithelps.utils.blackwhip.BlackwhipWebSwingPivots;
import com.github.bandithelps.utils.quirk.QuirkFactorUtil;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.logic.value.StaticValue;
import net.threetag.palladium.logic.value.Value;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web Swing: Palladium-style forward-up fan attach (real block preferred, ideal air pivot fallback),
 * client pendulum while held, momentum release fling on let-go. Separate from look-raycast
 * {@link BlackwhipSwingAbility}.
 */
public class BlackwhipWebSwingAbility extends Ability {

    private static final float MIN_ROPE = 3.0f;
    private static final float MAX_ROPE = 48.0f;
    private static final float DAMPING = 0.996f;
    private static final float BRAKE_DAMP = 0.88f;
    private static final float QF_PUMP_BONUS = 0.012f;
    private static final float QF_SPEED_BONUS = 0.14f;
    private static final float PERFECT_RELEASE_BONUS = 0.28f;
    private static final float THICKNESS = 0.85f;
    private static final int SEGMENT_COUNT = 10;
    private static final float LINK_LENGTH = 0.9f;
    private static final float BASE_CHAIN_HP = 24.0f;
    private static final float MAX_DISTANCE = 52.0f;
    private static final int RELEASE_ECHO_TICKS = 3;
    private static final int TIMEOUT_ECHO_TICKS = 1;
    private static final int MAX_KEEP = 2;
    private static final double MAX_SYNC_SPEED = 6.0;
    /** Horizontal scale applied to residual velocity when the whip times out. */
    private static final double TIMEOUT_HORIZONTAL_SCALE = 0.12;
    /** Vertical scale / clamp for timeout residual. */
    private static final double TIMEOUT_VERTICAL_SCALE = 0.08;
    private static final double TIMEOUT_MAX_UP = 0.05;

    private static final Map<UUID, SwingSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, ReleaseEcho> RELEASE_ECHOES = new ConcurrentHashMap<>();

    private static final class SwingSession {
        final int chainId;
        final Vec3 anchor;
        final float ropeLength;
        final float releaseForward;
        final float releaseUp;
        final float releaseSpeedScale;
        volatile Vec3 clientVelocity = Vec3.ZERO;

        SwingSession(int chainId, Vec3 anchor, float ropeLength,
                     float releaseForward, float releaseUp, float releaseSpeedScale) {
            this.chainId = chainId;
            this.anchor = anchor;
            this.ropeLength = ropeLength;
            this.releaseForward = releaseForward;
            this.releaseUp = releaseUp;
            this.releaseSpeedScale = releaseSpeedScale;
        }
    }

    private record ReleaseEcho(Vec3 velocity, int ticksLeft) {
    }

    /**
     * Nested so the top-level ability codec stays within RecordCodecBuilder's 16-field limit
     * while still accepting flat JSON keys (release_* + max_ground_height).
     */
    private record SwingTuning(Value releaseForward, Value releaseUp, Value releaseSpeedScale, Value maxGroundHeight) {
        static final MapCodec<SwingTuning> CODEC = RecordCodecBuilder.mapCodec((instance) ->
                instance.group(
                        Value.CODEC.optionalFieldOf("release_forward", new StaticValue(0.58f)).forGetter(SwingTuning::releaseForward),
                        Value.CODEC.optionalFieldOf("release_up", new StaticValue(0.48f)).forGetter(SwingTuning::releaseUp),
                        Value.CODEC.optionalFieldOf("release_speed_scale", new StaticValue(0.3f)).forGetter(SwingTuning::releaseSpeedScale),
                        Value.CODEC.optionalFieldOf("max_ground_height", new StaticValue(12.0f)).forGetter(SwingTuning::maxGroundHeight)
                ).apply(instance, SwingTuning::new));
    }

    public static final MapCodec<BlackwhipWebSwingAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(32.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("min_pivot_dist", new StaticValue(14.0f)).forGetter((ab) -> ab.minPivotDist),
                    Value.CODEC.optionalFieldOf("max_pivot_dist", new StaticValue(28.0f)).forGetter((ab) -> ab.maxPivotDist),
                    Value.CODEC.optionalFieldOf("elev_bias", new StaticValue(0.28f)).forGetter((ab) -> ab.elevBias),
                    Value.CODEC.optionalFieldOf("start_slack", new StaticValue(0.85f)).forGetter((ab) -> ab.startSlack),
                    Value.CODEC.optionalFieldOf("takeoff_boost", new StaticValue(0.48f)).forGetter((ab) -> ab.takeoffBoost),
                    Value.CODEC.optionalFieldOf("pump_accel", new StaticValue(0.09f)).forGetter((ab) -> ab.pumpAccel),
                    Value.CODEC.optionalFieldOf("turn_assist", new StaticValue(0.058f)).forGetter((ab) -> ab.turnAssist),
                    Value.CODEC.optionalFieldOf("auto_reel_rate", new StaticValue(0.1f)).forGetter((ab) -> ab.autoReelRate),
                    Value.CODEC.optionalFieldOf("max_speed", new StaticValue(3.8f)).forGetter((ab) -> ab.maxSpeed),
                    SwingTuning.CODEC.forGetter((ab) -> new SwingTuning(
                            ab.releaseForward, ab.releaseUp, ab.releaseSpeedScale, ab.maxGroundHeight)),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()
            ).apply(instance, (range, minPivotDist, maxPivotDist, elevBias, startSlack, takeoffBoost,
                               pumpAccel, turnAssist, autoReelRate, maxSpeed, tuning,
                               properties, state, energyBarUsages) ->
                    new BlackwhipWebSwingAbility(
                            range, minPivotDist, maxPivotDist, elevBias, startSlack, takeoffBoost,
                            pumpAccel, turnAssist, autoReelRate, maxSpeed,
                            tuning.releaseForward(), tuning.releaseUp(), tuning.releaseSpeedScale(),
                            tuning.maxGroundHeight(), properties, state, energyBarUsages)));

    public final Value range;
    public final Value minPivotDist;
    public final Value maxPivotDist;
    public final Value elevBias;
    public final Value startSlack;
    public final Value takeoffBoost;
    public final Value pumpAccel;
    public final Value turnAssist;
    public final Value autoReelRate;
    public final Value maxSpeed;
    public final Value releaseForward;
    public final Value releaseUp;
    public final Value releaseSpeedScale;
    public final Value maxGroundHeight;

    public BlackwhipWebSwingAbility(
            Value range, Value minPivotDist, Value maxPivotDist, Value elevBias,
            Value startSlack, Value takeoffBoost, Value pumpAccel, Value turnAssist,
            Value autoReelRate, Value maxSpeed, Value releaseForward, Value releaseUp,
            Value releaseSpeedScale, Value maxGroundHeight,
            AbilityProperties properties, AbilityStateManager conditions,
            List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.minPivotDist = minPivotDist;
        this.maxPivotDist = maxPivotDist;
        this.elevBias = elevBias;
        this.startSlack = startSlack;
        this.takeoffBoost = takeoffBoost;
        this.pumpAccel = pumpAccel;
        this.turnAssist = turnAssist;
        this.autoReelRate = autoReelRate;
        this.maxSpeed = maxSpeed;
        this.releaseForward = releaseForward;
        this.releaseUp = releaseUp;
        this.releaseSpeedScale = releaseSpeedScale;
        this.maxGroundHeight = maxGroundHeight;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        DataContext context = DataContext.forEntity(entity);
        stopSwing(player, level, false);

        BlackwhipSwingAbility.forceStop(player);
        BlackwhipZipAbility.forceStop(player);
        BlackwhipChargeZipAbility.forceStop(player);
        BlackwhipChainEntity.retractOwnedByPurpose(player.getId(),
                BlackwhipChainEntity.PURPOSE_SWING,
                BlackwhipChainEntity.PURPOSE_WEB_SWING,
                BlackwhipChainEntity.PURPOSE_ZIP_SIMPLE,
                BlackwhipChainEntity.PURPOSE_ZIP_CHARGE);

        float maxProximity = Math.max(0.5f, this.maxGroundHeight.getAsFloat(context));
        if (!isNearSwingSupport(player, level, maxProximity)) {
            level.playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.45f, 0.55f);
            return;
        }

        double range = this.range.getAsFloat(context);
        BlackwhipWebSwingPivots.Pivot pivot = BlackwhipWebSwingPivots.resolve(
                level, player, range,
                this.minPivotDist.getAsFloat(context),
                this.maxPivotDist.getAsFloat(context),
                this.elevBias.getAsFloat(context));

        double qf = QuirkFactorUtil.getQuirkFactor(player);
        float chainHp = BASE_CHAIN_HP + (float) (qf * 2.0);

        BlackwhipChainEntity chain;
        if (pivot.virtual()) {
            chain = BlackwhipChainHelper.spawnVirtualAnchoredChain(
                    player, pivot.point(), BlackwhipChainEntity.PURPOSE_WEB_SWING,
                    SEGMENT_COUNT, LINK_LENGTH, chainHp, THICKNESS, MAX_DISTANCE, MAX_KEEP, 0);
        } else {
            chain = BlackwhipChainHelper.spawnAnchoredChain(
                    player, pivot.point(), pivot.support(), BlackwhipChainEntity.PURPOSE_WEB_SWING,
                    SEGMENT_COUNT, LINK_LENGTH, chainHp, THICKNESS, MAX_DISTANCE, MAX_KEEP, 0);
        }
        if (chain == null) {
            return;
        }

        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        float slack = Math.max(0.0f, this.startSlack.getAsFloat(context));
        // On ground, keep the rope tighter so the first taut frame pulls you up into the arc.
        if (player.onGround()) {
            slack = Math.min(slack, 0.35f);
        }
        float ropeLength = Mth.clamp((float) center.distanceTo(pivot.point()) + slack, MIN_ROPE, MAX_ROPE);

        float pump = this.pumpAccel.getAsFloat(context) + (float) (qf * QF_PUMP_BONUS);
        float turn = this.turnAssist.getAsFloat(context);
        float autoReel = this.autoReelRate.getAsFloat(context);
        float speedCap = this.maxSpeed.getAsFloat(context) * (1.0f + (float) (qf * QF_SPEED_BONUS));

        if (player.onGround()) {
            float hop = this.takeoffBoost.getAsFloat(context);
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0.0, look.z);
            if (flat.lengthSqr() > 1.0e-4) {
                flat = flat.normalize();
            } else {
                flat = new Vec3(0.0, 0.0, 1.0);
            }
            Vec3 toPivot = pivot.point().subtract(center);
            Vec3 launch = flat.scale(0.85).add(0.0, Math.max(0.38, hop * 0.7), 0.0);
            if (toPivot.lengthSqr() > 1.0e-4) {
                Vec3 toward = toPivot.normalize();
                launch = launch.add(toward.x * 0.28, toward.y * 0.18, toward.z * 0.28);
            }
            player.setDeltaMovement(launch);
            player.hurtMarked = true;
            player.setOnGround(false);
            player.resetFallDistance();
        }

        SESSIONS.put(player.getUUID(), new SwingSession(
                chain.getId(),
                pivot.point(),
                ropeLength,
                this.releaseForward.getAsFloat(context),
                this.releaseUp.getAsFloat(context),
                this.releaseSpeedScale.getAsFloat(context)));

        PacketDistributor.sendToPlayer(player, new BlackwhipWebSwingPayload(
                true,
                pivot.point().x, pivot.point().y, pivot.point().z,
                ropeLength,
                MIN_ROPE,
                MAX_ROPE,
                pump,
                turn,
                autoReel,
                DAMPING,
                speedCap,
                BRAKE_DAMP));

        level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.5f, 1.45f);
    }

    /**
     * True when the player is on the ground, or solid terrain is within {@code maxDist} blocks
     * either below the feet or beside the body (cardinals + diagonals). Lets you start a swing
     * next to a cliff/building even when open air is below.
     */
    private static boolean isNearSwingSupport(ServerPlayer player, ServerLevel level, float maxDist) {
        if (player.onGround()) {
            return true;
        }
        Vec3 feet = player.position();
        if (hasSolidWithin(level, player, feet, feet.subtract(0.0, maxDist, 0.0), maxDist)) {
            return true;
        }

        Vec3 mid = feet.add(0.0, player.getBbHeight() * 0.55, 0.0);
        // Horizontal probes — enough coverage for walls/mountain sides without a dense sphere.
        double inv = 0.70710678118;
        double[][] horiz = {
                {1.0, 0.0}, {-1.0, 0.0}, {0.0, 1.0}, {0.0, -1.0},
                {inv, inv}, {inv, -inv}, {-inv, inv}, {-inv, -inv}
        };
        for (double[] d : horiz) {
            Vec3 end = mid.add(d[0] * maxDist, 0.0, d[1] * maxDist);
            if (hasSolidWithin(level, player, mid, end, maxDist)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSolidWithin(ServerLevel level, ServerPlayer player,
                                          Vec3 from, Vec3 to, float maxDist) {
        BlockHitResult hit = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        return from.distanceTo(hit.getLocation()) <= maxDist + 1.0e-3;
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            SwingSession session = SESSIONS.get(player.getUUID());
            if (session == null) {
                return super.tick(entity, abilityInstance, enabled);
            }
            if (!(level.getEntity(session.chainId) instanceof BlackwhipChainEntity chain) || !chain.isAnchored()) {
                stopSwing(player, level, false);
                return super.tick(entity, abilityInstance, enabled);
            }
            // Client owns pendulum; server only verifies the chain is alive.
        }
        return super.tick(entity, abilityInstance, enabled);
    }

    /** Stores the owning client's swing velocity for accurate release flings. */
    public static void acceptClientVelocity(ServerPlayer player, Vec3 velocity) {
        SwingSession session = SESSIONS.get(player.getUUID());
        if (session == null || velocity == null) {
            return;
        }
        double speed = velocity.length();
        if (speed > MAX_SYNC_SPEED && speed > 1.0e-6) {
            velocity = velocity.scale(MAX_SYNC_SPEED / speed);
        }
        session.clientVelocity = velocity;
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        releaseSwing(player, false);
    }

    /**
     * Client-driven whip snap. Apex keeps the full release fling; timeout is nearly inert.
     * Safe if the session is already gone (e.g. player already let go).
     */
    public static void breakSwing(ServerPlayer player, boolean timedOut) {
        releaseSwing(player, timedOut);
    }

    /** @deprecated use {@link #breakSwing(ServerPlayer, boolean)} */
    @Deprecated
    public static void breakAtMaxArc(ServerPlayer player) {
        breakSwing(player, false);
    }

    private static void releaseSwing(ServerPlayer player, boolean timedOut) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        SwingSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            PacketDistributor.sendToPlayer(player, BlackwhipWebSwingPayload.stop());
            return;
        }
        Vec3 fling = timedOut
                ? computeTimeoutResidual(player, session)
                : computeReleaseFling(player, session);
        applyFling(player, fling);
        RELEASE_ECHOES.put(player.getUUID(), new ReleaseEcho(
                fling, timedOut ? TIMEOUT_ECHO_TICKS : RELEASE_ECHO_TICKS));
        stopSwingStatic(player, level, true);
        level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS,
                timedOut ? 0.4f : 0.55f, timedOut ? 0.85f : 1.55f);
    }

    /** Negligible leftover motion when the player holds past the hard swing timeout. */
    private static Vec3 computeTimeoutResidual(ServerPlayer player, SwingSession session) {
        Vec3 velocity = session.clientVelocity;
        if (velocity.lengthSqr() < 0.04) {
            velocity = player.getDeltaMovement();
        }
        double y = Math.min(velocity.y * TIMEOUT_VERTICAL_SCALE, TIMEOUT_MAX_UP);
        if (y > 0.0) {
            y = Math.min(y, TIMEOUT_MAX_UP);
        }
        return new Vec3(
                velocity.x * TIMEOUT_HORIZONTAL_SCALE,
                y,
                velocity.z * TIMEOUT_HORIZONTAL_SCALE);
    }

    /**
     * Release continues the swing as a smooth arc (always both forward and up).
     * Arc height only gently tilts the launch pitch and power — never a single-axis dump.
     */
    private static Vec3 computeReleaseFling(ServerPlayer player, SwingSession session) {
        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3 toAnchor = session.anchor.subtract(center);
        double dist = toAnchor.length();
        Vec3 radial = dist > 1.0e-4 ? toAnchor.scale(1.0 / dist) : new Vec3(0.0, 1.0, 0.0);

        // Prefer client pendulum velocity — server movement is stale during client-owned swing.
        Vec3 velocity = session.clientVelocity;
        if (velocity.lengthSqr() < 0.04) {
            velocity = player.getDeltaMovement();
        }

        Vec3 look = player.getLookAngle();
        Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
        if (flatLook.lengthSqr() < 1.0e-4) {
            flatLook = new Vec3(0.0, 0.0, 1.0);
        } else {
            flatLook = flatLook.normalize();
        }

        // Keep the true swing tangent (has both horizontal and vertical) as the arc direction.
        Vec3 tangential = velocity.subtract(radial.scale(velocity.dot(radial)));
        double tSpeed = tangential.length();
        Vec3 arcDir;
        if (tSpeed > 0.1) {
            arcDir = tangential.scale(1.0 / tSpeed);
        } else {
            // Idle fallback: forward-up diagonal off the rope.
            Vec3 wish = flatLook.add(0.0, 0.45, 0.0);
            Vec3 projected = wish.subtract(radial.scale(wish.dot(radial)));
            arcDir = projected.lengthSqr() > 1.0e-4
                    ? projected.normalize()
                    : flatLook.add(0.0, 0.4, 0.0).normalize();
        }

        // Face the launch the way the player is swinging/looking.
        Vec3 flatArc = new Vec3(arcDir.x, 0.0, arcDir.z);
        if (flatArc.lengthSqr() > 1.0e-4 && flatArc.normalize().dot(flatLook) < 0.0) {
            arcDir = new Vec3(-arcDir.x, arcDir.y, -arcDir.z);
            flatArc = new Vec3(arcDir.x, 0.0, arcDir.z);
        }
        if (flatArc.lengthSqr() < 1.0e-4) {
            flatArc = flatLook;
        } else {
            flatArc = flatArc.normalize();
        }

        // 0 = deep under pivot, 1 = near top of arc.
        double below = Math.max(0.0, session.anchor.y - center.y);
        double maxBelow = Math.max(session.ropeLength * 0.95, 1.0);
        double heightAlongArc = 1.0 - Mth.clamp(below / maxBelow, 0.0, 1.0);

        // Soft pitch target for a continuous arc: flatter low, loftier high — always both axes.
        double targetPitchDeg = Mth.lerp(heightAlongArc, 22.0, 40.0);
        if (velocity.y < -0.05) {
            // Descending: keep some loft so release still arcs instead of slamming down.
            targetPitchDeg = Mth.lerp(0.45, targetPitchDeg, 26.0);
        } else if (velocity.y > 0.05) {
            targetPitchDeg += Mth.clamp(velocity.y * 8.0, 0.0, 6.0);
        }
        targetPitchDeg = Mth.clamp(targetPitchDeg, 18.0, 44.0);

        double targetPitchRad = Math.toRadians(targetPitchDeg);
        Vec3 pitchedArc = new Vec3(
                flatArc.x * Math.cos(targetPitchRad),
                Math.sin(targetPitchRad),
                flatArc.z * Math.cos(targetPitchRad));
        // Blend real swing tangent with the smooth target pitch so momentum still reads.
        arcDir = arcDir.scale(0.55).add(pitchedArc.scale(0.45)).add(flatLook.scale(0.12));
        // Nudge upward if the tangent was diving.
        if (arcDir.y < 0.16) {
            arcDir = new Vec3(arcDir.x, 0.16 + heightAlongArc * 0.12, arcDir.z);
        }
        arcDir = arcDir.normalize();

        double speed = Math.max(velocity.length(), tSpeed);
        // Mild power curve: mid-rising releases feel best, edges still get a full arc kick.
        double rising = Mth.clamp(velocity.y / 0.7, 0.0, 1.0);
        double sweet = Math.exp(-Math.pow((heightAlongArc - 0.42) / 0.28, 2.0)) * (0.35 + 0.65 * rising);
        double power = 0.88 + sweet * 0.28;
        if (velocity.y < -0.08) {
            power *= 0.82;
        }

        double carry = Math.max(speed, 0.65) * power;
        double boost = (session.releaseForward * 0.7 + session.releaseUp * 0.55
                + speed * session.releaseSpeedScale) * power;
        boost += PERFECT_RELEASE_BONUS * sweet * 0.55;

        // Single arc vector — horizontal and vertical stay coupled.
        Vec3 fling = arcDir.scale(carry + boost);
        // Tiny look assist so steering still matters, without flattening the arc.
        fling = fling.add(flatLook.scale(session.releaseForward * 0.18 * power));

        // Keep Y in a gentle band so every release still arcs.
        double minY = session.releaseUp * (0.4 + heightAlongArc * 0.25);
        double maxY = session.releaseUp * (1.35 + heightAlongArc * 0.55) + speed * 0.2;
        double y = Mth.clamp(fling.y, minY, maxY);
        return new Vec3(fling.x, y, fling.z);
    }

    private static void applyFling(ServerPlayer player, Vec3 fling) {
        player.setDeltaMovement(fling);
        player.hurtMarked = true;
        player.setOnGround(false);
        player.resetFallDistance();
    }

    /**
     * Applies queued release-fling echoes so client move packets do not cancel the pop.
     * Called once per server tick from {@link com.github.bandithelps.utils.blackwhip.BlackwhipServerEvents}.
     */
    public static void tickReleaseEchoes(MinecraftServer server) {
        if (RELEASE_ECHOES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, ReleaseEcho>> it = RELEASE_ECHOES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ReleaseEcho> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            ReleaseEcho echo = entry.getValue();
            applyFling(player, echo.velocity());
            int left = echo.ticksLeft() - 1;
            if (left <= 0) {
                it.remove();
            } else {
                entry.setValue(new ReleaseEcho(echo.velocity(), left));
            }
        }
    }

    private void stopSwing(ServerPlayer player, ServerLevel level, boolean playBreak) {
        stopSwingStatic(player, level, playBreak);
    }

    private static void stopSwingStatic(ServerPlayer player, ServerLevel level, boolean playBreak) {
        SwingSession session = SESSIONS.remove(player.getUUID());
        if (session != null && level.getEntity(session.chainId) instanceof BlackwhipChainEntity chain) {
            chain.deactivate();
            if (playBreak) {
                level.playSound(null, player.blockPosition(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 0.55f, 1.25f);
            }
        } else {
            BlackwhipChainEntity.retractOwnedByPurpose(player.getId(), BlackwhipChainEntity.PURPOSE_WEB_SWING);
        }
        PacketDistributor.sendToPlayer(player, BlackwhipWebSwingPayload.stop());
    }

    /** Clears any active web swing for mutex with whip swing / zip / detach. */
    public static void forceStop(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        RELEASE_ECHOES.remove(player.getUUID());
        stopSwingStatic(player, level, false);
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_WEB_SWING.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipWebSwingAbility> {
        public MapCodec<BlackwhipWebSwingAbility> codec() {
            return BlackwhipWebSwingAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipWebSwingAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("PS5-style web swing. Starts when solid terrain is nearby below or beside you (max_ground_height). Palladium-style elevation/yaw fan prefers a real block near the ideal pivot, else latches a virtual air point. Hold to swing with WASD/camera steering; Shift brakes; release flings up and forward with momentum. Holding past the timeout snaps with negligible launch.")
                    .add("range", TYPE_VALUE, "Ray / virtual pivot reach.")
                    .add("min_pivot_dist", TYPE_VALUE, "Minimum ideal pivot distance.")
                    .add("max_pivot_dist", TYPE_VALUE, "Maximum ideal pivot distance.")
                    .add("elev_bias", TYPE_VALUE, "Extra steepness when placing the ideal pivot.")
                    .add("start_slack", TYPE_VALUE, "Extra initial rope length so the first frames drop into the arc.")
                    .add("takeoff_boost", TYPE_VALUE, "Upward hop when attaching from the ground.")
                    .add("pump_accel", TYPE_VALUE, "Tangential accel from movement keys.")
                    .add("turn_assist", TYPE_VALUE, "Lateral lean when turning the camera.")
                    .add("auto_reel_rate", TYPE_VALUE, "How quickly rope shortens with speed.")
                    .add("max_speed", TYPE_VALUE, "Soft swing speed cap before quirk scaling.")
                    .add("release_forward", TYPE_VALUE, "Base forward kick on release.")
                    .add("release_up", TYPE_VALUE, "Base upward kick on release.")
                    .add("release_speed_scale", TYPE_VALUE, "Extra release kick scaled by swing speed.")
                    .add("max_ground_height", TYPE_VALUE, "Max blocks to solid support below or beside the player required to start a swing.")
                    .addExampleObject(new BlackwhipWebSwingAbility(
                            new StaticValue(32.0f), new StaticValue(14.0f), new StaticValue(28.0f), new StaticValue(0.4f),
                            new StaticValue(0.85f), new StaticValue(0.72f), new StaticValue(0.078f), new StaticValue(0.052f),
                            new StaticValue(0.1f), new StaticValue(3.5f), new StaticValue(0.55f), new StaticValue(0.62f),
                            new StaticValue(0.32f), new StaticValue(12.0f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
