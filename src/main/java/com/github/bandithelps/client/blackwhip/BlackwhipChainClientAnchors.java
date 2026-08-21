package com.github.bandithelps.client.blackwhip;

import com.github.bandithelps.utils.blackwhip.BlackwhipChainAnchors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.client.util.ModelUtil;
import org.joml.Vector3f;

/**
 * Client visual attach for Blackwhip chains. Uses Palladium's animated arm-bone world position
 * (same path as energy beams / webswing) so the rope root tracks PlayerAnim poses.
 */
public final class BlackwhipChainClientAnchors {

    /** Matches palladium swinging web beams: voxel offset [0, -10, 0] → (0, 0.625, 0). */
    private static final Vector3f HAND_OFFSET = new Vector3f(0.0f, 0.625f, 0.0f);

    private BlackwhipChainClientAnchors() {
    }

    public static Vec3 resolveVisualRoot(Entity owner, float partialTick, boolean fromBack) {
        HumanoidArm arm = owner instanceof LivingEntity living ? living.getMainArm() : HumanoidArm.RIGHT;
        return resolveVisualRoot(owner, partialTick, fromBack, arm);
    }

    public static Vec3 resolveVisualRoot(Entity owner, float partialTick, boolean fromBack, HumanoidArm arm) {
        if (fromBack) {
            return BlackwhipChainAnchors.resolveOwnerBack(owner, partialTick);
        }
        HumanoidArm useArm = arm != null
                ? arm
                : (owner instanceof LivingEntity living ? living.getMainArm() : HumanoidArm.RIGHT);
        if (owner instanceof AbstractClientPlayer player) {
            // First-person does not pose the left arm like third-person PlayerAnim, so the
            // third-person bone sits up at the shoulder. Park FP roots on the camera hands.
            if (isLocalFirstPerson(player)) {
                return BlackwhipChainAnchors.resolveFirstPersonHand(player, partialTick, useArm);
            }
            String part = useArm == HumanoidArm.LEFT
                    ? ModelUtil.LEFT_ARM_PART_NAME
                    : ModelUtil.RIGHT_ARM_PART_NAME;
            return ModelUtil.getInWorldPosition(part, HAND_OFFSET, player, partialTick);
        }
        if (owner instanceof LivingEntity living) {
            return BlackwhipChainAnchors.resolveOwnerWrist(living, partialTick, useArm);
        }
        return owner.getPosition(partialTick).add(0.0, owner.getBbHeight() * 0.5, 0.0);
    }

    private static boolean isLocalFirstPerson(AbstractClientPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer local = minecraft.player;
        return local != null
                && local == player
                && minecraft.options.getCameraType().isFirstPerson();
    }
}
