package com.github.bandithelps.utils.blackwhip;

import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.capabilities.body.BodySyncEvents;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side registry for latched chain-Blackwhip tethers. Parallel to {@link BlackwhipTagStore}; uses a
 * separate body-data key so the ribbon Blackwhip HUD is not affected. Deploying (unlatched) chains are
 * not registered here — only successful tip latches call {@link #registerChain}.
 */
public final class BlackwhipChainTagStore {

    public static final BodyPart COUNT_PART = BodyPart.CHEST;
    public static final String COUNT_KEY = "blackwhip_chain_connected_count";

    public record TagEntry(int targetId, long createdTick, int expireTicks, double maxDistance, int chainEntityId) {
    }

    private static final Map<UUID, Map<Integer, TagEntry>> PLAYER_TAGS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_COUNT = new ConcurrentHashMap<>();
    /** Owners with Lead toggled on — new latches lock at latch distance. */
    private static final Set<UUID> LEAD_ACTIVE = ConcurrentHashMap.newKeySet();
    /** Target entity ids currently being puppeted this tick (Lead spring skips these). */
    private static final Map<UUID, Set<Integer>> PUPPETED_TARGETS = new ConcurrentHashMap<>();
    /** Active Reel hold sessions (scroll extend/retract). */
    private static final Map<UUID, ReelSession> REEL_SESSIONS = new ConcurrentHashMap<>();

    /**
     * @param lockedTargetId entity id locked for single-target Puppet for the whole hold; {@code -1} for all-mode
     */
    public record ReelSession(String mode, double step, double minLength, int lockedTargetId) {
    }

    private BlackwhipChainTagStore() {
    }

    public static boolean isLeadActive(ServerPlayer owner) {
        return LEAD_ACTIVE.contains(owner.getUUID());
    }

    /**
     * Enables or disables Lead for {@code owner}. On enable, locks every current TAG chain at its
     * current owner↔target distance. On disable, unlocks all of those chains.
     */
    public static void setLeadActive(ServerPlayer owner, boolean active) {
        if (active) {
            LEAD_ACTIVE.add(owner.getUUID());
            lockAllLeashes(owner);
        } else {
            LEAD_ACTIVE.remove(owner.getUUID());
            unlockAllLeashes(owner);
        }
    }

    public static void lockAllLeashes(ServerPlayer owner) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        for (TagEntry entry : getTagEntries(owner)) {
            if (level.getEntity(entry.chainEntityId()) instanceof BlackwhipChainEntity chain
                    && level.getEntity(entry.targetId()) instanceof LivingEntity target
                    && target.isAlive()) {
                chain.lockLeashToCurrentDistance(owner, target);
            }
        }
    }

    public static void unlockAllLeashes(ServerPlayer owner) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        for (TagEntry entry : getTagEntries(owner)) {
            if (level.getEntity(entry.chainEntityId()) instanceof BlackwhipChainEntity chain) {
                chain.unlockLeashLength();
            }
        }
    }

    public static List<TagEntry> getTagEntries(ServerPlayer owner) {
        Map<Integer, TagEntry> tags = PLAYER_TAGS.get(owner.getUUID());
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(tags.values());
    }

    public static BlackwhipChainEntity getChainForTarget(ServerPlayer owner, int targetId) {
        Map<Integer, TagEntry> tags = PLAYER_TAGS.get(owner.getUUID());
        if (tags == null || !(owner.level() instanceof ServerLevel level)) {
            return null;
        }
        TagEntry entry = tags.get(targetId);
        if (entry == null) {
            return null;
        }
        Entity chain = level.getEntity(entry.chainEntityId());
        return chain instanceof BlackwhipChainEntity c ? c : null;
    }

    public static TagEntry getTagEntry(ServerPlayer owner, int targetId) {
        Map<Integer, TagEntry> tags = PLAYER_TAGS.get(owner.getUUID());
        return tags == null ? null : tags.get(targetId);
    }

    /**
     * Resolves an initial single-target pick (looked-at tagged, else nearest). Used once on Puppet press.
     */
    public static List<LivingEntity> resolveTargets(ServerPlayer owner, String mode) {
        List<LivingEntity> tagged = getTaggedEntities(owner);
        if (!"single".equalsIgnoreCase(mode) || tagged.isEmpty()) {
            return tagged;
        }
        LivingEntity looked = BlackwhipTargeting.raycastLiving(owner, 24.0);
        if (looked != null && isTagged(owner, looked.getId())) {
            return List.of(looked);
        }
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity t : tagged) {
            double d = owner.distanceToSqr(t);
            if (d < best) {
                best = d;
                nearest = t;
            }
        }
        return nearest == null ? List.of() : List.of(nearest);
    }

    /**
     * Targets for an active Puppet hold. Single-mode keeps the entity locked at press until release;
     * all-mode returns every tagged entity.
     */
    public static List<LivingEntity> resolveSessionTargets(ServerPlayer owner) {
        ReelSession session = REEL_SESSIONS.get(owner.getUUID());
        if (session == null) {
            return List.of();
        }
        if (session.lockedTargetId() < 0) {
            return getTaggedEntities(owner);
        }
        if (!(owner.level() instanceof ServerLevel level)) {
            return List.of();
        }
        if (!isTagged(owner, session.lockedTargetId())) {
            return List.of();
        }
        Entity e = level.getEntity(session.lockedTargetId());
        if (e instanceof LivingEntity living && living.isAlive()) {
            return List.of(living);
        }
        return List.of();
    }

    public static void markPuppeted(ServerPlayer owner, Iterable<LivingEntity> targets) {
        Set<Integer> ids = new HashSet<>();
        for (LivingEntity target : targets) {
            ids.add(target.getId());
        }
        if (ids.isEmpty()) {
            PUPPETED_TARGETS.remove(owner.getUUID());
        } else {
            PUPPETED_TARGETS.put(owner.getUUID(), ids);
        }
    }

    public static void clearPuppeted(ServerPlayer owner) {
        PUPPETED_TARGETS.remove(owner.getUUID());
    }

    public static boolean isPuppeted(ServerPlayer owner, int targetId) {
        Set<Integer> ids = PUPPETED_TARGETS.get(owner.getUUID());
        return ids != null && ids.contains(targetId);
    }

    public static void startReelSession(ServerPlayer owner, String mode, double step, double minLength,
                                        int lockedTargetId) {
        REEL_SESSIONS.put(owner.getUUID(), new ReelSession(
                mode == null ? "all" : mode,
                Math.max(0.05, step),
                Math.max(0.25, minLength),
                lockedTargetId));
    }

    public static void stopReelSession(ServerPlayer owner) {
        REEL_SESSIONS.remove(owner.getUUID());
    }

    public static ReelSession getReelSession(ServerPlayer owner) {
        return REEL_SESSIONS.get(owner.getUUID());
    }

    public static boolean hasReelSession(ServerPlayer owner) {
        return REEL_SESSIONS.containsKey(owner.getUUID());
    }

    public static boolean isTagged(ServerPlayer owner, int targetId) {
        Map<Integer, TagEntry> tags = PLAYER_TAGS.get(owner.getUUID());
        return tags != null && tags.containsKey(targetId);
    }

    public static int getTagCount(ServerPlayer owner) {
        Map<Integer, TagEntry> tags = PLAYER_TAGS.get(owner.getUUID());
        return tags == null ? 0 : tags.size();
    }

    /**
     * Registers an already-spawned chain that has just latched onto {@code target}.
     *
     * @return true if this was a brand-new tag
     */
    public static boolean registerChain(ServerPlayer owner, LivingEntity target, BlackwhipChainEntity chain,
                                        int expireTicks, double maxDistance, int maxKeep) {
        Map<Integer, TagEntry> tags = PLAYER_TAGS.computeIfAbsent(owner.getUUID(), k -> new ConcurrentHashMap<>());
        long now = owner.level().getGameTime();
        boolean isNew = !tags.containsKey(target.getId());

        if (!isNew) {
            TagEntry existing = tags.get(target.getId());
            tags.put(target.getId(), new TagEntry(target.getId(), now, Math.max(existing.expireTicks(), expireTicks),
                    maxDistance, existing.chainEntityId()));
            return false;
        }

        tags.put(target.getId(), new TagEntry(target.getId(), now, expireTicks, maxDistance, chain.getId()));

        if (maxKeep > 0 && tags.size() > maxKeep) {
            trimOldest(owner, tags, maxKeep);
        }
        if (isLeadActive(owner)) {
            chain.lockLeashToCurrentDistance(owner, target);
        }
        updateCount(owner, tags.size());
        return true;
    }

    public static void removeTag(ServerPlayer owner, int targetId) {
        Map<Integer, TagEntry> tags = PLAYER_TAGS.get(owner.getUUID());
        if (tags == null) {
            return;
        }
        TagEntry entry = tags.remove(targetId);
        if (entry != null) {
            despawnChain(owner, entry.chainEntityId());
        }
        updateCount(owner, tags.size());
    }

    /**
     * Removes a tag by chain entity id without despawning (chain already handling its own teardown).
     */
    public static void removeTagByChain(ServerPlayer owner, int chainEntityId) {
        Map<Integer, TagEntry> tags = PLAYER_TAGS.get(owner.getUUID());
        if (tags == null) {
            return;
        }
        Integer targetId = null;
        for (Map.Entry<Integer, TagEntry> e : tags.entrySet()) {
            if (e.getValue().chainEntityId() == chainEntityId) {
                targetId = e.getKey();
                break;
            }
        }
        if (targetId != null) {
            tags.remove(targetId);
            updateCount(owner, tags.size());
        }
    }

    public static void clearTags(ServerPlayer owner) {
        Map<Integer, TagEntry> tags = PLAYER_TAGS.remove(owner.getUUID());
        if (tags != null) {
            for (TagEntry entry : tags.values()) {
                despawnChain(owner, entry.chainEntityId());
            }
        }
        // Also retract deploying chains that never latched.
        BlackwhipChainEntity.retractAllOwned(owner.getId());
        updateCount(owner, 0);
    }

    /** Clears Lead state when the owner leaves / tags are fully wiped externally. */
    public static void clearLeadActive(ServerPlayer owner) {
        LEAD_ACTIVE.remove(owner.getUUID());
    }

    public static List<LivingEntity> getTaggedEntities(ServerPlayer owner) {
        List<LivingEntity> result = new ArrayList<>();
        Map<Integer, TagEntry> tags = PLAYER_TAGS.get(owner.getUUID());
        if (tags == null || !(owner.level() instanceof ServerLevel level)) {
            return result;
        }
        for (TagEntry entry : tags.values()) {
            Entity e = level.getEntity(entry.targetId());
            if (e instanceof LivingEntity living && living.isAlive()) {
                result.add(living);
            }
        }
        return result;
    }

    public static void tick(ServerPlayer owner) {
        Map<Integer, TagEntry> tags = PLAYER_TAGS.get(owner.getUUID());
        if (tags == null || tags.isEmpty()) {
            return;
        }
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        List<Integer> toRemove = new ArrayList<>();
        for (TagEntry entry : tags.values()) {
            Entity target = level.getEntity(entry.targetId());
            boolean dead = !(target instanceof LivingEntity living) || !living.isAlive();
            boolean expired = entry.expireTicks() > 0 && (now - entry.createdTick()) > entry.expireTicks();
            // Use closest AABB distance so huge multipart bosses (dragon) don't break while
            // you're still touching a limb far from the entity center.
            boolean tooFar = entry.maxDistance() > 0 && target != null
                    && Math.sqrt(target.getBoundingBox().distanceToSqr(owner.position())) > entry.maxDistance();
            Entity chain = level.getEntity(entry.chainEntityId());
            boolean missingChain = !(chain instanceof BlackwhipChainEntity);
            if (dead || expired || tooFar || missingChain) {
                toRemove.add(entry.targetId());
            }
        }
        for (int id : toRemove) {
            TagEntry entry = tags.remove(id);
            if (entry != null) {
                despawnChain(owner, entry.chainEntityId());
            }
        }
        if (!toRemove.isEmpty()) {
            updateCount(owner, tags.size());
        }
    }

    private static void trimOldest(ServerPlayer owner, Map<Integer, TagEntry> tags, int maxKeep) {
        List<TagEntry> sorted = new ArrayList<>(tags.values());
        sorted.sort(Comparator.comparingLong(TagEntry::createdTick));
        int removeCount = tags.size() - maxKeep;
        for (int i = 0; i < removeCount && i < sorted.size(); i++) {
            TagEntry entry = sorted.get(i);
            tags.remove(entry.targetId());
            despawnChain(owner, entry.chainEntityId());
        }
    }

    private static void despawnChain(ServerPlayer owner, int chainEntityId) {
        if (chainEntityId < 0 || !(owner.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getEntity(chainEntityId) instanceof BlackwhipChainEntity chain) {
            chain.deactivate();
        }
    }

    private static void updateCount(ServerPlayer owner, int count) {
        Integer last = LAST_COUNT.get(owner.getUUID());
        if (last != null && last == count) {
            return;
        }
        LAST_COUNT.put(owner.getUUID(), count);
        BodyAttachments.get(owner).setCustomFloat(owner, COUNT_PART, COUNT_KEY, count);
        BodySyncEvents.syncNow(owner);
    }
}
