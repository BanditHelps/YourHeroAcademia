package com.github.bandithelps.entities;

import com.github.bandithelps.utils.blackwhip.BlackwhipChainAnchors;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainTagStore;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative IK controller for one Blackwhip chain tether. Owns shared HP and drives a
 * dynamic set of {@link BlackwhipSegmentEntity} hit proxies along FABRIK joints, with a hard waist
 * helix latch on the target. IK runs in {@link #serverPostTick()} after owner movement settles.
 */
public class BlackwhipChainEntity extends Entity {

    public static final int MAX_SEGMENTS = BlackwhipChainAnchors.MAX_SEGMENTS;
    public static final int MIN_SEGMENTS = BlackwhipChainAnchors.MIN_SEGMENTS;
    public static final int DEFAULT_CORE = 0xFF101A1A;
    public static final int DEFAULT_GLOW = 0xB325BE9C;

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

    private final int[] segmentIds = new int[MAX_SEGMENTS];
    private final Vec3[] joints = new Vec3[MAX_SEGMENTS];
    private int retractCountdown = -1;
    private boolean segmentsSpawned;
    private int resizeCooldown;
    private int minSegmentSeed = MIN_SEGMENTS;

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
        builder.define(DATA_GLOW_COLOR, DEFAULT_GLOW);
        builder.define(DATA_THICKNESS, 1.0f);
        builder.define(DATA_TRAVEL_TICKS, 6);
        builder.define(DATA_RETRACT_TICKS, 6);
        builder.define(DATA_ACTIVE, true);
        builder.define(DATA_HURT_TICK, 0);
        builder.define(DATA_SEED, 0);
        builder.define(DATA_WRAP_TURNS, 1.6f);
        builder.define(DATA_WRAP_JOINTS, 5);
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
        if (getTargetId() >= 0 && (target == null || !target.isAlive()) && isActive()) {
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

        LivingEntity target = getTargetLiving();
        if (isActive() && target != null && target.isAlive()) {
            maybeResize(owner, target);
            updateJoints(owner, target);
            moveSegments(owner);
        } else if (!isActive()) {
            updateRetractJoints(owner);
            moveSegments(owner);
        }
    }

    /** Applies segment/explosion hit damage to shared HP. */
    public boolean damageFromSegment(DamageSource source, float damage) {
        if (!isActive() || damage <= 0.0f) {
            return false;
        }
        Entity attacker = source.getEntity();
        boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
        if (!explosion && attacker != null && attacker.getId() == getOwnerId()) {
            return false;
        }
        // Tagged target should not casually punch-break via normal melee either (click-through),
        // but explosions and third parties still apply.
        if (!explosion && attacker != null && attacker.getId() == getTargetId()) {
            return false;
        }

        float hp = getHp() - damage;
        this.getEntityData().set(DATA_HP, hp);
        this.getEntityData().set(DATA_HURT_TICK, this.tickCount);
        if (hp <= 0.0f) {
            notifyStoreBreak();
            deactivate();
        }
        return true;
    }

    public void deactivate() {
        if (!isActive() && this.retractCountdown >= 0) {
            return;
        }
        this.getEntityData().set(DATA_ACTIVE, false);
        this.retractCountdown = Math.max(1, getRetractTicks());
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

    private void maybeResize(Entity owner, LivingEntity target) {
        if (resizeCooldown > 0) {
            resizeCooldown--;
            return;
        }
        Vec3 wrist = BlackwhipChainAnchors.resolveOwnerWrist(owner);
        Vec3 entry = BlackwhipChainAnchors.resolveWaistEntry(target, wrist);
        int wrapJoints = BlackwhipChainAnchors.wrapJointCount(target);
        this.getEntityData().set(DATA_WRAP_JOINTS, wrapJoints);

        int n = getSegmentCount();
        int ropeEnd = Math.max(2, n - wrapJoints);
        double ropeDist = Math.max(0.01, wrist.distanceTo(entry));
        float nominal = getLinkLength();
        float minLink = BlackwhipChainAnchors.minLinkLength(nominal);
        float maxLink = BlackwhipChainAnchors.maxLinkLength(nominal);
        float avgLink = (float) (ropeDist / Math.max(1, ropeEnd - 1));

        // Hysteresis: stretch links first, only add/remove when outside the comfortable band.
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

    /**
     * Insert one joint by splitting the longest rope edge. Avoids full-chain resampling pops.
     */
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

        // Shift wrap + trailing rope joints up to make room.
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

    /**
     * Remove one mid-rope joint (shortest local span) and close the gap.
     */
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
                // Blend owner velocity with joint delta so clients interpolate smoothly.
                Vec3 motion = ownerDelta.add(jointDelta.scale(0.35));
                seg.setDeltaMovement(motion);
                seg.setPos(j.x, j.y, j.z);
            }
        }
    }

    private void updateJoints(Entity owner, LivingEntity target) {
        int n = getSegmentCount();
        if (n < 2) {
            return;
        }
        Vec3 root = BlackwhipChainAnchors.resolveOwnerWrist(owner);
        Vec3 entry = BlackwhipChainAnchors.resolveWaistEntry(target, root);
        float travel = Math.max(1, getTravelTicks());
        float extend = Mth.clamp(this.tickCount / travel, 0.0f, 1.0f);
        extend = 1.0f - (float) Math.pow(1.0 - extend, 3.0);

        int wrapJoints = Mth.clamp(getWrapJoints(), BlackwhipChainAnchors.MIN_WRAP_JOINTS,
                Math.min(BlackwhipChainAnchors.MAX_WRAP_JOINTS, n - 2));
        int ropeEnd = Math.max(2, n - wrapJoints);

        // Seed only uninitialized joints — never rewrite the whole rope to a straight line each tick
        // (that was the main source of grow/shrink jitter).
        for (int i = 0; i < n; i++) {
            if (joints[i] == null || joints[i].equals(Vec3.ZERO)) {
                double t = i / (double) (n - 1);
                joints[i] = root.add(entry.subtract(root).scale(t));
            }
        }

        Vec3 reachEntry = root.add(entry.subtract(root).scale(extend));
        float link = BlackwhipChainAnchors.adaptiveLinkLength(
                root.distanceTo(reachEntry), ropeEnd, getLinkLength());

        Vec3[] helix = BlackwhipChainAnchors.buildWaistHelix(target, root, wrapJoints, getWrapTurns());

        // Pin ends; solve middle with adaptive link length so existing joints ease instead of snap.
        joints[0] = root;
        if (ropeEnd >= 2) {
            joints[ropeEnd - 1] = reachEntry;
            solveFabrik(joints, ropeEnd, root, reachEntry, link, 4);
            double sag = link * 0.08;
            for (int i = 1; i < ropeEnd - 1; i++) {
                double envelope = Math.sin(Math.PI * (i / (double) Math.max(1, ropeEnd - 1)));
                joints[i] = joints[i].add(0, -sag * envelope, 0);
            }
            // Soft re-constrain (one pass) — enough to keep lengths, not enough to pop.
            constrainForward(joints, ropeEnd, root, link);
            joints[0] = root;
            joints[ropeEnd - 1] = reachEntry;
        }

        // Hard-constrain wrap joints onto the helix once mostly extended.
        if (extend > 0.45f) {
            float wrapBlend = Mth.clamp((extend - 0.45f) / 0.55f, 0.0f, 1.0f);
            for (int w = 0; w < wrapJoints; w++) {
                int ji = ropeEnd + w;
                if (ji >= n) {
                    break;
                }
                Vec3 helixPt = helix[Math.min(w, helix.length - 1)];
                joints[ji] = wrapBlend >= 0.99f ? helixPt : reachEntry.lerp(helixPt, wrapBlend);
            }
            joints[n - 1] = wrapBlend >= 0.99f ? helix[helix.length - 1]
                    : reachEntry.lerp(helix[helix.length - 1], wrapBlend);
        } else {
            // Still extending: park wrap joints at the rope tip until the coil engages.
            for (int i = ropeEnd; i < n; i++) {
                joints[i] = reachEntry;
            }
        }

        // Keep the whip draped over terrain instead of phasing through ground/walls.
        joints[0] = root;
        BlackwhipChainAnchors.collideJointChain(this.level(), this, joints, n);
        joints[0] = root;
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

    /** World positions of active joints (server). Used by explosion checks. */
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
        return damageFromSegment(source, damage);
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

    public void setColors(int core, int glow) {
        this.getEntityData().set(DATA_CORE_COLOR, core);
        this.getEntityData().set(DATA_GLOW_COLOR, glow);
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
