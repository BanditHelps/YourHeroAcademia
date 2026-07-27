package com.github.bandithelps.entities;

import com.github.bandithelps.utils.blackwhip.BlackwhipChainAnchors;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainTagStore;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative IK controller for one Blackwhip chain tether. Owns shared HP and drives a
 * dynamic set of {@link BlackwhipSegmentEntity} hit proxies along FABRIK joints. Chains deploy as a
 * flying tip that latches on tip contact, then pins a wrap helix on the target at the hit
 * height. IK runs in {@link #serverPostTick()} after owner movement settles.
 */
public class BlackwhipChainEntity extends Entity {

    public static final int MAX_SEGMENTS = BlackwhipChainAnchors.MAX_SEGMENTS;
    public static final int MIN_SEGMENTS = BlackwhipChainAnchors.MIN_SEGMENTS;
    public static final int DEFAULT_CORE = 0xFF101A1A;
    public static final int DEFAULT_OUTER = 0xE025BE9C;
    public static final int DEFAULT_GLOW = 0xB325BE9C;

    /** Tip is flying along aim; no target yet. */
    public static final int PHASE_DEPLOYING = 0;
    /** Tip latched to a living target. */
    public static final int PHASE_LATCHED = 1;
    /** Retracting to the wrist before discard. */
    public static final int PHASE_RETRACTING = 2;

    private static final int LATCH_BLEND_TICKS = 5;
    /** Client wrap coil ease after latch (tip settle then helix grow). */
    private static final int WRAP_ANIM_TICKS = 10;
    private static final double TIP_HIT_INFLATE = 0.35;

    private static final Set<BlackwhipChainEntity> ACTIVE_SERVER = ConcurrentHashMap.newKeySet();

    private static final EntityDataAccessor<Integer> DATA_OWNER_ID =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET_ID =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEGMENT_COUNT =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_LINK_LENGTH =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HP =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MAX_HP =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_CORE_COLOR =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_OUTER_COLOR =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GLOW_COLOR =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_THICKNESS =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_TRAVEL_TICKS =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RETRACT_TICKS =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ACTIVE =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_HURT_TICK =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEED =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_WRAP_TURNS =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_WRAP_JOINTS =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.INT);
    /** Fraction of target hitbox height for the wrap band / tip anchor (set on latch). */
    private static final EntityDataAccessor<Float> DATA_WRAP_HEIGHT =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_MAX_RANGE =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.FLOAT);
    /** Entity tickCount when latched; -1 while deploying / unused. Drives wrap animation. */
    private static final EntityDataAccessor<Integer> DATA_LATCH_TICK =
            SynchedEntityData.defineId(BlackwhipChainEntity.class, EntityDataSerializers.INT);

    private final int[] segmentIds = new int[MAX_SEGMENTS];
    private final Vec3[] joints = new Vec3[MAX_SEGMENTS];
    private int retractCountdown = -1;
    private boolean segmentsSpawned;
    private int resizeCooldown;
    private int minSegmentSeed = MIN_SEGMENTS;

    private Vec3 tipPos = Vec3.ZERO;
    private Vec3 tipVelocity = Vec3.ZERO;
    private boolean tipReady;
    private int latchBlendRemaining;
    private Vec3 latchBlendFrom = Vec3.ZERO;

    /** Stored at spawn for {@link BlackwhipChainTagStore#registerChain} on latch. */
    private int latchTtlTicks;
    private double latchMaxDistance;
    private int latchMaxKeep = 2;

    public BlackwhipChainEntity(EntityType<? extends BlackwhipChainEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        for (int i = 0; i < MAX_SEGMENTS; i++) {
            segmentIds[i] = -1;
            joints[i] = Vec3.ZERO;
        }
    }

    public static Set<BlackwhipChainEntity> activeServerChains() {
        return ACTIVE_SERVER;
    }

    /** Active deploying or latched chains owned by {@code ownerId}. */
    public static int countOwnedActive(int ownerId) {
        if (ownerId < 0) {
            return 0;
        }
        int count = 0;
        for (BlackwhipChainEntity chain : ACTIVE_SERVER) {
            if (chain.isAlive() && chain.isActive() && chain.getOwnerId() == ownerId) {
                count++;
            }
        }
        return count;
    }

    public static BlackwhipChainEntity findOwnedActive(int ownerId) {
        if (ownerId < 0) {
            return null;
        }
        for (BlackwhipChainEntity chain : ACTIVE_SERVER) {
            if (chain.isAlive() && chain.isActive() && chain.getOwnerId() == ownerId) {
                return chain;
            }
        }
        return null;
    }

    public static void retractAllOwned(int ownerId) {
        if (ownerId < 0) {
            return;
        }
        for (BlackwhipChainEntity chain : ACTIVE_SERVER) {
            if (chain.isAlive() && chain.isActive() && chain.getOwnerId() == ownerId) {
                chain.deactivate();
            }
        }
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!this.level().isClientSide()) {
            ACTIVE_SERVER.add(this);
        }
    }

    @Override
    public void onRemovedFromLevel() {
        ACTIVE_SERVER.remove(this);
        super.onRemovedFromLevel();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER_ID, -1);
        builder.define(DATA_TARGET_ID, -1);
        builder.define(DATA_SEGMENT_COUNT, MIN_SEGMENTS);
        builder.define(DATA_LINK_LENGTH, 0.85f);
        builder.define(DATA_HP, 20.0f);
        builder.define(DATA_MAX_HP, 20.0f);
        builder.define(DATA_CORE_COLOR, DEFAULT_CORE);
        builder.define(DATA_OUTER_COLOR, DEFAULT_OUTER);
        builder.define(DATA_GLOW_COLOR, DEFAULT_GLOW);
        builder.define(DATA_THICKNESS, 1.0f);
        builder.define(DATA_TRAVEL_TICKS, 12);
        builder.define(DATA_RETRACT_TICKS, 6);
        builder.define(DATA_ACTIVE, true);
        builder.define(DATA_HURT_TICK, 0);
        builder.define(DATA_SEED, 0);
        builder.define(DATA_WRAP_TURNS, 2.0f);
        builder.define(DATA_WRAP_JOINTS, 1);
        builder.define(DATA_WRAP_HEIGHT, BlackwhipChainAnchors.DEFAULT_WRAP_HEIGHT);
        builder.define(DATA_PHASE, PHASE_DEPLOYING);
        builder.define(DATA_MAX_RANGE, 18.0f);
        builder.define(DATA_LATCH_TICK, -1);
    }

    /**
     * Starts tip flight from the owner's wrist toward the crosshair aim point. {@code direction}
     * is the eye look vector; the tip path is raised from the wrist so it tracks the cursor
     * instead of flying parallel below it into the ground.
     */
    public void beginDeploy(Vec3 direction, double maxRange) {
        Entity owner = getOwner();
        Vec3 wrist = owner != null ? BlackwhipChainAnchors.resolveOwnerWrist(owner) : this.position();
        Vec3 look = direction.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : direction.normalize();
        float range = (float) Math.max(1.0, maxRange);

        // Crosshair ray originates at the eyes; aim the tip at that world point so the path
        // climbs from the wrist toward where the player is looking.
        Vec3 eye = owner instanceof LivingEntity living
                ? living.getEyePosition()
                : wrist.add(0.0, 1.62, 0.0);
        Vec3 aimPoint = eye.add(look.scale(range));
        Vec3 dir = aimPoint.subtract(wrist);
        if (dir.lengthSqr() < 1.0e-6) {
            dir = look;
        } else {
            dir = dir.normalize();
        }

        setMaxRange(range);
        setPhase(PHASE_DEPLOYING);
        setTargetId(-1);
        setLatchTick(-1);
        setWrapHeight(BlackwhipChainAnchors.DEFAULT_WRAP_HEIGHT);
        this.tipPos = wrist.add(dir.scale(0.15));
        double tipSpeed = range / (double) Math.max(1, getTravelTicks());
        this.tipVelocity = dir.scale(tipSpeed);
        this.tipReady = true;
        this.latchBlendRemaining = 0;
        this.getEntityData().set(DATA_ACTIVE, true);
        this.retractCountdown = -1;
    }

    public void setLatchParams(int ttlTicks, double maxDistance, int maxKeep) {
        this.latchTtlTicks = Math.max(0, ttlTicks);
        this.latchMaxDistance = maxDistance;
        this.latchMaxKeep = Math.max(1, maxKeep);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }

        Entity owner = getOwner();
        if (getOwnerId() >= 0 && (owner == null || !owner.isAlive())) {
            forceDiscard();
            return;
        }

        LivingEntity target = getTargetLiving();
        if (isLatched() && (target == null || !target.isAlive())) {
            deactivate();
        }

        // Keep controller near the wrist for tracking/culling; IK runs in serverPostTick.
        if (owner != null) {
            Vec3 wrist = BlackwhipChainAnchors.resolveOwnerWrist(owner);
            this.setPos(wrist.x, wrist.y, wrist.z);
            this.setDeltaMovement(owner.getDeltaMovement());
        }

        if (!segmentsSpawned && owner != null) {
            spawnSegments();
            segmentsSpawned = true;
        }

        if (this.retractCountdown > 0) {
            this.retractCountdown--;
            if (this.retractCountdown == 0) {
                forceDiscard();
            }
        }
    }

    /**
     * Called from {@link com.github.bandithelps.utils.blackwhip.BlackwhipServerEvents} after entity
     * movement for the tick has settled.
     */
    public void serverPostTick() {
        if (this.level().isClientSide() || this.isRemoved()) {
            return;
        }
        Entity owner = getOwner();
        if (owner == null || !owner.isAlive()) {
            return;
        }
        if (!segmentsSpawned) {
            return;
        }

        if (isDeploying()) {
            tickDeployFlight(owner);
            if (!isDeploying()) {
                // Latched or retracting mid-tick — finish with the matching branch below.
                if (isLatched()) {
                    LivingEntity target = getTargetLiving();
                    if (target != null && target.isAlive()) {
                        maybeResize(owner, target);
                        updateLatchedJoints(owner, target);
                        moveSegments(owner);
                    }
                } else if (!isActive()) {
                    updateRetractJoints(owner);
                    moveSegments(owner);
                }
                return;
            }
            maybeResizeToTip(owner);
            updateDeployJoints(owner);
            moveSegments(owner);
            return;
        }

        LivingEntity target = getTargetLiving();
        if (isLatched() && target != null && target.isAlive()) {
            maybeResize(owner, target);
            updateLatchedJoints(owner, target);
            moveSegments(owner);
        } else if (!isActive()) {
            updateRetractJoints(owner);
            moveSegments(owner);
        }
    }

    private void tickDeployFlight(Entity owner) {
        if (!tipReady) {
            beginDeploy(owner.getLookAngle(), getMaxRange());
        }
        Vec3 wrist = BlackwhipChainAnchors.resolveOwnerWrist(owner);
        Vec3 prevTip = tipPos;
        Vec3 next = tipPos.add(tipVelocity);

        BlockHitResult blockHit = this.level().clip(new ClipContext(
                prevTip, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            tipPos = blockHit.getLocation();
            deactivate();
            return;
        }

        Vec3 fromWrist = next.subtract(wrist);
        double dist = fromWrist.length();
        float maxRange = getMaxRange();
        boolean atMax = dist >= maxRange;
        if (atMax && dist > 1.0e-6) {
            next = wrist.add(fromWrist.scale(maxRange / dist));
        }
        tipPos = next;

        if (tryLatchAtTip(owner)) {
            return;
        }
        if (atMax) {
            deactivate();
        }
    }

    private boolean tryLatchAtTip(Entity owner) {
        AABB tipBox = new AABB(tipPos, tipPos).inflate(TIP_HIT_INFLATE);
        List<LivingEntity> candidates = this.level().getEntitiesOfClass(
                LivingEntity.class,
                tipBox,
                e -> e.isAlive() && e.getId() != getOwnerId());
        if (candidates.isEmpty()) {
            return false;
        }

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity living : candidates) {
            if (owner instanceof ServerPlayer player && BlackwhipChainTagStore.isTagged(player, living.getId())) {
                continue;
            }
            double d = living.getBoundingBox().getCenter().distanceToSqr(tipPos);
            if (d < bestDist) {
                bestDist = d;
                best = living;
            }
        }
        if (best == null) {
            return false;
        }
        latch(best);
        return true;
    }

    public void latch(LivingEntity target) {
        if (!isDeploying() || target == null || !target.isAlive()) {
            return;
        }
        setTargetId(target.getId());
        setPhase(PHASE_LATCHED);
        setLatchTick(this.tickCount);
        // Anchor / wrap band follows tip contact on the hitbox (inflate can sit outside AABB).
        AABB hitBb = target.getBoundingBox();
        Vec3 contact = new Vec3(
                Mth.clamp(tipPos.x, hitBb.minX, hitBb.maxX),
                Mth.clamp(tipPos.y, hitBb.minY, hitBb.maxY),
                Mth.clamp(tipPos.z, hitBb.minZ, hitBb.maxZ));
        setWrapHeight(BlackwhipChainAnchors.computeWrapHeight(target, contact));
        this.latchBlendFrom = tipPos;
        this.latchBlendRemaining = LATCH_BLEND_TICKS;

        Entity owner = getOwner();
        if (owner instanceof ServerPlayer player) {
            BlackwhipChainTagStore.registerChain(
                    player, target, this, latchTtlTicks, latchMaxDistance, latchMaxKeep);
            if (player.level() instanceof ServerLevel level) {
                level.playSound(null, player.blockPosition(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.7f, 1.3f);
            }
        } else if (this.level() instanceof ServerLevel level) {
            level.playSound(null, this.blockPosition(), SoundEvents.LEAD_TIED, SoundSource.NEUTRAL, 0.7f, 1.3f);
        }
    }

    /**
     * Applies shared HP damage. During deploy, tip-segment hits also deflect {@link #tipVelocity}.
     */
    public boolean damageFromSegment(DamageSource source, float damage) {
        return damageFromSegment(source, damage, -1);
    }

    public boolean damageFromSegment(DamageSource source, float damage, int segmentIndex) {
        if (!isActive() || damage <= 0.0f) {
            return false;
        }
        Entity attacker = source.getEntity();
        boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
        if (!explosion && attacker != null && attacker.getId() == getOwnerId()) {
            return false;
        }
        // Latched target should not casually punch-break via normal melee (click-through),
        // but explosions and third parties still apply. During deploy there is no target yet.
        if (isLatched() && !explosion && attacker != null && attacker.getId() == getTargetId()) {
            return false;
        }

        if (isDeploying() && isTipSegmentIndex(segmentIndex)) {
            applyTipKnockback(source);
        }

        float hp = getHp() - damage;
        this.getEntityData().set(DATA_HP, hp);
        this.getEntityData().set(DATA_HURT_TICK, this.tickCount);
        if (hp <= 0.0f) {
            if (isLatched()) {
                notifyStoreBreak();
            }
            deactivate();
        }
        return true;
    }

    private boolean isTipSegmentIndex(int segmentIndex) {
        if (segmentIndex < 0) {
            return true; // controller / unknown — treat as tip-capable during deploy
        }
        int n = getSegmentCount();
        int tipJoints = Mth.clamp(getWrapJoints(), BlackwhipChainAnchors.MIN_WRAP_JOINTS,
                Math.min(BlackwhipChainAnchors.MAX_WRAP_JOINTS, Math.max(1, n - 2)));
        int ropeEnd = Math.max(2, n - tipJoints);
        return segmentIndex >= ropeEnd - 1;
    }

    private void applyTipKnockback(DamageSource source) {
        Entity attacker = source.getEntity();
        double speed = Math.max(0.35, tipVelocity.length());
        Vec3 redirected;
        if (attacker != null) {
            Vec3 look = attacker.getLookAngle();
            if (look.lengthSqr() < 1.0e-6) {
                look = tipPos.subtract(attacker.position());
            }
            if (look.lengthSqr() < 1.0e-6) {
                look = tipVelocity.scale(-1.0);
            }
            redirected = look.normalize().scale(speed * 1.15);
        } else {
            redirected = tipVelocity.lengthSqr() < 1.0e-6
                    ? new Vec3(0, 0.2, 0)
                    : tipVelocity.normalize().scale(-speed);
        }
        // Cap so deflections stay readable.
        double maxSpeed = Math.max(speed * 1.5, getMaxRange() / (double) Math.max(1, getTravelTicks()) * 1.5);
        if (redirected.length() > maxSpeed) {
            redirected = redirected.normalize().scale(maxSpeed);
        }
        this.tipVelocity = redirected;
    }

    public void deactivate() {
        if (!isActive() && this.retractCountdown >= 0) {
            return;
        }
        this.getEntityData().set(DATA_ACTIVE, false);
        setPhase(PHASE_RETRACTING);
        this.retractCountdown = Math.max(1, getRetractTicks());
        // Keep latch tick so retract can still read a finished wrap progress if needed.
    }

    private void notifyStoreBreak() {
        Entity owner = getOwner();
        if (owner instanceof ServerPlayer player) {
            BlackwhipChainTagStore.removeTagByChain(player, this.getId());
        }
    }

    private void forceDiscard() {
        discardSegments();
        this.discard();
    }

    private void spawnSegments() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        int count = getSegmentCount();
        Vec3 origin = this.position();
        for (int i = 0; i < count; i++) {
            spawnOneSegment(level, i, origin);
        }
        for (int i = count; i < MAX_SEGMENTS; i++) {
            segmentIds[i] = -1;
        }
    }

    private void spawnOneSegment(ServerLevel level, int index, Vec3 pos) {
        BlackwhipSegmentEntity seg = new BlackwhipSegmentEntity(ModEntities.BLACKWHIP_SEGMENT.get(), level);
        seg.setChainId(this.getId());
        seg.setIndex(index);
        seg.setPos(pos.x, pos.y, pos.z);
        level.addFreshEntity(seg);
        segmentIds[index] = seg.getId();
        joints[index] = pos;
    }

    private void discardSegments() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        for (int i = 0; i < MAX_SEGMENTS; i++) {
            discardSegmentAt(level, i);
        }
    }

    private void discardSegmentAt(ServerLevel level, int i) {
        int id = segmentIds[i];
        if (id >= 0) {
            Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        segmentIds[i] = -1;
    }

    private void maybeResizeToTip(Entity owner) {
        if (resizeCooldown > 0) {
            resizeCooldown--;
            return;
        }
        Vec3 wrist = BlackwhipChainAnchors.resolveOwnerWrist(owner);
        int wrapJoints = BlackwhipChainAnchors.MIN_WRAP_JOINTS;
        this.getEntityData().set(DATA_WRAP_JOINTS, wrapJoints);

        int n = getSegmentCount();
        int ropeEnd = Math.max(2, n - wrapJoints);
        double ropeDist = Math.max(0.01, wrist.distanceTo(tipPos));
        float nominal = getLinkLength();
        float minLink = BlackwhipChainAnchors.minLinkLength(nominal);
        float maxLink = BlackwhipChainAnchors.maxLinkLength(nominal);
        float avgLink = (float) (ropeDist / Math.max(1, ropeEnd - 1));

        if (avgLink > maxLink && n < MAX_SEGMENTS) {
            growRopeJoint(wrapJoints);
            resizeCooldown = 2;
        } else if (avgLink < minLink && n > Math.max(MIN_SEGMENTS, minSegmentSeed)) {
            int ropeJoints = n - wrapJoints;
            if (ropeJoints > 2) {
                shrinkRopeJoint(wrapJoints);
                resizeCooldown = 2;
            }
        }
    }

    private void maybeResize(Entity owner, LivingEntity target) {
        if (resizeCooldown > 0) {
            resizeCooldown--;
            return;
        }
        Vec3 wrist = BlackwhipChainAnchors.resolveOwnerWrist(owner);
        Vec3 entry = BlackwhipChainAnchors.resolveWaistEntry(target, wrist, getWrapHeight());
        int wrapJoints = BlackwhipChainAnchors.wrapJointCount(target);
        this.getEntityData().set(DATA_WRAP_JOINTS, wrapJoints);

        int n = getSegmentCount();
        int ropeEnd = Math.max(2, n - wrapJoints);
        double ropeDist = Math.max(0.01, wrist.distanceTo(entry));
        float nominal = getLinkLength();
        float minLink = BlackwhipChainAnchors.minLinkLength(nominal);
        float maxLink = BlackwhipChainAnchors.maxLinkLength(nominal);
        float avgLink = (float) (ropeDist / Math.max(1, ropeEnd - 1));

        if (avgLink > maxLink && n < MAX_SEGMENTS) {
            growRopeJoint(wrapJoints);
            resizeCooldown = 3;
        } else if (avgLink < minLink && n > Math.max(MIN_SEGMENTS, minSegmentSeed)) {
            int ropeJoints = n - wrapJoints;
            if (ropeJoints > 2) {
                shrinkRopeJoint(wrapJoints);
                resizeCooldown = 3;
            }
        }
    }

    private void growRopeJoint(int wrapJoints) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        int n = getSegmentCount();
        if (n >= MAX_SEGMENTS) {
            return;
        }
        int ropeEnd = Math.max(2, n - wrapJoints);
        int edge = BlackwhipChainAnchors.longestRopeEdge(joints, ropeEnd);
        int insertAt = edge + 1;
        Vec3 mid = joints[edge].lerp(joints[edge + 1], 0.5);

        for (int i = n; i > insertAt; i--) {
            joints[i] = joints[i - 1];
            segmentIds[i] = segmentIds[i - 1];
        }
        joints[insertAt] = mid;
        segmentIds[insertAt] = -1;
        spawnOneSegment(level, insertAt, mid);

        setSegmentCount(n + 1);
        reindexSegments(level, n + 1);
    }

    private void shrinkRopeJoint(int wrapJoints) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        int n = getSegmentCount();
        int ropeEnd = Math.max(2, n - wrapJoints);
        if (ropeEnd < 3 || n <= MIN_SEGMENTS) {
            return;
        }
        int removeAt = BlackwhipChainAnchors.shortestRemovableRopeJoint(joints, ropeEnd);
        discardSegmentAt(level, removeAt);

        for (int i = removeAt; i < n - 1; i++) {
            joints[i] = joints[i + 1];
            segmentIds[i] = segmentIds[i + 1];
        }
        joints[n - 1] = Vec3.ZERO;
        segmentIds[n - 1] = -1;

        setSegmentCount(n - 1);
        reindexSegments(level, n - 1);
    }

    private void reindexSegments(ServerLevel level, int count) {
        for (int i = 0; i < count; i++) {
            int id = segmentIds[i];
            if (id >= 0 && level.getEntity(id) instanceof BlackwhipSegmentEntity seg) {
                seg.setIndex(i);
            }
        }
    }

    private void moveSegments(Entity owner) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 ownerDelta = owner.getDeltaMovement();
        int count = getSegmentCount();
        for (int i = 0; i < count; i++) {
            int id = segmentIds[i];
            if (id < 0) {
                continue;
            }
            Entity e = level.getEntity(id);
            if (e instanceof BlackwhipSegmentEntity seg) {
                Vec3 j = joints[i];
                Vec3 prev = seg.position();
                Vec3 jointDelta = j.subtract(prev);
                Vec3 motion = ownerDelta.add(jointDelta.scale(0.35));
                seg.setDeltaMovement(motion);
                seg.setPos(j.x, j.y, j.z);
            }
        }
    }

    private void updateDeployJoints(Entity owner) {
        int n = getSegmentCount();
        if (n < 2) {
            return;
        }
        Vec3 root = BlackwhipChainAnchors.resolveOwnerWrist(owner);
        Vec3 tip = tipPos;
        int tipJoints = Mth.clamp(getWrapJoints(), BlackwhipChainAnchors.MIN_WRAP_JOINTS,
                Math.min(BlackwhipChainAnchors.MAX_WRAP_JOINTS, Math.max(1, n - 2)));
        int ropeEnd = Math.max(2, n - tipJoints);
        solveRopeToTip(root, tip, ropeEnd, n, 0.70f);
    }

    private void updateLatchedJoints(Entity owner, LivingEntity target) {
        int n = getSegmentCount();
        if (n < 2) {
            return;
        }
        Vec3 root = BlackwhipChainAnchors.resolveOwnerWrist(owner);
        Vec3 entry = BlackwhipChainAnchors.resolveWaistEntry(target, root, getWrapHeight());

        float blend = 1.0f;
        if (latchBlendRemaining > 0) {
            float t = 1.0f - (latchBlendRemaining / (float) LATCH_BLEND_TICKS);
            blend = 1.0f - (float) Math.pow(1.0 - t, 3.0);
            latchBlendRemaining--;
        }
        Vec3 reachEntry = latchBlendFrom.lerp(entry, blend);
        tipPos = reachEntry;

        int tipJoints = Mth.clamp(getWrapJoints(), BlackwhipChainAnchors.MIN_WRAP_JOINTS,
                Math.min(BlackwhipChainAnchors.MAX_WRAP_JOINTS, Math.max(1, n - 2)));
        int ropeEnd = Math.max(2, n - tipJoints);
        solveRopeToTip(root, reachEntry, ropeEnd, n, -1.0f);
    }

    /**
     * @param dampBlend mid-joint damp toward solved pose; negative = motion-aware auto blend
     */
    private void solveRopeToTip(Vec3 root, Vec3 tip, int ropeEnd, int n, float dampBlend) {
        Vec3 prevRoot = joints[0];
        Vec3 prevTip = joints[ropeEnd - 1];

        for (int i = 0; i < n; i++) {
            if (joints[i] == null || joints[i].equals(Vec3.ZERO)) {
                double t = i / (double) (n - 1);
                joints[i] = root.add(tip.subtract(root).scale(t));
            }
        }

        Vec3[] prevMids = new Vec3[Math.max(0, ropeEnd - 2)];
        for (int i = 1; i < ropeEnd - 1; i++) {
            prevMids[i - 1] = joints[i];
        }

        float link = BlackwhipChainAnchors.adaptiveLinkLength(
                root.distanceTo(tip), ropeEnd, getLinkLength());

        joints[0] = root;
        if (ropeEnd >= 2) {
            joints[ropeEnd - 1] = tip;
            solveFabrik(joints, ropeEnd, root, tip, link, 4);
            applyMidChainShape(joints, ropeEnd, root, tip, link);
            constrainForward(joints, ropeEnd, root, link);
            joints[0] = root;
            joints[ropeEnd - 1] = tip;

            float blend = dampBlend;
            if (blend < 0.0f) {
                double rootMove = prevRoot != null ? prevRoot.distanceTo(root) : 0.0;
                double tipMove = prevTip != null ? prevTip.distanceTo(tip) : 0.0;
                double chordMotion = Math.max(rootMove, tipMove);
                blend = chordMotion < 0.025
                        ? 0.20f
                        : (chordMotion < 0.08 ? 0.45f : 0.70f);
            }
            for (int i = 1; i < ropeEnd - 1; i++) {
                Vec3 prev = prevMids[i - 1];
                if (prev != null && !prev.equals(Vec3.ZERO)) {
                    joints[i] = prev.lerp(joints[i], blend);
                }
            }
            joints[0] = root;
            joints[ropeEnd - 1] = tip;
        }

        for (int i = ropeEnd; i < n; i++) {
            joints[i] = tip;
        }

        joints[0] = root;
        BlackwhipChainAnchors.collideJointChain(this.level(), this, joints, ropeEnd);
        joints[0] = root;
        // During deploy, keep the tip ballistic — do not let block collide pull the tip back.
        if (isDeploying()) {
            joints[ropeEnd - 1] = tip;
        }
        Vec3 finalTip = joints[ropeEnd - 1];
        for (int i = ropeEnd; i < n; i++) {
            joints[i] = finalTip;
        }
    }

    private static void applyMidChainShape(Vec3[] joints, int ropeEnd, Vec3 root, Vec3 tip, float link) {
        Vec3 axis = tip.subtract(root);
        double axisLen = axis.length();
        if (axisLen < 1.0e-6) {
            return;
        }
        Vec3 dir = axis.scale(1.0 / axisLen);
        Vec3 side = new Vec3(0, 1, 0).cross(dir);
        if (side.lengthSqr() < 1.0e-6) {
            side = new Vec3(1, 0, 0).cross(dir);
        }
        side = side.normalize();
        Vec3 lift = dir.cross(side).normalize();
        if (lift.y < 0.0) {
            lift = lift.scale(-1.0);
        }

        double liftAmt = link * 0.10;
        double sagAmt = link * 0.035;
        int denom = Math.max(1, ropeEnd - 1);
        for (int i = 1; i < ropeEnd - 1; i++) {
            double envelope = Math.sin(Math.PI * (i / (double) denom));
            joints[i] = joints[i]
                    .add(lift.scale(liftAmt * envelope))
                    .add(0, -sagAmt * envelope, 0);
        }
    }

    private void updateRetractJoints(Entity owner) {
        int n = getSegmentCount();
        Vec3 root = BlackwhipChainAnchors.resolveOwnerWrist(owner);
        float rt = Math.max(1, getRetractTicks());
        float progress = 1.0f - Mth.clamp(this.retractCountdown / rt, 0.0f, 1.0f);
        for (int i = 0; i < n; i++) {
            double t = i / (double) Math.max(1, n - 1);
            double pull = Math.min(1.0, progress + t * 0.35);
            joints[i] = joints[i].lerp(root, pull);
        }
        joints[0] = root;
        BlackwhipChainAnchors.collideJointChain(this.level(), this, joints, n);
        joints[0] = root;
    }

    private static void solveFabrik(Vec3[] joints, int n, Vec3 root, Vec3 tip, float link, int iterations) {
        joints[0] = root;
        joints[n - 1] = tip;
        for (int iter = 0; iter < iterations; iter++) {
            joints[n - 1] = tip;
            for (int i = n - 2; i >= 0; i--) {
                Vec3 dir = joints[i].subtract(joints[i + 1]);
                double len = dir.length();
                if (len < 1.0e-6) {
                    dir = new Vec3(0, 1, 0);
                    len = 1.0;
                }
                joints[i] = joints[i + 1].add(dir.scale(link / len));
            }
            joints[0] = root;
            for (int i = 1; i < n; i++) {
                Vec3 dir = joints[i].subtract(joints[i - 1]);
                double len = dir.length();
                if (len < 1.0e-6) {
                    dir = new Vec3(0, 1, 0);
                    len = 1.0;
                }
                joints[i] = joints[i - 1].add(dir.scale(link / len));
            }
        }
        joints[0] = root;
        joints[n - 1] = tip;
    }

    private static void constrainForward(Vec3[] joints, int n, Vec3 root, float link) {
        joints[0] = root;
        for (int i = 1; i < n; i++) {
            Vec3 dir = joints[i].subtract(joints[i - 1]);
            double len = dir.length();
            if (len < 1.0e-6) {
                dir = new Vec3(0, 1, 0);
                len = 1.0;
            }
            joints[i] = joints[i - 1].add(dir.scale(link / len));
        }
    }

    /** @deprecated use {@link BlackwhipChainAnchors#resolveOwnerWrist(Entity)} */
    @Deprecated
    public static Vec3 resolveOwnerAnchor(Entity owner) {
        return BlackwhipChainAnchors.resolveOwnerWrist(owner);
    }

    public static Vec3 resolveWaistLatch(LivingEntity target) {
        AABB bb = target.getBoundingBox();
        double y = bb.minY + bb.getYsize() * 0.48;
        return new Vec3((bb.minX + bb.maxX) * 0.5, y, (bb.minZ + bb.maxZ) * 0.5);
    }

    public List<BlackwhipSegmentEntity> collectSegments() {
        List<BlackwhipSegmentEntity> list = new ArrayList<>();
        AABB box = this.getBoundingBox().inflate(96.0);
        for (BlackwhipSegmentEntity seg : this.level().getEntitiesOfClass(BlackwhipSegmentEntity.class, box)) {
            if (seg.getChainId() == this.getId()) {
                list.add(seg);
            }
        }
        list.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));
        return list;
    }

    public List<Vec3> jointPositions() {
        List<Vec3> out = new ArrayList<>();
        int n = getSegmentCount();
        for (int i = 0; i < n; i++) {
            if (joints[i] != null) {
                out.add(joints[i]);
            }
        }
        return out;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return damageFromSegment(source, damage, -1);
    }

    @Override
    public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 16384.0;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    public void setOwnerId(int id) {
        this.getEntityData().set(DATA_OWNER_ID, id);
    }

    public void setTargetId(int id) {
        this.getEntityData().set(DATA_TARGET_ID, id);
    }

    public void setSegmentCount(int count) {
        this.getEntityData().set(DATA_SEGMENT_COUNT, Mth.clamp(count, MIN_SEGMENTS, MAX_SEGMENTS));
    }

    public void setMinSegmentSeed(int seed) {
        this.minSegmentSeed = Mth.clamp(seed, MIN_SEGMENTS, MAX_SEGMENTS);
    }

    public void setLinkLength(float length) {
        this.getEntityData().set(DATA_LINK_LENGTH, Math.max(0.25f, length));
    }

    public void setHp(float hp) {
        this.getEntityData().set(DATA_HP, hp);
    }

    public void setMaxHp(float maxHp) {
        this.getEntityData().set(DATA_MAX_HP, Math.max(1.0f, maxHp));
        if (getHp() > maxHp) {
            setHp(maxHp);
        }
    }

    public void setColors(int core, int outer, int glow) {
        this.getEntityData().set(DATA_CORE_COLOR, core);
        this.getEntityData().set(DATA_OUTER_COLOR, outer);
        this.getEntityData().set(DATA_GLOW_COLOR, glow);
    }

    /** @deprecated prefer {@link #setColors(int, int, int)} */
    @Deprecated
    public void setColors(int core, int glow) {
        setColors(core, DEFAULT_OUTER, glow);
    }

    public void setThickness(float thickness) {
        this.getEntityData().set(DATA_THICKNESS, thickness);
    }

    public void setTravelTicks(int ticks) {
        this.getEntityData().set(DATA_TRAVEL_TICKS, Math.max(1, ticks));
    }

    public void setRetractTicks(int ticks) {
        this.getEntityData().set(DATA_RETRACT_TICKS, Math.max(1, ticks));
    }

    public void setSeed(int seed) {
        this.getEntityData().set(DATA_SEED, seed);
    }

    public void setWrapTurns(float turns) {
        this.getEntityData().set(DATA_WRAP_TURNS, turns);
    }

    public void setWrapHeight(float height) {
        this.getEntityData().set(DATA_WRAP_HEIGHT, Mth.clamp(
                height, BlackwhipChainAnchors.MIN_WRAP_HEIGHT, BlackwhipChainAnchors.MAX_WRAP_HEIGHT));
    }

    public void setPhase(int phase) {
        this.getEntityData().set(DATA_PHASE, Mth.clamp(phase, PHASE_DEPLOYING, PHASE_RETRACTING));
    }

    public void setMaxRange(float range) {
        this.getEntityData().set(DATA_MAX_RANGE, Math.max(1.0f, range));
    }

    public void setLatchTick(int tick) {
        this.getEntityData().set(DATA_LATCH_TICK, tick);
    }

    public int getOwnerId() {
        return this.getEntityData().get(DATA_OWNER_ID);
    }

    public int getTargetId() {
        return this.getEntityData().get(DATA_TARGET_ID);
    }

    public int getSegmentCount() {
        return Mth.clamp(this.getEntityData().get(DATA_SEGMENT_COUNT), MIN_SEGMENTS, MAX_SEGMENTS);
    }

    public float getLinkLength() {
        return this.getEntityData().get(DATA_LINK_LENGTH);
    }

    public float getHp() {
        return this.getEntityData().get(DATA_HP);
    }

    public float getMaxHp() {
        return this.getEntityData().get(DATA_MAX_HP);
    }

    public int getCoreColor() {
        return this.getEntityData().get(DATA_CORE_COLOR);
    }

    public int getOuterColor() {
        return this.getEntityData().get(DATA_OUTER_COLOR);
    }

    public int getGlowColor() {
        return this.getEntityData().get(DATA_GLOW_COLOR);
    }

    public float getThickness() {
        return this.getEntityData().get(DATA_THICKNESS);
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

    public int getHurtTick() {
        return this.getEntityData().get(DATA_HURT_TICK);
    }

    public int getSeed() {
        return this.getEntityData().get(DATA_SEED);
    }

    public float getWrapTurns() {
        return this.getEntityData().get(DATA_WRAP_TURNS);
    }

    public int getWrapJoints() {
        return this.getEntityData().get(DATA_WRAP_JOINTS);
    }

    /** Fraction of target hitbox height where the wrap band / tip anchor sits. */
    public float getWrapHeight() {
        return this.getEntityData().get(DATA_WRAP_HEIGHT);
    }

    public int getPhase() {
        return this.getEntityData().get(DATA_PHASE);
    }

    public float getMaxRange() {
        return this.getEntityData().get(DATA_MAX_RANGE);
    }

    public int getLatchTick() {
        return this.getEntityData().get(DATA_LATCH_TICK);
    }

    public boolean isDeploying() {
        return isActive() && getPhase() == PHASE_DEPLOYING;
    }

    public boolean isLatched() {
        return isActive() && getPhase() == PHASE_LATCHED;
    }

    /**
     * Client/render helper: tip travel toward max range while deploying; after latch, 0→1 wrap
     * ease so the latch-height coil animates instead of popping in fully formed.
     */
    public float getExtendProgress(float partial) {
        int phase = getPhase();
        if (phase == PHASE_LATCHED || phase == PHASE_RETRACTING) {
            int latchAt = getLatchTick();
            if (latchAt < 0) {
                return 1.0f;
            }
            float wrap = (this.tickCount - latchAt + partial) / (float) WRAP_ANIM_TICKS;
            return Mth.clamp(wrap, 0.0f, 1.0f);
        }
        float maxRange = Math.max(1.0f, getMaxRange());
        Entity owner = getOwner();
        if (owner == null) {
            return Mth.clamp((tickCount + partial) / (float) Math.max(1, getTravelTicks()), 0.0f, 1.0f);
        }
        List<BlackwhipSegmentEntity> segs = collectSegments();
        if (!segs.isEmpty()) {
            BlackwhipSegmentEntity tipSeg = segs.getLast();
            Vec3 wrist = BlackwhipChainAnchors.resolveOwnerWrist(owner, partial);
            double sx = Mth.lerp(partial, tipSeg.xOld, tipSeg.getX());
            double sy = Mth.lerp(partial, tipSeg.yOld, tipSeg.getY());
            double sz = Mth.lerp(partial, tipSeg.zOld, tipSeg.getZ());
            return Mth.clamp((float) (wrist.distanceTo(new Vec3(sx, sy, sz)) / maxRange), 0.0f, 1.0f);
        }
        return Mth.clamp((tickCount + partial) / (float) Math.max(1, getTravelTicks()), 0.0f, 1.0f);
    }

    public Entity getOwner() {
        int id = getOwnerId();
        return id >= 0 ? this.level().getEntity(id) : null;
    }

    public LivingEntity getTargetLiving() {
        int id = getTargetId();
        if (id < 0) {
            return null;
        }
        Entity e = this.level().getEntity(id);
        return e instanceof LivingEntity living ? living : null;
    }

    public Player getTargetPlayer() {
        LivingEntity living = getTargetLiving();
        return living instanceof Player player ? player : null;
    }

    public boolean isParticipant(Entity entity) {
        if (entity == null) {
            return false;
        }
        int id = entity.getId();
        return id == getOwnerId() || id == getTargetId();
    }
}
