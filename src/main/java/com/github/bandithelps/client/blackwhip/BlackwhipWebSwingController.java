package com.github.bandithelps.client.blackwhip;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.network.BlackwhipWebSwingVelocityPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * PS5-style web-swing pendulum: camera-relative pump, turn assist, auto-reel with speed,
 * ground launch assist, Shift brake, Jump hop. Velocity-only constraint (no setPos).
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class BlackwhipWebSwingController {

    private static final double STRAFE_SCALE = 0.9;
    private static final double BACK_SCALE = 0.45;
    private static final double LOOK_VERTICAL_BLEND = 0.45;
    private static final double STRETCH_PULL = 0.20;
    private static final double MAX_STRETCH_RATIO = 1.14;
    private static final double BOTTOM_ENERGY = 0.018;
    private static final double JUMP_HOP = 0.16;
    private static final double BRAKE_LENGTHEN = 0.28;
    private static final double GROUND_LIFT = 0.28;
    private static final double GROUND_FORWARD = 0.22;
    private static final double PIVOT_PULL = 0.10;

    private BlackwhipWebSwingController() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ClientBlackwhipWebSwingState.isActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null) {
            return;
        }

        Vec3 anchor = ClientBlackwhipWebSwingState.getAnchor();
        double rope = ClientBlackwhipWebSwingState.getRopeLength();
        double minRope = ClientBlackwhipWebSwingState.getMinRope();
        double maxRope = ClientBlackwhipWebSwingState.getMaxRope();
        double pumpAccel = ClientBlackwhipWebSwingState.getPumpAccel();
        double turnAssist = ClientBlackwhipWebSwingState.getTurnAssist();
        double autoReel = ClientBlackwhipWebSwingState.getAutoReelRate();
        double maxSpeed = ClientBlackwhipWebSwingState.getMaxSpeed();

        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3 toAnchor = anchor.subtract(center);
        double dist = toAnchor.length();
        if (dist < 1.0e-4) {
            return;
        }
        Vec3 radial = toAnchor.scale(1.0 / dist);

        boolean brake = minecraft.options.keyShift.isDown();
        boolean jump = minecraft.options.keyJump.isDown();
        // Floor contact is NOT a jam — treating it as one caused ground drag.
        boolean wallJam = player.horizontalCollision;
        boolean ceilingJam = player.verticalCollision && !player.onGround() && player.getDeltaMovement().y > 0.0;
        boolean jammed = wallJam || ceilingJam;

        if (jammed && dist > rope) {
            rope = Math.min(maxRope, dist + 0.05);
            ClientBlackwhipWebSwingState.setRopeLength(rope);
        } else if (dist > rope * MAX_STRETCH_RATIO) {
            rope = Math.min(maxRope, dist);
            ClientBlackwhipWebSwingState.setRopeLength(rope);
        }

        Vec3 velocity = player.getDeltaMovement();
        double horizSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

        if (brake) {
            rope = Math.min(maxRope, rope + BRAKE_LENGTHEN);
            ClientBlackwhipWebSwingState.setRopeLength(rope);
            velocity = velocity.scale(ClientBlackwhipWebSwingState.getBrakeDamp());
        } else if (!jammed && autoReel > 0.0 && horizSpeed > 0.55) {
            double target = Mth.lerp(Mth.clamp((horizSpeed - 0.55) / 2.2, 0.0, 1.0), rope, minRope + 2.5);
            if (target < rope) {
                rope = Math.max(minRope, rope - autoReel);
                ClientBlackwhipWebSwingState.setRopeLength(rope);
            }
        }

        // Ground takeoff: lift off and kick forward into the pendulum instead of scraping.
        if (player.onGround() && !brake) {
            velocity = applyGroundLaunch(player, velocity, radial, anchor, center);
        }

        if (jump && !player.onGround()) {
            velocity = velocity.add(0.0, JUMP_HOP, 0.0);
        }

        // Slack: free motion until taut.
        if (dist <= rope) {
            if (!brake) {
                velocity = applyMovementPump(minecraft, player, velocity, radial, pumpAccel);
                velocity = applyTurnAssist(player, velocity, radial, turnAssist);
            }
            velocity = applyBottomEnergy(velocity, radial, center, anchor);
            velocity = capSpeed(velocity, maxSpeed);
            finishTick(player, velocity);
            return;
        }

        // Taut: strip outward radial speed + soft spring (pulls you up into the arc when grounded).
        double radialSpeed = velocity.dot(radial);
        if (radialSpeed < 0.0 && !brake) {
            velocity = velocity.subtract(radial.scale(radialSpeed));
        }
        if (!jammed && dist > rope) {
            double over = dist - rope;
            double pull = Math.min(over, 1.75) * STRETCH_PULL;
            // Prefer upward component of the pull so ground attach launches rather than drags.
            Vec3 pullVec = radial.scale(pull);
            if (player.onGround() && pullVec.y < PIVOT_PULL) {
                pullVec = new Vec3(pullVec.x, Math.max(pullVec.y, PIVOT_PULL), pullVec.z);
            }
            velocity = velocity.add(pullVec);
        }

        if (!jammed && !brake) {
            velocity = applyMovementPump(minecraft, player, velocity, radial, pumpAccel);
            velocity = applyTurnAssist(player, velocity, radial, turnAssist);
            velocity = applyBottomEnergy(velocity, radial, center, anchor);
        } else if (jammed) {
            velocity = velocity.scale(0.85);
        }

        velocity = velocity.scale(ClientBlackwhipWebSwingState.getDamping());
        velocity = capSpeed(velocity, maxSpeed);
        finishTick(player, velocity);
    }

    private static void finishTick(LocalPlayer player, Vec3 velocity) {
        player.setDeltaMovement(velocity);
        player.resetFallDistance();
        if (!player.onGround() || velocity.y > 0.05) {
            player.setOnGround(false);
        }
        // Sync real client momentum for server-side release fling.
        ClientPacketDistributor.sendToServer(new BlackwhipWebSwingVelocityPayload(velocity.x, velocity.y, velocity.z));
    }

    private static Vec3 applyGroundLaunch(LocalPlayer player, Vec3 velocity, Vec3 radial,
                                          Vec3 anchor, Vec3 center) {
        Vec3 look = player.getLookAngle();
        Vec3 flatFwd = new Vec3(look.x, 0.0, look.z);
        if (flatFwd.lengthSqr() < 1.0e-4) {
            flatFwd = new Vec3(radial.x, 0.0, radial.z);
        }
        if (flatFwd.lengthSqr() < 1.0e-4) {
            flatFwd = new Vec3(0.0, 0.0, 1.0);
        } else {
            flatFwd = flatFwd.normalize();
        }

        // Swing-out direction: perpendicular to rope in the look plane, biased upward.
        Vec3 wish = flatFwd.add(0.0, 0.65, 0.0).add(radial.scale(0.25));
        Vec3 tangent = projectOffRadial(wish, radial);
        if (tangent.lengthSqr() < 1.0e-4) {
            tangent = new Vec3(flatFwd.x, 0.55, flatFwd.z);
        }
        tangent = tangent.normalize();

        double lift = Math.max(GROUND_LIFT, radial.y * 0.35 + 0.18);
        velocity = new Vec3(
                velocity.x + tangent.x * GROUND_FORWARD + flatFwd.x * 0.08,
                Math.max(velocity.y, lift),
                velocity.z + tangent.z * GROUND_FORWARD + flatFwd.z * 0.08);

        // Slight pull toward a forward-up pivot to leave the ground.
        if (anchor.y > center.y + 1.0) {
            velocity = velocity.add(radial.scale(PIVOT_PULL));
        }
        return velocity;
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
        // Default forward intent while airborne so holding alone still builds the arc.
        if (forward == 0.0f && strafe == 0.0f && !player.onGround()) {
            forward = 0.55f;
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
        wish = wish.add(0.0, Math.max(0.15, look.y * LOOK_VERTICAL_BLEND) * Math.max(0.0f, forward), 0.0);
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

    private static Vec3 applyTurnAssist(LocalPlayer player, Vec3 velocity, Vec3 radial, double turnAssist) {
        if (turnAssist <= 0.0) {
            return velocity;
        }
        float yaw = player.getYRot();
        float last = ClientBlackwhipWebSwingState.getLastYaw();
        ClientBlackwhipWebSwingState.setLastYaw(yaw);
        if (Float.isNaN(last)) {
            return velocity;
        }
        float deltaYaw = Mth.wrapDegrees(yaw - last);
        if (Math.abs(deltaYaw) < 0.35f) {
            return velocity;
        }
        double speed = velocity.length();
        if (speed < 0.15) {
            return velocity;
        }
        Vec3 look = player.getLookAngle();
        Vec3 flatRight = new Vec3(-look.z, 0.0, look.x);
        if (flatRight.lengthSqr() < 1.0e-4) {
            return velocity;
        }
        flatRight = flatRight.normalize();
        Vec3 lean = projectOffRadial(flatRight.scale(Math.signum(deltaYaw)), radial);
        if (lean.lengthSqr() < 1.0e-4) {
            return velocity;
        }
        double strength = turnAssist * Mth.clamp(Math.abs(deltaYaw) / 12.0, 0.0, 1.0) * Mth.clamp(speed / 1.6, 0.35, 1.4);
        return velocity.add(lean.normalize().scale(strength));
    }

    private static Vec3 applyBottomEnergy(Vec3 velocity, Vec3 radial, Vec3 center, Vec3 anchor) {
        if (center.y >= anchor.y - 0.5) {
            return velocity;
        }
        double horizontalRadial = Math.sqrt(radial.x * radial.x + radial.z * radial.z);
        if (horizontalRadial < 0.45) {
            return velocity;
        }
        Vec3 tangential = projectOffRadial(velocity, radial);
        double tSpeed = tangential.length();
        if (tSpeed < 0.25 || tSpeed > 2.8) {
            return velocity;
        }
        return velocity.add(tangential.normalize().scale(BOTTOM_ENERGY));
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
