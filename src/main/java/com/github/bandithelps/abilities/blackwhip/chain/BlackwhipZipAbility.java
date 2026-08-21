package com.github.bandithelps.abilities.blackwhip.chain;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.network.BlackwhipChainZipAnimPayload;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainAnchors;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainHelper;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainTagStore;
import com.github.bandithelps.utils.blackwhip.BlackwhipTargeting;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.logic.value.StaticValue;
import net.threetag.palladium.logic.value.Value;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.neoforged.neoforge.network.PacketDistributor;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whip Zip: look-targeted directional burst toward a block, or a held reel into a living entity
 * (speeds up while held, ends on release or contact) with damage + knockback.
 * <p>
 * A missed look-ray auto-latches a solid block in a forward cone, preferring whatever is
 * closest to look-center; open air / void does nothing. Entity reels only start when
 * {@code damage} is greater than zero, and slam damage scales linearly with latch distance
 * versus {@code range}.
 * <p>
 * If the target is already chain-tagged, no new whip is spawned — player and mob are pulled
 * together along the existing tether for the slam, and the tag is left intact.
 * <p>
 * Block-burst echoes and entity reels are driven from {@link #tickSessions}. Entity reels also end
 * from {@link #lastTick} when the held key is released.
 */
public class BlackwhipZipAbility extends Ability {

    private static final float THICKNESS = 0.9f;
    private static final float LINK_LENGTH = 0.85f;
    private static final float CHAIN_HP = 18.0f;
    private static final int SEGMENT_COUNT = 8;
    private static final int MAX_KEEP = 8;
    /** Brief echoes so client move packets don't cancel a block burst. */
    private static final int BURST_ECHO_TICKS = 2;
    /** Safety cap so a stuck reel cannot run forever if lastTick never fires. */
    private static final int MAX_REEL_TICKS = 80;
    /** Ticks to ease from start speed up to peak reel speed. */
    private static final int REEL_RAMP_TICKS = 10;
    /** Starting pull speed toward a living target (blocks/tick). */
    private static final float REEL_SPEED_START = 0.7f;
    /** Peak pull speed after ramping (still braked near contact). */
    private static final float REEL_SPEED_PEAK = 1.85f;
    /** Extra closing-rate multiplier when yanking an already-tagged mob. */
    private static final float TAGGED_CLOSE_MULT = 1.45f;
    /** Stop and settle once within this distance of the target. */
    private static final float CONTACT_DIST = 1.25f;
    /** Tagged tugs settle even closer so the kick connects. */
    private static final float TAGGED_CONTACT_DIST = 0.95f;
    /** Begin braking inside this range so you don't fly through. */
    private static final float BRAKE_DIST = 4.0f;
    private static final float TAGGED_BRAKE_DIST = 2.5f;
    private static final float SWEEP_RADIUS = 0.75f;
    /** Residual speed left after a successful slam (kills orbit/overshoot). */
    private static final float HIT_STOP_SCALE = 0.08f;
    /** Knockback strength applied to the mob on slam. */
    private static final float HIT_KNOCKBACK = 0.55f;
    /** Soft total-speed ceiling that grows gently with quirk factor. */
    private static final float SPEED_CAP_BASE = 3.5f;
    private static final float SPEED_CAP_PER_QF = 0.12f;
    /** Hard upward clamp so ceiling zips stay snappy, not runaway. */
    private static final float MAX_UP_BASE = 2.35f;
    private static final float MAX_UP_PER_QF = 0.08f;
    /** Tiny loft only when the burst is nearly flat. */
    private static final float FLAT_UP_BIAS = 0.12f;
    /** Forward-cone half-angle used when the look-ray misses a block. */
    private static final double AUTO_ATTACH_HALF_ANGLE = 28.0;
    private static final int AUTO_ATTACH_RINGS = 3;
    private static final int AUTO_ATTACH_RAYS_PER_RING = 8;

    private enum ZipMode {
        BLOCK_BURST,
        ENTITY_REEL
    }

    private static final Map<UUID, ZipSession> SESSIONS = new ConcurrentHashMap<>();

    private static final class ZipSession {
        ZipMode mode = ZipMode.BLOCK_BURST;
        Vec3 burstVelocity = Vec3.ZERO;
        int echoTicksLeft;
        /** Age of the entity reel in ticks (drives acceleration). */
        int reelAge;
        /** Hard safety timeout; normal end is release or contact. */
        int reelTicksLeft;
        int chainId = -1;
        int targetId = -1;
        float damage;
        float reelSpeedPeak;
        /**
         * True when reeling an already-tagged mob: no new chain, mutual pull, never tear the tag.
         */
        boolean reuseTag;
        final Set<Integer> damagedIds = new HashSet<>();
    }

    public static final MapCodec<BlackwhipZipAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(22.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("simple_pull_power", new StaticValue(2.2f)).forGetter((ab) -> ab.simplePullPower),
                    Value.CODEC.optionalFieldOf("qf_pull_bonus", new StaticValue(0.04f)).forGetter((ab) -> ab.qfPullBonus),
                    Value.CODEC.optionalFieldOf("simple_visual_ticks", new StaticValue(10.0f)).forGetter((ab) -> ab.simpleVisualTicks),
                    Value.CODEC.optionalFieldOf("damage", new StaticValue(4.0f)).forGetter((ab) -> ab.damage),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipZipAbility::new));

    public final Value range;
    public final Value simplePullPower;
    public final Value qfPullBonus;
    public final Value simpleVisualTicks;
    public final Value damage;

    public BlackwhipZipAbility(Value range, Value simplePullPower, Value qfPullBonus, Value simpleVisualTicks,
                               Value damage, AbilityProperties properties, AbilityStateManager conditions,
                               List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.simplePullPower = simplePullPower;
        this.qfPullBonus = qfPullBonus;
        this.simpleVisualTicks = simpleVisualTicks;
        this.damage = damage;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlackwhipSwingAbility.forceStop(player);
        BlackwhipWebSwingAbility.forceStop(player);
        BlackwhipChargeZipAbility.forceStop(player);
        clearSession(player, level);

        DataContext context = DataContext.forEntity(entity);
        double range = this.range.getAsFloat(context);
        float maxDamage = Math.max(0.0f, this.damage.getAsFloat(context));
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 rayEnd = eye.add(look.scale(range));

        BlockHitResult blockHit = level.clip(new ClipContext(
                eye, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        LivingEntity entityHit = maxDamage > 0.0f ? BlackwhipTargeting.raycastLiving(player, range) : null;

        double blockDist = blockHit.getType() == HitResult.Type.BLOCK
                ? eye.distanceTo(blockHit.getLocation())
                : Double.POSITIVE_INFINITY;
        double entityDist = entityHitDistance(eye, rayEnd, entityHit);

        boolean preferEntity = entityHit != null && entityDist < blockDist;
        if (!preferEntity && blockHit.getType() != HitResult.Type.BLOCK) {
            blockHit = BlackwhipTargeting.furthestBlockInCone(
                    player, range, AUTO_ATTACH_HALF_ANGLE, AUTO_ATTACH_RINGS, AUTO_ATTACH_RAYS_PER_RING);
            if (blockHit == null || blockHit.getType() != HitResult.Type.BLOCK) {
                return;
            }
        }

        double qf = QuirkFactorUtil.getQuirkFactor(player);
        float power = resolvePullPower(context, qf);

        if (preferEntity) {
            startEntityReel(player, level, entityHit, range, power, maxDamage);
        } else {
            startBlockBurst(player, level, context, blockHit, range, power, qf);
        }
    }

    private void startBlockBurst(ServerPlayer player, ServerLevel level, DataContext context,
                                 BlockHitResult hit, double range, float power, double qf) {
        Vec3 anchor = BlackwhipChainAnchors.surfaceAttachPoint(hit);
        int visualTicks = Math.max(4, this.simpleVisualTicks.getAsInt(context));
        BlackwhipChainEntity chain = BlackwhipChainHelper.spawnAnchoredChain(
                player, anchor, hit.getBlockPos(), BlackwhipChainEntity.PURPOSE_ZIP_SIMPLE,
                SEGMENT_COUNT, LINK_LENGTH, CHAIN_HP, THICKNESS, range * 1.35, MAX_KEEP, visualTicks);
        if (chain == null) {
            return;
        }

        Vec3 burst = computeBurstVelocity(player, anchor, power, qf);
        ZipSession session = new ZipSession();
        session.mode = ZipMode.BLOCK_BURST;
        session.chainId = chain.getId();
        session.burstVelocity = burst;
        session.echoTicksLeft = BURST_ECHO_TICKS;
        SESSIONS.put(player.getUUID(), session);

        applyVelocity(player, burst);
        level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.55f, 1.35f);
        level.playSound(null, player.blockPosition(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.7f, 1.35f);
    }

    private void startEntityReel(ServerPlayer player, ServerLevel level,
                                 LivingEntity target, double range, float power, float maxDamage) {
        boolean alreadyTagged = BlackwhipChainTagStore.isTagged(player, target.getId());
        int chainId = -1;
        if (!alreadyTagged) {
            BlackwhipChainEntity chain = BlackwhipChainHelper.spawnEntityLatchedChain(
                    player, target, BlackwhipChainEntity.PURPOSE_ZIP_SIMPLE,
                    SEGMENT_COUNT, LINK_LENGTH, CHAIN_HP, THICKNESS, range * 1.35, MAX_KEEP);
            if (chain == null) {
                return;
            }
            chainId = chain.getId();
        }

        float zipFrac = Mth.clamp((float) (player.distanceTo(target) / Math.max(range, 1.0e-3)), 0.0f, 1.0f);
        float dmg = maxDamage * zipFrac;
        float reelPeak = Mth.clamp(REEL_SPEED_PEAK * (0.92f + power * 0.06f), 1.4f, 2.15f);

        ZipSession session = new ZipSession();
        session.mode = ZipMode.ENTITY_REEL;
        session.chainId = chainId;
        session.targetId = target.getId();
        session.reelAge = 0;
        session.reelTicksLeft = MAX_REEL_TICKS;
        session.damage = dmg;
        session.reelSpeedPeak = reelPeak;
        session.reuseTag = alreadyTagged;
        SESSIONS.put(player.getUUID(), session);

        // Point-blank: settle immediately instead of launching past.
        // Otherwise motion is driven only by tickSessions (avoids a double-pull on the start tick).
        if (isInContact(player, target, alreadyTagged)) {
            tryDamageTarget(player, level, session, target, true);
            settleOnTarget(player);
            finishSession(player, level, session, true);
            SESSIONS.remove(player.getUUID());
        } else {
            sendAnim(player, BlackwhipChainZipAnimPayload.reel());
        }
        if (alreadyTagged) {
            level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.65f, 1.55f);
        } else {
            level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.55f, 1.45f);
            level.playSound(null, player.blockPosition(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.7f, 1.4f);
        }
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        // Held release cancels an in-progress entity reel; block bursts are already spent.
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ZipSession session = SESSIONS.get(player.getUUID());
        if (session != null && session.mode == ZipMode.ENTITY_REEL) {
            applyVelocity(player, player.getDeltaMovement().scale(0.45));
            finishSession(player, level, session, false);
            SESSIONS.remove(player.getUUID());
        }
    }

    /**
     * Continues block-burst echoes and held entity reels.
     * Called once per server tick from {@link com.github.bandithelps.utils.blackwhip.BlackwhipServerEvents}.
     * Entity reels normally end on key release ({@link #lastTick}) or mob contact.
     */
    public static void tickSessions(MinecraftServer server) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, ZipSession>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ZipSession> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            ZipSession session = entry.getValue();
            if (session.mode == ZipMode.BLOCK_BURST) {
                if (session.echoTicksLeft > 0) {
                    applyVelocity(player, session.burstVelocity);
                    session.echoTicksLeft--;
                }
                if (session.echoTicksLeft <= 0) {
                    it.remove();
                }
                continue;
            }

            LivingEntity target = resolveTarget(level, session);
            if (target == null || !target.isAlive()) {
                finishSession(player, level, session, false);
                it.remove();
                continue;
            }

            // Contact first — never apply another pull tick once you're already in range.
            if (isInContact(player, target, session.reuseTag) || willOvershoot(player, target, session)) {
                tryDamageTarget(player, level, session, target, true);
                settleOnTarget(player);
                finishSession(player, level, session, true);
                it.remove();
                continue;
            }

            applyReelStep(player, target, session);
            tryDamageTarget(player, level, session, target, false);
            applySweepDamage(player, level, session);

            if (isInContact(player, target, session.reuseTag)) {
                tryDamageTarget(player, level, session, target, true);
                settleOnTarget(player);
                finishSession(player, level, session, true);
                it.remove();
                continue;
            }

            session.reelAge++;
            session.reelTicksLeft--;
            if (session.reelTicksLeft <= 0) {
                // Safety timeout only — normal cancel is key release.
                applyVelocity(player, player.getDeltaMovement().scale(0.35));
                finishSession(player, level, session, false);
                it.remove();
            }
        }
    }

    private float resolvePullPower(DataContext context, double qf) {
        // Single QF path: base * (1 + qf * bonus). Do not also bake QF into the JSON base.
        float power = this.simplePullPower.getAsFloat(context) * (float) (1.0 + qf * this.qfPullBonus.getAsFloat(context));
        float cap = SPEED_CAP_BASE + (float) qf * SPEED_CAP_PER_QF;
        return Mth.clamp(power, 0.5f, cap);
    }

    private static Vec3 computeBurstVelocity(ServerPlayer player, Vec3 anchor, float power, double qf) {
        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3 toAnchor = anchor.subtract(center);
        double dist = toAnchor.length();
        Vec3 dir = dist > 1.0e-3 ? toAnchor.scale(1.0 / dist) : player.getLookAngle().normalize();
        double distScale = Mth.clamp(dist / 10.0, 0.75, 1.0);
        Vec3 burst = dir.scale(power * distScale);
        if (Math.abs(dir.y) < 0.15) {
            burst = burst.add(0.0, FLAT_UP_BIAS, 0.0);
        }
        double maxUp = MAX_UP_BASE + qf * MAX_UP_PER_QF;
        if (burst.y > maxUp) {
            burst = new Vec3(burst.x, maxUp, burst.z);
        }
        return burst;
    }

    private static void applyReelStep(ServerPlayer player, LivingEntity target, ZipSession session) {
        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3 goal = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 to = goal.subtract(center);
        double dist = to.length();
        if (dist < 1.0e-3) {
            return;
        }
        Vec3 dir = to.scale(1.0 / dist);

        // Ease-in acceleration over the first REEL_RAMP_TICKS, then hold peak until near contact.
        float ramp = Mth.clamp(session.reelAge / (float) REEL_RAMP_TICKS, 0.0f, 1.0f);
        float eased = ramp * ramp; // slow start, then pick up speed
        double cruising = Mth.lerp(eased, REEL_SPEED_START, session.reelSpeedPeak);

        float brakeDist = session.reuseTag ? TAGGED_BRAKE_DIST : BRAKE_DIST;
        double maxStep = session.reuseTag ? dist * 0.78 : dist * 0.55;
        // Approach brake: slow inside brake range; tagged tugs keep more of their step.
        double approachMin = session.reuseTag ? 0.40 : 0.22;
        double approach = dist > brakeDist ? 1.0 : Mth.clamp(dist / brakeDist, approachMin, 1.0);
        double speed = Math.min(cruising * approach, maxStep);
        if (session.reuseTag) {
            speed *= TAGGED_CLOSE_MULT;
        }

        // Cancel one tick of vanilla gravity so upward / downward reels keep the same rate.
        Vec3 gravityCancel = new Vec3(0.0, 0.08, 0.0);

        if (session.reuseTag) {
            // Existing tether: yank both ends together harder so they meet in the middle faster.
            double half = speed * 0.5;
            applyVelocity(player, dir.scale(half).add(gravityCancel));
            Vec3 mobVel = dir.scale(-half);
            if (!target.onGround()) {
                mobVel = mobVel.add(gravityCancel);
            }
            target.setDeltaMovement(mobVel);
            target.hurtMarked = true;
            target.setOnGround(false);
            return;
        }

        // Untagged: player flies the full path toward the mob on a new zip whip.
        applyVelocity(player, dir.scale(speed).add(gravityCancel));
    }

    private static boolean isInContact(ServerPlayer player, LivingEntity target) {
        return isInContact(player, target, false);
    }

    private static boolean isInContact(ServerPlayer player, LivingEntity target, boolean taggedTug) {
        float contact = taggedTug ? TAGGED_CONTACT_DIST : CONTACT_DIST;
        return player.distanceTo(target) <= contact
                || player.getBoundingBox().inflate(taggedTug ? 0.2 : 0.35).intersects(target.getBoundingBox());
    }

    /** True when this tick's pull would carry the player past / through the target. */
    private static boolean willOvershoot(ServerPlayer player, LivingEntity target, ZipSession session) {
        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3 goal = target.position().add(0, target.getBbHeight() * 0.5, 0);
        double dist = center.distanceTo(goal);
        float ramp = Mth.clamp(session.reelAge / (float) REEL_RAMP_TICKS, 0.0f, 1.0f);
        double cruising = Mth.lerp(ramp * ramp, REEL_SPEED_START, session.reelSpeedPeak);
        if (session.reuseTag) {
            cruising *= TAGGED_CLOSE_MULT;
        }
        float contact = session.reuseTag ? TAGGED_CONTACT_DIST : CONTACT_DIST;
        return dist <= contact + cruising * 0.85;
    }

    /** Kill leftover reel momentum so a slam doesn't become a fly-by orbit. */
    private static void settleOnTarget(ServerPlayer player) {
        Vec3 residual = player.getDeltaMovement().scale(HIT_STOP_SCALE);
        // Keep a tiny upward so you don't immediately ground-clip into the mob.
        if (residual.y < 0.05) {
            residual = new Vec3(residual.x * 0.5, 0.08, residual.z * 0.5);
        }
        applyVelocity(player, residual);
    }

    private static void tryDamageTarget(ServerPlayer player, ServerLevel level, ZipSession session,
                                        LivingEntity target, boolean forceContact) {
        if (session.damage <= 0.0f || session.damagedIds.contains(target.getId())) {
            return;
        }
        boolean touching = forceContact || isInContact(player, target, session.reuseTag);
        if (!touching) {
            return;
        }
        target.hurt(level.damageSources().mobAttack(player), session.damage);
        applyHitKnockback(player, target, HIT_KNOCKBACK);
        session.damagedIds.add(target.getId());
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG,
                SoundSource.PLAYERS, 0.7f, 1.15f);
    }

    private static void applySweepDamage(ServerPlayer player, ServerLevel level, ZipSession session) {
        if (session.damage <= 0.0f) {
            return;
        }
        Vec3 start = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3 motion = player.getDeltaMovement();
        Vec3 end = motion.lengthSqr() > 0.04
                ? start.add(motion.normalize().scale(1.6))
                : start.add(player.getLookAngle().scale(1.6));
        AABB search = new AABB(start, end).inflate(SWEEP_RADIUS);
        for (Entity e : level.getEntities(player, search)) {
            if (!(e instanceof LivingEntity living) || !living.isAlive() || living == player) {
                continue;
            }
            if (session.damagedIds.contains(living.getId())) {
                continue;
            }
            Optional<Vec3> impact = living.getBoundingBox().inflate(SWEEP_RADIUS).clip(start, end);
            if (impact.isEmpty() && !living.getBoundingBox().intersects(player.getBoundingBox().inflate(0.4))) {
                continue;
            }
            living.hurt(level.damageSources().mobAttack(player), session.damage * 0.85f);
            applyHitKnockback(player, living, HIT_KNOCKBACK * 0.75f);
            session.damagedIds.add(living.getId());
            level.playSound(null, living.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK,
                    SoundSource.PLAYERS, 0.55f, 1.25f);
        }
    }

    /** Knocks the mob away from the player (vanilla knockback direction convention). */
    private static void applyHitKnockback(ServerPlayer player, LivingEntity target, float strength) {
        if (strength <= 0.0f) {
            return;
        }
        target.knockback(strength, player.getX() - target.getX(), player.getZ() - target.getZ());
        target.hurtMarked = true;
    }

    private static LivingEntity resolveTarget(ServerLevel level, ZipSession session) {
        if (session.targetId < 0) {
            return null;
        }
        Entity e = level.getEntity(session.targetId);
        return e instanceof LivingEntity living ? living : null;
    }

    private static double entityHitDistance(Vec3 eye, Vec3 rayEnd, LivingEntity entity) {
        if (entity == null) {
            return Double.POSITIVE_INFINITY;
        }
        return entity.getBoundingBox().clip(eye, rayEnd)
                .map(eye::distanceTo)
                .orElse(Double.POSITIVE_INFINITY);
    }

    private static void applyVelocity(ServerPlayer player, Vec3 velocity) {
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        player.setOnGround(false);
        player.resetFallDistance();
    }

    private static void finishSession(ServerPlayer player, ServerLevel level, ZipSession session, boolean hitLanded) {
        // Tagged tug reuses the grab tether — never deactivate/retract it here.
        if (!session.reuseTag) {
            if (session.chainId >= 0 && level.getEntity(session.chainId) instanceof BlackwhipChainEntity chain) {
                chain.deactivate();
            } else {
                BlackwhipChainEntity.retractOwnedByPurpose(player.getId(), BlackwhipChainEntity.PURPOSE_ZIP_SIMPLE);
            }
        }
        if (session.mode == ZipMode.ENTITY_REEL) {
            if (hitLanded) {
                sendAnim(player, BlackwhipChainZipAnimPayload.punch());
            } else {
                sendAnim(player, BlackwhipChainZipAnimPayload.none());
            }
        }
        if (hitLanded) {
            level.playSound(null, player.blockPosition(),
                    session.reuseTag ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.FISHING_BOBBER_RETRIEVE,
                    SoundSource.PLAYERS, 0.55f, session.reuseTag ? 1.05f : 1.35f);
        }
    }

    private static void sendAnim(ServerPlayer player, BlackwhipChainZipAnimPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    private void clearSession(ServerPlayer player, ServerLevel level) {
        ZipSession session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            finishSession(player, level, session, false);
        } else {
            BlackwhipChainEntity.retractOwnedByPurpose(player.getId(), BlackwhipChainEntity.PURPOSE_ZIP_SIMPLE);
        }
    }

    /** Clears zip state for mutex with swing / charge zip / detach. */
    public static void forceStop(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        ZipSession session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            finishSession(player, level, session, false);
        } else {
            BlackwhipChainEntity.retractOwnedByPurpose(player.getId(), BlackwhipChainEntity.PURPOSE_ZIP_SIMPLE);
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_ZIP.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipZipAbility> {
        public MapCodec<BlackwhipZipAbility> codec() {
            return BlackwhipZipAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipZipAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Look-targeted whip zip. Bursts toward a looked-at surface, or if the look-ray misses, auto-latches a solid block in a forward cone preferring look-center (does nothing in open air/void). When damage is greater than zero, hold to reel into a living entity (accelerates while held) until release or contact for knockback; slam damage scales linearly with latch distance versus range (damage is the max). Already-tagged targets reuse the tether: both are yanked together and the tag stays. Pull power uses a single quirk-factor bonus (do not also scale simple_pull_power by QF in molang).")
                    .add("range", TYPE_VALUE, "Raycast reach for the zip target, and the distance at which slam damage reaches its max.")
                    .add("simple_pull_power", TYPE_VALUE, "Base launch velocity toward a block (no QF inside this value).")
                    .add("qf_pull_bonus", TYPE_VALUE, "Extra launch multiplier per quirk factor: power * (1 + qf * bonus).")
                    .add("simple_visual_ticks", TYPE_VALUE, "How long the block-zip chain stays visible.")
                    .add("damage", TYPE_VALUE, "Max slam/sweep damage at full range. Zero disables entity attach. Scales linearly with distance / range.")
                    .addExampleObject(new BlackwhipZipAbility(
                            new StaticValue(22.0f), new StaticValue(2.2f), new StaticValue(0.04f),
                            new StaticValue(10.0f), new StaticValue(4.0f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
