package com.github.bandithelps.client.blackwhip;

import com.github.bandithelps.utils.blackwhip.BlackwhipChainAnchors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
     * Locks the rope tip to the live interpolated target, redistributes mid joints between the
     * already-locked wrist and that tip, then appends a mid-hitbox coil rebuilt every frame.
     *
     * @return true if a coil was appended
     */
    public static boolean attachTipAndCoil(List<Vec3> joints, LivingEntity target, Vec3 wrist,
                                           float wrapTurns, float extendProgress, float partialTick) {
        if (joints.size() < 2 || target == null) {
            return false;
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

        Vec3 tip = BlackwhipChainAnchors.resolveWaistEntry(target, wrist, partialTick);
        // Blend tip toward entry while extending so the latch doesn't pop in.
        float tipBlend = Mth.clamp(extendProgress, 0.0f, 1.0f);
        tip = joints.get(joints.size() - 1).lerp(tip, tipBlend);

        BlackwhipChainAnchors.redistributeJoints(joints, wrist, tip);

        if (extendProgress < 0.45f) {
            return false;
        }

        float wrapBlend = Mth.clamp((extendProgress - 0.45f) / 0.55f, 0.0f, 1.0f);
        Vec3[] coil = BlackwhipChainAnchors.buildRenderCoil(
                target, wrist, BlackwhipChainAnchors.RENDER_COIL_SAMPLES, wrapTurns, partialTick);
        int keep = Math.max(2, Math.round((coil.length - 1) * wrapBlend) + 1);

        Vec3 lockedTip = joints.get(joints.size() - 1);
        for (int i = 0; i < keep; i++) {
            Vec3 pt = coil[i];
            if (i == 0 && lockedTip.distanceToSqr(pt) < 0.01) {
                continue;
            }
            joints.add(pt);
        }
        return true;
    }
}
