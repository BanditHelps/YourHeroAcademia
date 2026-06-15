package com.github.bandithelps.utils.blackwhip;

import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.capabilities.body.BodySyncEvents;
import com.github.bandithelps.entities.BlackwhipAnchor;
import com.github.bandithelps.entities.BlackwhipEntity;
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
 * Server-side registry of which entities a player has "tagged" with a Blackwhip tendril. Each tag owns
 * a {@link BlackwhipEntity} tether so the visual is kept in sync automatically. Mirrors the active tag
 * count into body data ({@code blackwhip_connected_count} on the chest) so it can drive a HUD bar.
 */
public final class BlackwhipTagStore {

    public static final BodyPart COUNT_PART = BodyPart.CHEST;
    public static final String COUNT_KEY = "blackwhip_connected_count";

    public record TagEntry(int targetId, long createdTick, int expireTicks, double maxDistance, int whipEntityId) {
    }

    private static final Map<UUID, Map<Integer, TagEntry>> PLAYER_TAGS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_COUNT = new ConcurrentHashMap<>();

    private BlackwhipTagStore() {
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
     * Tags a target (or refreshes its TTL if already tagged). Spawns the tether visual on first tag and
     * trims oldest tags down to {@code maxKeep} (<= 0 means unlimited).
     *
     * @return true if this was a brand-new tag, false if it merely refreshed an existing one.
     */
    public static boolean addTag(ServerPlayer owner, LivingEntity target, int expireTicks, double maxDistance,
                                 int maxKeep, float thickness, float curve, int travelTicks) {
        Map<Integer, TagEntry> tags = PLAYER_TAGS.computeIfAbsent(owner.getUUID(), k -> new ConcurrentHashMap<>());
        long now = owner.level().getGameTime();
        boolean isNew = !tags.containsKey(target.getId());

        if (!isNew) {
            TagEntry existing = tags.get(target.getId());
            tags.put(target.getId(), new TagEntry(target.getId(), now, Math.max(existing.expireTicks(), expireTicks), maxDistance, existing.whipEntityId()));
            return false;
        }

        BlackwhipEntity whip = BlackwhipHelper.spawnTether(owner, target, BlackwhipAnchor.HAND, thickness, curve, travelTicks);
        tags.put(target.getId(), new TagEntry(target.getId(), now, expireTicks, maxDistance, whip.getId()));

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
            despawnWhip(owner, entry.whipEntityId());
        }
        updateCount(owner, tags.size());
    }

    public static void clearTags(ServerPlayer owner) {
        Map<Integer, TagEntry> tags = PLAYER_TAGS.remove(owner.getUUID());
        if (tags != null) {
            for (TagEntry entry : tags.values()) {
                despawnWhip(owner, entry.whipEntityId());
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

    /** Expire stale tags by TTL/distance/death and refresh the mirrored count. Called each server tick batch. */
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
            if (dead || expired || tooFar) {
                toRemove.add(entry.targetId());
            }
        }
        for (int id : toRemove) {
            TagEntry entry = tags.remove(id);
            if (entry != null) {
                despawnWhip(owner, entry.whipEntityId());
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
            despawnWhip(owner, entry.whipEntityId());
        }
    }

    private static void despawnWhip(ServerPlayer owner, int whipEntityId) {
        if (whipEntityId < 0 || !(owner.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getEntity(whipEntityId) instanceof BlackwhipEntity whip) {
            whip.deactivate();
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
