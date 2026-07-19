package com.github.bandithelps.utils.blackwhip;

import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.entities.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Factory for {@link BlackwhipChainEntity} tethers (IK chain Blackwhip variant).
 */
public final class BlackwhipChainHelper {

    private BlackwhipChainHelper() {
    }

    public static BlackwhipChainEntity spawnChain(ServerPlayer owner, LivingEntity target,
                                                  int segmentCount, float linkLength, float chainHp,
                                                  float thickness, int travelTicks) {
        ServerLevel level = (ServerLevel) owner.level();
        float link = Math.max(0.25f, linkLength);
        Vec3 wrist = BlackwhipChainAnchors.resolveOwnerWrist(owner);
        Vec3 entry = BlackwhipChainAnchors.resolveWaistEntry(target, wrist);
        int wrap = BlackwhipChainAnchors.wrapJointCount(target);
        int byDistance = BlackwhipChainAnchors.desiredSegmentCount(wrist.distanceTo(entry), link, wrap);
        int seed = Mth.clamp(segmentCount, BlackwhipChainEntity.MIN_SEGMENTS, BlackwhipChainEntity.MAX_SEGMENTS);
        int segments = Mth.clamp(Math.max(seed, byDistance), BlackwhipChainEntity.MIN_SEGMENTS, BlackwhipChainEntity.MAX_SEGMENTS);

        BlackwhipChainEntity chain = new BlackwhipChainEntity(ModEntities.BLACKWHIP_CHAIN.get(), level);
        chain.setOwnerId(owner.getId());
        chain.setTargetId(target.getId());
        chain.setMinSegmentSeed(seed);
        chain.setSegmentCount(segments);
        chain.setLinkLength(link);
        chain.setMaxHp(chainHp);
        chain.setHp(chainHp);
        chain.setThickness(thickness);
        chain.setTravelTicks(travelTicks);
        chain.setRetractTicks(6);
        chain.setWrapTurns(2.0f);
        chain.setColors(BlackwhipColors.getCore(owner), BlackwhipColors.getOuter(owner), BlackwhipColors.getGlow(owner));
        chain.setSeed(owner.getRandom().nextInt());
        chain.setPos(wrist.x, wrist.y, wrist.z);
        level.addFreshEntity(chain);
        return chain;
    }

    public static void despawn(BlackwhipChainEntity chain) {
        if (chain != null && chain.isAlive()) {
            chain.deactivate();
        }
    }
}
