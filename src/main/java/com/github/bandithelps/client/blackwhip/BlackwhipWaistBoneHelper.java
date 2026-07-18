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
 * Client waist / wrap polish for chain Blackwhip tips.
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
        double waistY = player.getBbHeight() * (player.isCrouching() ? 0.42 : 0.48);
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
     * Pulls the last wrap joints onto a client torso helix so player wraps read clearly.
     */
    public static void polishWrapJoints(List<Vec3> joints, int wrapJoints, LivingEntity target, Vec3 fromOwner, float partialTick) {
        if (joints.size() < 3 || wrapJoints < 2 || target == null) {
            return;
        }
        Vec3 ownerApprox = fromOwner;
        Vec3[] helix = BlackwhipChainAnchors.buildWaistHelix(target, ownerApprox, wrapJoints, 1.6f);
        int n = joints.size();
        int start = Math.max(1, n - wrapJoints);
        for (int i = 0; i < wrapJoints; i++) {
            int ji = start + i;
            if (ji >= n) {
                break;
            }
            Vec3 helixPt = helix[Math.min(i, helix.length - 1)];
            // Strong blend toward helix for a visible coil.
            joints.set(ji, joints.get(ji).lerp(helixPt, 0.75));
        }
    }
}
