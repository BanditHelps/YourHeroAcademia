package com.github.bandithelps.utils.blackwhip;

import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.entities.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Factory for {@link BlackwhipChainEntity} tethers (IK chain Blackwhip variant).
 */
public final class BlackwhipChainHelper {

    private BlackwhipChainHelper() {
    }

    /**
     * Spawns a deploying chain whose tip flies along the owner's eye look-ray ({@code direction})
     * and latches on swept tip contact. Controller entity is placed at the wrist for culling; tip
     * origin is resolved in {@link BlackwhipChainEntity#beginDeploy}.
     *
     * @return the chain, or {@code null} if the owner already has {@code maxKeep} active chains
     */
    public static BlackwhipChainEntity spawnFlyingChain(LivingEntity owner, Vec3 direction, double maxRange,
                                                        int segmentCount, float linkLength, float chainHp,
                                                        float thickness, int travelTicks,
                                                        int ttlTicks, double maxDistance, int maxKeep) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return null;
        }
        int keep = Math.max(1, maxKeep);
        if (BlackwhipChainEntity.countOwnedActive(owner.getId()) >= keep) {
            return null;
        }

        float link = Math.max(0.25f, linkLength);
        Vec3 wrist = BlackwhipChainAnchors.resolveOwnerWrist(owner);
        Vec3 dir = direction.lengthSqr() < 1.0e-6 ? owner.getLookAngle() : direction.normalize();
        int seed = Mth.clamp(segmentCount, BlackwhipChainEntity.MIN_SEGMENTS, BlackwhipChainEntity.MAX_SEGMENTS);
        // Start short; flight resize grows toward tip.
        int segments = Mth.clamp(seed, BlackwhipChainEntity.MIN_SEGMENTS, BlackwhipChainEntity.MAX_SEGMENTS);

        BlackwhipChainEntity chain = new BlackwhipChainEntity(ModEntities.BLACKWHIP_CHAIN.get(), level);
        chain.setOwnerId(owner.getId());
        chain.setTargetId(-1);
        chain.setPurpose(BlackwhipChainEntity.PURPOSE_TAG);
        chain.setMinSegmentSeed(seed);
        chain.setSegmentCount(segments);
        chain.setLinkLength(link);
        chain.setMaxHp(chainHp);
        chain.setHp(chainHp);
        chain.setThickness(thickness);
        chain.setTravelTicks(travelTicks);
        chain.setRetractTicks(6);
        chain.setWrapTurns(2.0f);
        chain.setLatchParams(ttlTicks, maxDistance, keep);
        applyOwnerColors(chain, owner);
        chain.setSeed(owner.getRandom().nextInt());
        chain.setPos(wrist.x, wrist.y, wrist.z);
        level.addFreshEntity(chain);
        chain.beginDeploy(dir, maxRange);
        return chain;
    }

    /**
     * Spawns a chain already pinned to a world/block point (no tip flight). Used by swing and zip.
     *
     * @param lifetimeTicks auto-retract after this many ticks; {@code 0} = until deactivated
     * @return the chain, or {@code null} if the owner is at the active-chain cap
     */
    public static BlackwhipChainEntity spawnAnchoredChain(LivingEntity owner, Vec3 anchor, BlockPos support,
                                                         int purpose, int segmentCount, float linkLength,
                                                         float chainHp, float thickness, double maxDistance,
                                                         int maxKeep, int lifetimeTicks) {
        if (!(owner.level() instanceof ServerLevel level) || anchor == null) {
            return null;
        }
        int keep = Math.max(1, maxKeep);
        // Movement ropes should not be blocked by living grab tethers.
        int active = countTowardKeep(owner.getId(), purpose);
        if (active >= keep) {
            return null;
        }

        float link = Math.max(0.25f, linkLength);
        Vec3 wrist = BlackwhipChainAnchors.resolveOwnerWrist(owner);
        double ropeDist = Math.max(0.5, wrist.distanceTo(anchor));
        int wrapJoints = BlackwhipChainAnchors.MIN_WRAP_JOINTS;
        int desired = BlackwhipChainAnchors.desiredSegmentCount(ropeDist, link, wrapJoints);
        int seed = Mth.clamp(segmentCount > 0 ? segmentCount : desired,
                BlackwhipChainEntity.MIN_SEGMENTS, BlackwhipChainEntity.MAX_SEGMENTS);
        int segments = Mth.clamp(desired, BlackwhipChainEntity.MIN_SEGMENTS, seed);

        BlackwhipChainEntity chain = new BlackwhipChainEntity(ModEntities.BLACKWHIP_CHAIN.get(), level);
        chain.setOwnerId(owner.getId());
        chain.setTargetId(-1);
        chain.setPurpose(purpose);
        chain.setMinSegmentSeed(seed);
        chain.setSegmentCount(segments);
        chain.setLinkLength(link);
        chain.setMaxHp(Math.max(1.0f, chainHp));
        chain.setHp(Math.max(1.0f, chainHp));
        chain.setThickness(thickness);
        chain.setTravelTicks(1);
        chain.setRetractTicks(5);
        chain.setWrapTurns(0.0f);
        chain.setLatchParams(0, maxDistance, keep);
        chain.setMaxRange((float) Math.max(1.0, maxDistance > 0 ? maxDistance : ropeDist));
        chain.setLifetimeTicks(lifetimeTicks);
        applyOwnerColors(chain, owner);
        chain.setSeed(owner.getRandom().nextInt());
        chain.setPos(wrist.x, wrist.y, wrist.z);
        level.addFreshEntity(chain);
        chain.latchBlock(anchor, support != null ? support : BlockPos.containing(anchor));
        return chain;
    }

    private static int countTowardKeep(int ownerId, int purpose) {
        if (purpose == BlackwhipChainEntity.PURPOSE_TAG) {
            return BlackwhipChainEntity.countOwnedActive(ownerId);
        }
        return BlackwhipChainEntity.countOwnedActiveByPurpose(ownerId, BlackwhipChainEntity.PURPOSE_SWING)
                + BlackwhipChainEntity.countOwnedActiveByPurpose(ownerId, BlackwhipChainEntity.PURPOSE_ZIP_SIMPLE)
                + BlackwhipChainEntity.countOwnedActiveByPurpose(ownerId, BlackwhipChainEntity.PURPOSE_ZIP_CHARGE);
    }

    private static void applyOwnerColors(BlackwhipChainEntity chain, LivingEntity owner) {
        if (owner instanceof Player player) {
            chain.setColors(BlackwhipColors.getCore(player), BlackwhipColors.getOuter(player), BlackwhipColors.getGlow(player));
        } else {
            chain.setColors(
                    BlackwhipChainEntity.DEFAULT_CORE,
                    BlackwhipChainEntity.DEFAULT_OUTER,
                    BlackwhipChainEntity.DEFAULT_GLOW);
        }
    }

    public static void despawn(BlackwhipChainEntity chain) {
        if (chain != null && chain.isAlive()) {
            chain.deactivate();
        }
    }
}
