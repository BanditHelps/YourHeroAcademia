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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side registry for chain-Blackwhip tethers. Parallel to {@link BlackwhipTagStore}; uses a
 * separate body-data key so the ribbon Blackwhip HUD is not affected.
 */
public final class BlackwhipChainTagStore {

    public static final BodyPart COUNT_PART = BodyPart.CHEST;
    public static final String COUNT_KEY = "blackwhip_chain_connected_count";

    public record TagEntry(int targetId, long createdTick, int expireTicks, double maxDistance, int chainEntityId) {
    }

    private static final Map<UUID, Map<Integer, TagEntry>> PLAYER_TAGS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_COUNT = new ConcurrentHashMap<>();

    private BlackwhipChainTagStore() {
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
     * @return true if this was a brand-new tag
     */
    public static boolean addTag(ServerPlayer owner, LivingEntity target, int expireTicks, double maxDistance,
                                 int maxKeep, int segmentCount, float linkLength, float chainHp,
                                 float thickness, int travelTicks) {
        Map<Integer, TagEntry> tags = PLAYER_TAGS.computeIfAbsent(owner.getUUID(), k -> new ConcurrentHashMap<>());
        long now = owner.level().getGameTime();
        boolean isNew = !tags.containsKey(target.getId());

        if (!isNew) {
            TagEntry existing = tags.get(target.getId());
            tags.put(target.getId(), new TagEntry(target.getId(), now, Math.max(existing.expireTicks(), expireTicks),
                    maxDistance, existing.chainEntityId()));
            return false;
        }

        BlackwhipChainEntity chain = BlackwhipChainHelper.spawnChain(
                owner, target, segmentCount, linkLength, chainHp, thickness, travelTicks);
        tags.put(target.getId(), new TagEntry(target.getId(), now, expireTicks, maxDistance, chain.getId()));

        if (maxKeep > 0 && tags.size() > maxKeep) {
            trimOldest(owner, tags, maxKeep);
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
        updateCount(owner, 0);
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
            boolean tooFar = entry.maxDistance() > 0 && target != null && target.distanceTo(owner) > entry.maxDistance();
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
