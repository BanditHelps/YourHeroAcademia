package com.github.bandithelps.client.blackwhip;

import com.github.bandithelps.utils.blackwhip.BlackwhipChainAnchors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Client waist / wrap helpers for chain Blackwhip tips.
 */
public final class BlackwhipWaistBoneHelper {

    private BlackwhipWaistBoneHelper() {
    }

    public static Optional<Vec3> resolveWaistTip(Player player, float partialTick) {
        if (player == null || !player.isAlive()) {
            return Optional.empty();
        }
        float yaw;
        if (player instanceof AbstractClientPlayer) {
            yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        } else {
            yaw = player.yBodyRot;
        }
        Vec3 pos = player.getPosition(partialTick);
        double waistY = player.getBbHeight() * (player.isCrouching() ? 0.42 : 0.50);
        Vec3 fwd = Vec3.directionFromRotation(0, yaw).normalize();
        return Optional.of(pos.add(0, waistY, 0).add(fwd.scale(0.08)));
    }

    public static Optional<Vec3> resolveWaistTipClient(int entityId, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return Optional.empty();
        }
        if (mc.level.getEntity(entityId) instanceof Player player) {
            return resolveWaistTip(player, partialTick);
        }
        return Optional.empty();
    }

    /**
     * Locks the rope tip to the live wrap entry, redistributes mid joints, then grows a latch-height
     * coil from {@code wrapProgress} (0 = tip only, 1 = full wrap).
     * <p>
     * Tip is always fully locked — only the coil length animates. Lerping the tip while growing the
     * helix stacks translucent ribbon layers and causes z-fighting / muddy colors.
     *
     * @param wrapHeight fraction of target hitbox height for the wrap band (from latch tip)
     * @return number of rope joints after tip lock (before coil samples), or 0 on failure
     */
    public static int attachTipAndCoil(List<Vec3> joints, LivingEntity target, Vec3 wrist,
                                       float wrapTurns, float wrapProgress, float wrapHeight,
                                       float partialTick) {
        if (joints.size() < 2 || target == null) {
            return 0;
        }

        // Drop trailing tip proxies that sit on top of the rope tip.
        while (joints.size() > 2) {
            Vec3 last = joints.get(joints.size() - 1);
            Vec3 prev = joints.get(joints.size() - 2);
            if (last.distanceToSqr(prev) < 1.0e-4) {
                joints.remove(joints.size() - 1);
            } else {
                break;
            }
        }

        Vec3 tip = BlackwhipChainAnchors.resolveWaistEntry(target, wrist, partialTick, wrapHeight);
        BlackwhipChainAnchors.redistributeJoints(joints, wrist, tip);
        int ropeCount = joints.size();

        float wrapBlend = Mth.clamp(wrapProgress, 0.0f, 1.0f);
        if (wrapBlend <= 0.02f) {
            return ropeCount;
        }
        // Ease-out so the wrap reads as a quick catch that settles.
        wrapBlend = 1.0f - (float) Math.pow(1.0 - wrapBlend, 2.0);

        Vec3[] coil = BlackwhipChainAnchors.buildRenderCoil(
                target, wrist, BlackwhipChainAnchors.RENDER_COIL_SAMPLES, wrapTurns, partialTick, wrapHeight);
        int keep = Math.max(2, Math.round((coil.length - 1) * wrapBlend) + 1);

        Vec3 lockedTip = joints.get(joints.size() - 1);
        for (int i = 0; i < keep; i++) {
            Vec3 pt = coil[i];
            if (i == 0 && lockedTip.distanceToSqr(pt) < 0.01) {
                continue;
            }
            joints.add(pt);
        }
        return ropeCount;
    }

    /**
     * Same as {@link #attachTipAndCoil(List, LivingEntity, Vec3, float, float, float, float)} but
     * wraps an arbitrary AABB (world blocks / hovering displays).
     */
    public static int attachTipAndCoil(List<Vec3> joints, AABB bb, Vec3 wrist,
                                       float wrapTurns, float wrapProgress, float wrapHeight) {
        if (joints.size() < 2 || bb == null) {
            return 0;
        }

        while (joints.size() > 2) {
            Vec3 last = joints.get(joints.size() - 1);
            Vec3 prev = joints.get(joints.size() - 2);
            if (last.distanceToSqr(prev) < 1.0e-4) {
                joints.remove(joints.size() - 1);
            } else {
                break;
            }
        }

        Vec3 tip = BlackwhipChainAnchors.resolveWaistEntry(bb, wrist, 0.0f, wrapHeight);
        BlackwhipChainAnchors.redistributeJoints(joints, wrist, tip);
        int ropeCount = joints.size();

        float wrapBlend = Mth.clamp(wrapProgress, 0.0f, 1.0f);
        if (wrapBlend <= 0.02f) {
            return ropeCount;
        }
        wrapBlend = 1.0f - (float) Math.pow(1.0 - wrapBlend, 2.0);

        Vec3[] coil = BlackwhipChainAnchors.buildRenderCoil(
                bb, wrist, BlackwhipChainAnchors.RENDER_COIL_SAMPLES, wrapTurns, wrapHeight);
        int keep = Math.max(2, Math.round((coil.length - 1) * wrapBlend) + 1);

        Vec3 lockedTip = joints.get(joints.size() - 1);
        for (int i = 0; i < keep; i++) {
            Vec3 pt = coil[i];
            if (i == 0 && lockedTip.distanceToSqr(pt) < 0.01) {
                continue;
            }
            joints.add(pt);
        }
        return ropeCount;
    }
}
