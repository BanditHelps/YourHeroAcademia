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
 * Client-authoritative Spider-Man pendulum simulation for the local player while a {@code blackwhip_zip}
 * swing is active. Runs after the client movement tick: applies a soft rope constraint (Verlet-style
 * positional correction instead of a hard snap), strips only outward radial velocity to preserve
 * tangential momentum, lets the player pump the arc and reel in/out, and adds light damping.
 *
 * <p>Releasing the swing simply stops the constraint, so the player keeps their momentum for a natural
 * launch. Other viewers see this as ordinary player movement plus the synced rope entity.</p>
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class BlackwhipSwingController {

    private static final double MIN_ROPE = 2.0;
    private static final double MAX_ROPE = 64.0;
    private static final double REEL_SPEED = 0.3;
    private static final double PUMP_ACCEL = 0.025;
    private static final double DAMPING = 0.992;

    private BlackwhipSwingController() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ClientBlackwhipSwingState.isActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null) {
            return;
        }

        Vec3 anchor = ClientBlackwhipSwingState.getAnchor();
        double rope = ClientBlackwhipSwingState.getRopeLength();

        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3 toAnchor = anchor.subtract(center);
        double dist = toAnchor.length();
        if (dist < 1.0e-4) {
            return;
        }

        // Reel in / out.
        if (minecraft.options.keyShift.isDown()) {
            rope = Math.max(MIN_ROPE, rope - REEL_SPEED);
            ClientBlackwhipSwingState.setRopeLength(rope);
        } else if (minecraft.options.keySprint.isDown()) {
            rope = Math.min(MAX_ROPE, rope + REEL_SPEED);
            ClientBlackwhipSwingState.setRopeLength(rope);
        }

        if (dist <= rope) {
            // Slack rope: let the player free-fall / move normally.
            return;
        }

        Vec3 dir = toAnchor.scale(1.0 / dist);
        Vec3 velocity = player.getDeltaMovement();

        // Soft positional constraint toward the rope sphere.
        double over = dist - rope;
        player.setPos(
                player.getX() + dir.x * over * 0.5,
                player.getY() + dir.y * over * 0.5,
                player.getZ() + dir.z * over * 0.5
        );

        // Remove only the outward radial component, preserving tangential (swing) momentum.
        double radial = velocity.dot(dir);
        if (radial < 0.0) {
            velocity = velocity.subtract(dir.scale(radial));
        }

        // Pump the arc with forward input.
        if (minecraft.options.keyUp.isDown()) {
            Vec3 look = player.getLookAngle();
            Vec3 horizontal = new Vec3(look.x, 0, look.z);
            if (horizontal.lengthSqr() > 1.0e-4) {
                velocity = velocity.add(horizontal.normalize().scale(PUMP_ACCEL));
            }
        }

        velocity = velocity.scale(DAMPING);
        player.setDeltaMovement(velocity);
        player.resetFallDistance();
        player.setOnGround(false);
    }
}
