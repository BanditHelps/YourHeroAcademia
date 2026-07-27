package com.github.bandithelps.utils.blackwhip;

import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.entities.ModEntities;
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
     * Spawns a deploying chain that flies along {@code direction} and latches on tip contact.
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
