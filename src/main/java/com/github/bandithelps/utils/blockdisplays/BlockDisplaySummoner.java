package com.github.bandithelps.utils.blockdisplays;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public class BlockDisplaySummoner {

    private static final double INITIAL_RADIUS = 0.25;

    private static final Vector3f CENTER_OFFSET_VECTOR = new Vector3f(-0.25f, 0, -0.2f); // Because for some reason, it is not centered
    private static final List<PendingTransform> PENDING_TRANSFORMS = new ArrayList<>();

    private record PendingTransform(BetterBlockDisplay display, Vector3f translation, Vector3f scale, int applyAtTick) {}

    private static BlockState randomPaletteBlock(RandomSource random, List<BlockState> palette) {
        if (palette == null || palette.isEmpty()) {
            return Blocks.STONE.defaultBlockState();
        }
        return palette.get(random.nextInt(palette.size()));
    }

    public static void summonShockwave(
            ServerLevel level,
            ServerPlayer player,
            float endRadius,
            int tickSpeed,
            double density,
            List<BlockState> palette,
            Vector3f locationOffset,
            Vector3f rotationOffset,
            Vector3f initialScale,
            Vector3f finalScale,
            int lifetime,
            boolean randomDecay,
            boolean randomRotation,
            boolean useRelative) {

        RandomSource random = player.getRandom();

        double centerX, centerY, centerZ;
        Quaternionf rotation, playerRotation;

        // If using relative coordinates, it is the same as doing ^ ^ ^ instead of ~ ~ ~
        if (useRelative) {
            Vec3 forward = player.getLookAngle().normalize();

            // Up
            Vec3 up = new Vec3(0, 1, 0);

            // Right
            Vec3 right = forward.cross(up).normalize();

            // recompute up so it is the real one
            up = right.cross(forward).normalize();

            double relativeX = locationOffset.x;
            double relativeY = locationOffset.y;
            double relativeZ = locationOffset.z;

            Vec3 offset =
                    right.scale(relativeX)
                            .add(up.scale(relativeY))
                                    .add(forward.scale(relativeZ));

            centerX = player.getX() + offset.x;
            centerY = player.getY() + offset.y;
            centerZ = player.getZ() + offset.z;

            playerRotation = new Quaternionf()
                    .rotateY((float)Math.toRadians(-player.getYRot())) // yaw
                    .rotateX((float) Math.toRadians(player.getXRot())); // pitch

            rotation = new Quaternionf()
                    .rotateXYZ(
                            (float)Math.toRadians(rotationOffset.x()),
                            (float)Math.toRadians(rotationOffset.y()),
                            (float)Math.toRadians(rotationOffset.z())
                    );

            rotation = new Quaternionf(playerRotation).mul(rotation);


        } else {
            centerX = player.getX() + locationOffset.get(0);
            centerY = player.getY() + locationOffset.get(1);
            centerZ = player.getZ() + locationOffset.get(2);

            rotation = new Quaternionf()
                    .rotateXYZ(
                            (float)Math.toRadians(rotationOffset.x()),
                            (float)Math.toRadians(rotationOffset.y()),
                            (float)Math.toRadians(rotationOffset.z())
                    );
        }



        double endPoint = 2 * Math.PI; // Full rotation of the circle
        double step = endPoint / density;





        // h = horizontal distance from the center - center point of x
        // k = vertical distance from the center - center point of z
        // x = h + r*cos(theta)
        // z = k + r * sin(theta)

        // Spawn on the inner ring and apply transform one tick later.
        // This gives clients a true "previous" state to interpolate from.
        for (double theta = 0; theta < endPoint; theta += step) {
            BetterBlockDisplay display = new BetterBlockDisplay(EntityType.BLOCK_DISPLAY, level);

            // Define these offsets in able to rotate them by the rotational offset passed above
            Vector3f beginningOffset = new Vector3f(
                    (float)(INITIAL_RADIUS * Math.cos(theta)),
                    0f,
                    (float)(INITIAL_RADIUS * Math.sin(theta))
            );

            Vector3f endingOffset = new Vector3f(
                    (float)(endRadius * Math.cos(theta)),
                    0f,
                    (float)(endRadius * Math.sin(theta))
            );

            beginningOffset.rotate(rotation);
            endingOffset.rotate(rotation);

            double initialX = centerX + beginningOffset.x;
            double initialY = centerY + beginningOffset.y;
            double initialZ = centerZ + beginningOffset.z;

            double endX = centerX + endingOffset.x;;
            double endY = centerY + endingOffset.y;
            double endZ = centerZ + endingOffset.z;;

            display.setPos(initialX, initialY, initialZ);
            display.setBlock(randomPaletteBlock(random, palette));
            display.setScale(initialScale);
            display.setTranslation(new Vector3f(CENTER_OFFSET_VECTOR));

            if (randomRotation) {
                display.setRightRotation(new Quaternionf(random.nextDouble(),random.nextDouble(),random.nextDouble(),0.5));
            }

            display.setInterpolation(tickSpeed);


            if (randomDecay) {
                display.setLifetime(random.nextInt(lifetime));
            } else {
                display.setLifetime(lifetime);
            }


            Vector3f startPos = new Vector3f((float)initialX, (float)initialY, (float)initialZ);
            Vector3f endPos = new Vector3f((float)endX, (float)endY, (float)endZ);

            Vector3f translation = endPos.sub(startPos, new Vector3f()); // the movement required to get from the inner radius to the outer radius


            level.addFreshEntity(display);

            PENDING_TRANSFORMS.add(new PendingTransform(display, translation, finalScale,level.getServer().getTickCount() + 1));
        }
    }

    public static void summonEmitterDisplay(
            ServerLevel level,
            Vec3 position,
            int interpolationTicks,
            List<BlockState> palette,
            Vector3f initialScale,
            Vector3f finalScale,
            Vector3f driftOffset,
            int lifetime,
            boolean randomDecay,
            boolean randomRotation) {
        if (level == null || position == null) {
            return;
        }

        RandomSource random = level.getRandom();
        BetterBlockDisplay display = new BetterBlockDisplay(EntityType.BLOCK_DISPLAY, level);
        display.setPos(position.x, position.y, position.z);
        display.setBlock(randomPaletteBlock(random, palette));
        display.setScale(initialScale);
        display.setTranslation(new Vector3f(CENTER_OFFSET_VECTOR));

        if (randomRotation) {
            display.setRightRotation(new Quaternionf(random.nextFloat(), random.nextFloat(), random.nextFloat(), 0.5f));
        }

        display.setInterpolation(Math.max(0, interpolationTicks));

        if (randomDecay) {
            display.setLifetime(Math.max(1, random.nextInt(Math.max(1, lifetime))));
        } else {
            display.setLifetime(Math.max(1, lifetime));
        }

        level.addFreshEntity(display);
        PENDING_TRANSFORMS.add(
                new PendingTransform(
                        display,
                        driftOffset == null ? new Vector3f() : new Vector3f(driftOffset),
                        finalScale == null ? new Vector3f(1.0f, 1.0f, 1.0f) : new Vector3f(finalScale),
                        level.getServer().getTickCount() + 1
                )
        );
    }





    // TODO Summon Implosion

    /**
     * We have to schedule the block displays update of the "start_interpolation" value, because otherwise it doesn't move
     * @param event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_TRANSFORMS.isEmpty()) {
            return;
        }

        int tick = event.getServer().getTickCount();
        Iterator<PendingTransform> iterator = PENDING_TRANSFORMS.iterator();
        while (iterator.hasNext()) {
            PendingTransform pending = iterator.next();
            if (tick < pending.applyAtTick()) {
                continue;
            }

            if (!pending.display().isRemoved()) {
                pending.display().setTranslation(new Vector3f(CENTER_OFFSET_VECTOR).add(pending.translation()));
                pending.display().setScale(pending.scale());
                pending.display().startInterpolation();
            }

            iterator.remove();
        }
    }
}
