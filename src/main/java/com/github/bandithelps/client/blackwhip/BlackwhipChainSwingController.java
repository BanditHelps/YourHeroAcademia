package com.github.bandithelps.client.blackwhip;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Pendulum swing driven by player movement input. WASD pumps tangential momentum.
 * Space climbs up the chain, Shift slides down. Rope constraint is velocity-based and
 * auto-slacks when jammed against blocks to avoid camera jitter.
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class BlackwhipChainSwingController {

    /** Strafe / back input strength relative to forward pump. */
    private static final double STRAFE_SCALE = 0.75;
    private static final double BACK_SCALE = 0.55;
    /** Extra radial velocity while climbing / sliding along the rope. */
    private static final double CLIMB_PULL = 0.12;
    private static final double SLIDE_PUSH = 0.10;
    /** How strongly overstretch is corrected via velocity (not teleport). */
    private static final double STRETCH_PULL = 0.18;
    /** Max stretch before we force-slack the rope instead of fighting geometry. */
    private static final double MAX_STRETCH_RATIO = 1.12;

    private BlackwhipChainSwingController() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ClientBlackwhipChainSwingState.isActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null) {
            return;
        }

        Vec3 anchor = ClientBlackwhipChainSwingState.getAnchor();
        double rope = ClientBlackwhipChainSwingState.getRopeLength();
        double minRope = ClientBlackwhipChainSwingState.getMinRope();
        double maxRope = ClientBlackwhipChainSwingState.getMaxRope();
        double reelSpeed = ClientBlackwhipChainSwingState.getReelSpeed();
        double pumpAccel = ClientBlackwhipChainSwingState.getPumpAccel();
        double maxSpeed = ClientBlackwhipChainSwingState.getMaxSpeed();

        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3 toAnchor = anchor.subtract(center);
        double dist = toAnchor.length();
        if (dist < 1.0e-4) {
            return;
        }
        Vec3 radial = toAnchor.scale(1.0 / dist); // toward anchor

        boolean climb = minecraft.options.keyJump.isDown();
        boolean slide = minecraft.options.keyShift.isDown() && !climb;
        boolean jammed = player.horizontalCollision || player.verticalCollision;

        // If wedged into geometry, immediately slack the rope to the current distance so the
        // constraint stops fighting the block (this is what caused camera shake).
        if (jammed && dist > rope) {
            rope = Math.min(maxRope, dist + 0.05);
            ClientBlackwhipChainSwingState.setRopeLength(rope);
        }

        if (climb && !jammed) {
            rope = Math.max(minRope, rope - reelSpeed);
            ClientBlackwhipChainSwingState.setRopeLength(rope);
        } else if (slide) {
            rope = Math.min(maxRope, rope + reelSpeed);
            ClientBlackwhipChainSwingState.setRopeLength(rope);
        } else if (dist > rope * MAX_STRETCH_RATIO) {
            // Extreme stretch (usually into a corner/overhang) — give rope rather than yank.
            rope = Math.min(maxRope, dist);
            ClientBlackwhipChainSwingState.setRopeLength(rope);
        }

        Vec3 velocity = player.getDeltaMovement();

        // Climb / slide along the rope; don't climb harder into a ceiling/wall.
        if (climb && !player.verticalCollision) {
            velocity = velocity.add(radial.scale(CLIMB_PULL));
        } else if (slide) {
            velocity = velocity.subtract(radial.scale(SLIDE_PUSH));
        }

        // Slack rope: free fall / move until taut.
        if (dist <= rope) {
            if (climb || slide) {
                velocity = capSpeed(velocity, maxSpeed);
                player.setDeltaMovement(velocity);
                player.resetFallDistance();
            }
            return;
        }

        // Velocity-only constraint (no setPos) — avoids collision teleport jitter.
        double over = dist - rope;
        double radialSpeed = velocity.dot(radial); // positive = moving toward anchor
        if (radialSpeed < 0.0 && !slide) {
            // Strip outward component.
            velocity = velocity.subtract(radial.scale(radialSpeed));
        }
        // Gentle spring back toward the rope length when stretched and not jammed.
        if (!jammed && over > 0.0) {
            velocity = velocity.add(radial.scale(Math.min(over, 1.5) * STRETCH_PULL));
        }

        // Movement keys supply momentum along the swing tangent.
        if (!jammed) {
            velocity = applyMovementPump(minecraft, player, velocity, radial, pumpAccel);
        } else {
            // While jammed, damp hard so we don't buzz against the block.
            velocity = velocity.scale(0.85);
        }

        velocity = velocity.scale(ClientBlackwhipChainSwingState.getDamping());
        velocity = capSpeed(velocity, maxSpeed);

        player.setDeltaMovement(velocity);
        player.resetFallDistance();
        // Don't force off-ground every tick — that fights floor/ceiling collision and jitters.
        if (!player.onGround() || climb) {
            player.setOnGround(false);
        }
    }

    private static Vec3 applyMovementPump(Minecraft minecraft, LocalPlayer player, Vec3 velocity,
                                          Vec3 radial, double pumpAccel) {
        float forward = 0.0f;
        float strafe = 0.0f;
        if (minecraft.options.keyUp.isDown()) {
            forward += 1.0f;
        }
        if (minecraft.options.keyDown.isDown()) {
            forward -= 1.0f;
        }
        if (minecraft.options.keyLeft.isDown()) {
            strafe += 1.0f;
        }
        if (minecraft.options.keyRight.isDown()) {
            strafe -= 1.0f;
        }
        if (forward == 0.0f && strafe == 0.0f) {
            return velocity;
        }

        Vec3 look = player.getLookAngle();
        Vec3 flatFwd = new Vec3(look.x, 0.0, look.z);
        if (flatFwd.lengthSqr() < 1.0e-4) {
            flatFwd = new Vec3(0.0, 0.0, 1.0);
        } else {
            flatFwd = flatFwd.normalize();
        }
        Vec3 flatRight = new Vec3(-flatFwd.z, 0.0, flatFwd.x);

        double fwdScale = forward >= 0.0f ? 1.0 : BACK_SCALE;
        Vec3 wish = flatFwd.scale(forward * fwdScale).add(flatRight.scale(strafe * STRAFE_SCALE));
        if (wish.lengthSqr() < 1.0e-4) {
            return velocity;
        }
        wish = wish.normalize();

        Vec3 tangent = projectOffRadial(wish, radial);
        if (tangent.lengthSqr() < 1.0e-4) {
            return velocity;
        }
        return velocity.add(tangent.normalize().scale(pumpAccel));
    }

    private static Vec3 projectOffRadial(Vec3 v, Vec3 radial) {
        return v.subtract(radial.scale(v.dot(radial)));
    }

    private static Vec3 capSpeed(Vec3 velocity, double maxSpeed) {
        double speed = velocity.length();
        if (speed > maxSpeed && speed > 1.0e-6) {
            return velocity.scale(maxSpeed / speed);
        }
        return velocity;
    }
}
