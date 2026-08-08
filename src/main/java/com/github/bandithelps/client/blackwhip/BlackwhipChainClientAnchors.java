package com.github.bandithelps.client.blackwhip;

import com.github.bandithelps.utils.blackwhip.BlackwhipChainAnchors;
import net.minecraft.client.player.AbstractClientPlayer;
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

    /**
     * Animated main-hand world position for players; falls back to approximate wrist math otherwise.
     */
    public static Vec3 resolveVisualRoot(Entity owner, float partialTick) {
        if (owner instanceof AbstractClientPlayer player) {
            String part = player.getMainArm() == HumanoidArm.LEFT
                    ? ModelUtil.LEFT_ARM_PART_NAME
                    : ModelUtil.RIGHT_ARM_PART_NAME;
            return ModelUtil.getInWorldPosition(part, HAND_OFFSET, player, partialTick);
        }
        if (owner instanceof LivingEntity living) {
            return BlackwhipChainAnchors.resolveOwnerWrist(living, partialTick);
        }
        return owner.getPosition(partialTick).add(0.0, owner.getBbHeight() * 0.5, 0.0);
    }
}
