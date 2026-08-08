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
 * Web Swing: always-on camera pivot (surface preferred, virtual fallback), client pendulum while
 * held, momentum release fling on let-go. Separate from look-raycast {@link BlackwhipChainSwingAbility}.
 */
public class BlackwhipWebSwingAbility extends Ability {

    private static final float MIN_ROPE = 3.0f;
    private static final float MAX_ROPE = 48.0f;
    private static final float DAMPING = 0.996f;
    private static final float BRAKE_DAMP = 0.88f;
    private static final float QF_PUMP_BONUS = 0.012f;
    private static final float QF_SPEED_BONUS = 0.14f;
    private static final float PERFECT_RELEASE_BONUS = 0.35f;
    private static final float THICKNESS = 0.85f;
    private static final int SEGMENT_COUNT = 10;
    private static final float LINK_LENGTH = 0.9f;
    private static final float BASE_CHAIN_HP = 24.0f;
    private static final float MAX_DISTANCE = 52.0f;
    private static final int RELEASE_ECHO_TICKS = 3;
    private static final int MAX_KEEP = 2;
    private static final double MAX_SYNC_SPEED = 6.0;

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

    // RecordCodecBuilder.group supports at most 16 fields (13 Values + properties/state/energy).
    public static final MapCodec<BlackwhipWebSwingAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(32.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("min_pivot_dist", new StaticValue(14.0f)).forGetter((ab) -> ab.minPivotDist),
                    Value.CODEC.optionalFieldOf("max_pivot_dist", new StaticValue(28.0f)).forGetter((ab) -> ab.maxPivotDist),
                    Value.CODEC.optionalFieldOf("elev_bias", new StaticValue(0.35f)).forGetter((ab) -> ab.elevBias),
                    Value.CODEC.optionalFieldOf("start_slack", new StaticValue(0.85f)).forGetter((ab) -> ab.startSlack),
                    Value.CODEC.optionalFieldOf("takeoff_boost", new StaticValue(0.72f)).forGetter((ab) -> ab.takeoffBoost),
                    Value.CODEC.optionalFieldOf("pump_accel", new StaticValue(0.078f)).forGetter((ab) -> ab.pumpAccel),
                    Value.CODEC.optionalFieldOf("turn_assist", new StaticValue(0.052f)).forGetter((ab) -> ab.turnAssist),
                    Value.CODEC.optionalFieldOf("auto_reel_rate", new StaticValue(0.1f)).forGetter((ab) -> ab.autoReelRate),
                    Value.CODEC.optionalFieldOf("max_speed", new StaticValue(3.5f)).forGetter((ab) -> ab.maxSpeed),
                    Value.CODEC.optionalFieldOf("release_forward", new StaticValue(0.55f)).forGetter((ab) -> ab.releaseForward),
                    Value.CODEC.optionalFieldOf("release_up", new StaticValue(0.62f)).forGetter((ab) -> ab.releaseUp),
                    Value.CODEC.optionalFieldOf("release_speed_scale", new StaticValue(0.32f)).forGetter((ab) -> ab.releaseSpeedScale),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipWebSwingAbility::new));

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

    public BlackwhipWebSwingAbility(
            Value range, Value minPivotDist, Value maxPivotDist, Value elevBias,
            Value startSlack, Value takeoffBoost, Value pumpAccel, Value turnAssist,
            Value autoReelRate, Value maxSpeed, Value releaseForward, Value releaseUp,
            Value releaseSpeedScale,
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
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        DataContext context = DataContext.forEntity(entity);
        stopSwing(player, level, false);

        BlackwhipChainSwingAbility.forceStop(player);
        BlackwhipChainZipAbility.forceStop(player);
        BlackwhipChainChargeZipAbility.forceStop(player);
        BlackwhipChainEntity.retractOwnedByPurpose(player.getId(),
                BlackwhipChainEntity.PURPOSE_SWING,
                BlackwhipChainEntity.PURPOSE_WEB_SWING,
                BlackwhipChainEntity.PURPOSE_ZIP_SIMPLE,
                BlackwhipChainEntity.PURPOSE_ZIP_CHARGE);

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
            Vec3 launch = flat.scale(0.55).add(0.0, Math.max(0.55, hop), 0.0);
            if (toPivot.lengthSqr() > 1.0e-4) {
                launch = launch.add(toPivot.normalize().scale(0.35));
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
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        SwingSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            PacketDistributor.sendToPlayer(player, BlackwhipWebSwingPayload.stop());
            return;
        }
        Vec3 fling = computeReleaseFling(player, session);
        applyFling(player, fling);
        RELEASE_ECHOES.put(player.getUUID(), new ReleaseEcho(fling, RELEASE_ECHO_TICKS));
        stopSwing(player, level, true);
        level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.55f, 1.55f);
    }

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

        // Launch along swing tangent (momentum), falling back to look-forward-up off the rope.
        Vec3 tangential = velocity.subtract(radial.scale(velocity.dot(radial)));
        double tSpeed = tangential.length();
        Vec3 launchDir;
        if (tSpeed > 0.12) {
            launchDir = tangential.scale(1.0 / tSpeed);
        } else {
            Vec3 wish = flatLook.add(0.0, 0.55, 0.0);
            Vec3 projected = wish.subtract(radial.scale(wish.dot(radial)));
            launchDir = projected.lengthSqr() > 1.0e-4 ? projected.normalize() : flatLook.add(0.0, 0.4, 0.0).normalize();
        }

        // If the tangent is aiming downward, flip/bias it into a forward-up exit.
        if (launchDir.y < 0.12) {
            launchDir = new Vec3(launchDir.x, Math.max(0.28, -launchDir.y * 0.35 + 0.28), launchDir.z);
            // Keep it aligned with look when possible so you fling where you're swinging/facing.
            launchDir = launchDir.add(flatLook.scale(0.45)).add(0.0, 0.2, 0.0);
            if (launchDir.lengthSqr() > 1.0e-6) {
                launchDir = launchDir.normalize();
            }
        }

        double speed = Math.max(velocity.length(), tSpeed);
        double boost = session.releaseForward + speed * session.releaseSpeedScale * 1.35;
        // Carry most of the swing speed along the launch dir, then add the release kick.
        double carry = Math.max(speed, 0.55);
        Vec3 fling = launchDir.scale(carry + boost);

        double up = session.releaseUp + speed * session.releaseSpeedScale * 0.85;
        if (velocity.y > 0.0) {
            up += Math.min(0.45, velocity.y * 0.65);
        }
        // Perfect window: mid arc, moving up / out.
        double elev = radial.y;
        boolean inWindow = elev > 0.12 && elev < 0.82 && launchDir.y > 0.05 && speed > 0.35;
        if (inWindow) {
            up += PERFECT_RELEASE_BONUS;
            fling = fling.add(launchDir.scale(PERFECT_RELEASE_BONUS * 0.65));
        }

        fling = new Vec3(fling.x, Math.max(fling.y, up * 0.85) + up * 0.35, fling.z);
        // Never release into a downward pop — that was the "drop" feel.
        if (fling.y < session.releaseUp * 0.75) {
            fling = new Vec3(fling.x, session.releaseUp * 0.75, fling.z);
        }
        return fling;
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
        SwingSession session = SESSIONS.remove(player.getUUID());
        if (session != null && level.getEntity(session.chainId) instanceof BlackwhipChainEntity chain) {
            chain.deactivate();
        } else {
            BlackwhipChainEntity.retractOwnedByPurpose(player.getId(), BlackwhipChainEntity.PURPOSE_WEB_SWING);
        }
        PacketDistributor.sendToPlayer(player, BlackwhipWebSwingPayload.stop());
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
            builder.setDescription("PS5-style web swing. Always attaches (surface preferred, virtual air pivot fallback). Hold to swing with WASD/camera steering; Shift brakes; release flings up and forward with momentum.")
                    .add("range", TYPE_VALUE, "Cone / virtual pivot reach.")
                    .add("min_pivot_dist", TYPE_VALUE, "Minimum virtual pivot distance.")
                    .add("max_pivot_dist", TYPE_VALUE, "Maximum virtual pivot distance.")
                    .add("elev_bias", TYPE_VALUE, "Upward bias when placing virtual pivots.")
                    .add("start_slack", TYPE_VALUE, "Extra initial rope length so the first frames drop into the arc.")
                    .add("takeoff_boost", TYPE_VALUE, "Upward hop when attaching from the ground.")
                    .add("pump_accel", TYPE_VALUE, "Tangential accel from movement keys.")
                    .add("turn_assist", TYPE_VALUE, "Lateral lean when turning the camera.")
                    .add("auto_reel_rate", TYPE_VALUE, "How quickly rope shortens with speed.")
                    .add("max_speed", TYPE_VALUE, "Soft swing speed cap before quirk scaling.")
                    .add("release_forward", TYPE_VALUE, "Base forward kick on release.")
                    .add("release_up", TYPE_VALUE, "Base upward kick on release.")
                    .add("release_speed_scale", TYPE_VALUE, "Extra release kick scaled by swing speed.")
                    .addExampleObject(new BlackwhipWebSwingAbility(
                            new StaticValue(32.0f), new StaticValue(14.0f), new StaticValue(28.0f), new StaticValue(0.4f),
                            new StaticValue(0.85f), new StaticValue(0.72f), new StaticValue(0.078f), new StaticValue(0.052f),
                            new StaticValue(0.1f), new StaticValue(3.5f), new StaticValue(0.55f), new StaticValue(0.62f),
                            new StaticValue(0.32f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
