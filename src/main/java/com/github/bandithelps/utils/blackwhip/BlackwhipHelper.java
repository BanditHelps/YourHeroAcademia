package com.github.bandithelps.utils.blackwhip;

import com.github.bandithelps.entities.BlackwhipAnchor;
import com.github.bandithelps.entities.BlackwhipEntity;
import com.github.bandithelps.entities.BlackwhipStyle;
import com.github.bandithelps.entities.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Factory + lifecycle helpers for spawning {@link BlackwhipEntity} visuals. Every spawn reads the
 * owner's customized colors so all viewers see the correct whip color. The returned entity can be
 * stored by callers (e.g. the tag store) to deactivate it later.
 */
public final class BlackwhipHelper {

    private BlackwhipHelper() {
    }

    private static BlackwhipEntity create(ServerPlayer owner, BlackwhipStyle style) {
        ServerLevel level = (ServerLevel) owner.level();
        BlackwhipEntity whip = new BlackwhipEntity(ModEntities.BLACKWHIP.get(), level);
        whip.setStyle(style);
        whip.setOwnerId(owner.getId());
        whip.setColors(BlackwhipColors.getCore(owner), BlackwhipColors.getOuter(owner), BlackwhipColors.getGlow(owner));
        whip.setSeed(owner.getRandom().nextInt());
        whip.setPos(owner.getX(), owner.getY() + owner.getBbHeight() * 0.5, owner.getZ());
        return whip;
    }

    /** A tendril from the owner's hand to a living target, with wrap rings around it. */
    public static BlackwhipEntity spawnTether(ServerPlayer owner, Entity target, BlackwhipAnchor anchor,
                                              float thickness, float curve, int travelTicks) {
        BlackwhipEntity whip = create(owner, BlackwhipStyle.TETHER);
        whip.setAnchor(anchor);
        whip.setEndEntity(target.getId());
        whip.setThickness(thickness);
        whip.setCurve(curve);
        whip.setTravelTicks(travelTicks);
        whip.setRetractTicks(6);
        owner.level().addFreshEntity(whip);
        return whip;
    }

    /** A rope from the owner's hand to a fixed world point (anchor or swing). */
    public static BlackwhipEntity spawnAnchorRope(ServerPlayer owner, Vec3 point, BlackwhipStyle style,
                                                  float thickness, float curve, int travelTicks) {
        BlackwhipEntity whip = create(owner, style);
        whip.setAnchor(BlackwhipAnchor.HAND);
        whip.setEndPoint(point);
        whip.setThickness(thickness);
        whip.setCurve(curve);
        whip.setTravelTicks(travelTicks);
        whip.setRetractTicks(6);
        owner.level().addFreshEntity(whip);
        return whip;
    }

    /** Rings wrapped around a target with no rope (used by AOE/restrain). */
    public static BlackwhipEntity spawnWrap(ServerPlayer owner, Entity target, float thickness) {
        BlackwhipEntity whip = create(owner, BlackwhipStyle.WRAP);
        whip.setEndEntity(target.getId());
        whip.setThickness(thickness);
        whip.setTravelTicks(6);
        whip.setRetractTicks(6);
        owner.level().addFreshEntity(whip);
        return whip;
    }

    /** Procedural orbiting back tentacles (aura). */
    public static BlackwhipEntity spawnAura(ServerPlayer owner, int strands, float length, float curve,
                                            float thickness, float jagged) {
        BlackwhipEntity whip = create(owner, BlackwhipStyle.AURA);
        whip.setAnchor(BlackwhipAnchor.BACK);
        whip.setEndNone();
        whip.setStrands(strands);
        whip.setLength(length);
        whip.setCurve(curve);
        whip.setThickness(thickness);
        whip.setJaggedness(jagged);
        whip.setTravelTicks(12);
        whip.setRetractTicks(10);
        owner.level().addFreshEntity(whip);
        return whip;
    }

    /** Procedural forward shield bubble of petals. */
    public static BlackwhipEntity spawnBubble(ServerPlayer owner, int strands, float radius, float forwardOffset,
                                              float curve, float thickness, float jagged) {
        BlackwhipEntity whip = create(owner, BlackwhipStyle.BUBBLE);
        whip.setAnchor(BlackwhipAnchor.BACK);
        whip.setEndNone();
        whip.setStrands(strands);
        whip.setLength(radius);
        whip.setForwardOffset(forwardOffset);
        whip.setCurve(curve);
        whip.setThickness(thickness);
        whip.setJaggedness(jagged);
        whip.setTravelTicks(6);
        whip.setRetractTicks(7);
        owner.level().addFreshEntity(whip);
        return whip;
    }

    /** A short-lived sweeping lash arc to a forward point. */
    public static BlackwhipEntity spawnLash(ServerPlayer owner, Vec3 endPoint, float thickness, float curve, int lifetime) {
        BlackwhipEntity whip = create(owner, BlackwhipStyle.LASH);
        whip.setAnchor(BlackwhipAnchor.HAND);
        whip.setEndPoint(endPoint);
        whip.setThickness(thickness);
        whip.setCurve(curve);
        whip.setJaggedness(0.6f);
        whip.setTravelTicks(3);
        whip.setRetractTicks(4);
        whip.setLifetime(lifetime);
        owner.level().addFreshEntity(whip);
        return whip;
    }

    /** A rope from the owner to a carried block-stack entity. */
    public static BlackwhipEntity spawnBlockGrab(ServerPlayer owner, Entity stack, float thickness, float curve, int travelTicks) {
        BlackwhipEntity whip = create(owner, BlackwhipStyle.BLOCK_GRAB);
        whip.setAnchor(BlackwhipAnchor.HAND);
        whip.setEndEntity(stack.getId());
        whip.setThickness(thickness);
        whip.setCurve(curve);
        whip.setTravelTicks(travelTicks);
        whip.setRetractTicks(6);
        owner.level().addFreshEntity(whip);
        return whip;
    }

    /** Begins the retract animation; the entity discards itself afterward. */
    public static void despawn(BlackwhipEntity whip) {
        if (whip != null && whip.isAlive()) {
            whip.deactivate();
        }
    }
}
