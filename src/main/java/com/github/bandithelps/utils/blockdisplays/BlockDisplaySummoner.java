package com.github.bandithelps.utils.blockdisplays;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.blockdisplayanims.BDTrailAbility;
import com.github.bandithelps.entities.ModEntities;
import com.github.bandithelps.entities.RgbaDisplayEntity;
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
    private static final int FILLED_DOME_IDLE_MIN_INTERVAL_TICKS = 14;
    private static final int FILLED_DOME_IDLE_MAX_INTERVAL_TICKS = 28;
    private static final float FILLED_DOME_IDLE_MIN_DEGREES = 0.8f;
    private static final float FILLED_DOME_IDLE_MAX_DEGREES = 1.9f;
    private static final int TRANSFORM_APPLY_DELAY_TICKS = 2;

    private static final Vector3f CENTER_OFFSET_VECTOR = new Vector3f(-0.25f, 0, -0.2f); // Because for some reason, it is not centered
    private static final List<PendingTransform> PENDING_TRANSFORMS = new ArrayList<>();
    private static final List<PendingRgbaTransform> PENDING_RGBA_TRANSFORMS = new ArrayList<>();

    private record PendingTransform(BetterBlockDisplay display, Vector3f translation, Vector3f scale, int applyAtTick) {}
    private record PendingRgbaTransform(RgbaDisplayEntity display, Vector3f translation, Vector3f scale, int applyAtTick, int interpolationTicks) {}

    private static BlockState randomPaletteBlock(RandomSource random, List<BlockState> palette) {
        if (palette == null || palette.isEmpty()) {
            return Blocks.STONE.defaultBlockState();
        }
        return palette.get(random.nextInt(palette.size()));
    }

    private static BlockState resolveDisplayBlock(RandomSource random, List<BlockState> palette, BlockDisplayVisualOptions visualOptions) {
        if (visualOptions != null) {
            var override = visualOptions.getFaceColorBlockStateOverride();
            if (override.isPresent()) {
                return override.get();
            }
        }
        return randomPaletteBlock(random, palette);
    }

    private static void applyVisualOptions(BetterBlockDisplay display, BlockDisplayVisualOptions visualOptions) {
        if (visualOptions == null) {
            return;
        }
        visualOptions.applyTo(display);
    }

    private static boolean shouldUseRgbaRenderer(BlockDisplayVisualOptions visualOptions) {
        return visualOptions != null && visualOptions.getRgbaArgb().isPresent();
    }

    private static RgbaDisplayEntity createRgbaDisplay(
            ServerLevel level,
            RandomSource random,
            double x,
            double y,
            double z,
            Vector3f initialScale,
            int lifetime,
            boolean randomDecay,
            boolean randomRotation,
            BlockDisplayVisualOptions visualOptions) {
        RgbaDisplayEntity display = new RgbaDisplayEntity(ModEntities.RGBA_DISPLAY.get(), level);
        display.setPos(x, y, z);
        display.setScale(initialScale == null ? new Vector3f(1.0f, 1.0f, 1.0f) : new Vector3f(initialScale));
        display.setArgbColor(visualOptions.getRgbaArgb().orElse(0xFFFFFFFF));
        display.setBlendMode(visualOptions.getRgbaBlendMode());

        if (randomRotation) {
            display.setRightRotation(randomUnitRotation(random));
        }

        if (randomDecay) {
            display.setLifetime(Math.max(1, random.nextInt(Math.max(1, lifetime))));
        } else {
            display.setLifetime(Math.max(1, lifetime));
        }
        return display;
    }

    private static Quaternionf randomUnitRotation(RandomSource random) {
        Quaternionf rotation = new Quaternionf(
                random.nextFloat() * 2.0f - 1.0f,
                random.nextFloat() * 2.0f - 1.0f,
                random.nextFloat() * 2.0f - 1.0f,
                random.nextFloat() * 2.0f - 1.0f
        );
        if (rotation.lengthSquared() < 1.0E-6f) {
            return new Quaternionf();
        }
        return rotation.normalize();
    }

    private static int resolvePointCount(double requestedDensity, double radius, int minPoints) {
        if (!Double.isFinite(requestedDensity)) {
            return minPoints;
        }

        int computedPoints = (int) Math.round(Math.max(0.0, requestedDensity) * Math.max(0.0, radius));
        return Math.max(minPoints, computedPoints);
    }

    private static int[] resolveDomeShellSteps(double requestedDensity, double radius) {
        int shellPoints = resolvePointCount(requestedDensity, radius, 32);
        int verticalSteps = Math.max(4, (int) Math.round(Math.sqrt(shellPoints / 2.0)));
        int horizontalSteps = Math.max(8, (int) Math.round((double) shellPoints / verticalSteps));
        return new int[]{verticalSteps, horizontalSteps};
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
            boolean useRelative,
            BlockDisplayVisualOptions visualOptions) {

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

            Vector3f startPos = new Vector3f((float)initialX, (float)initialY, (float)initialZ);
            Vector3f endPos = new Vector3f((float)endX, (float)endY, (float)endZ);

            Vector3f translation = endPos.sub(startPos, new Vector3f()); // the movement required to get from the inner radius to the outer radius

            if (shouldUseRgbaRenderer(visualOptions)) {
                RgbaDisplayEntity rgbaDisplay = createRgbaDisplay(
                        level,
                        random,
                        initialX,
                        initialY,
                        initialZ,
                        initialScale,
                        lifetime,
                        randomDecay,
                        randomRotation,
                        visualOptions
                );
                level.addFreshEntity(rgbaDisplay);
                PENDING_RGBA_TRANSFORMS.add(new PendingRgbaTransform(rgbaDisplay, translation, finalScale, level.getServer().getTickCount() + TRANSFORM_APPLY_DELAY_TICKS, tickSpeed));
                continue;
            }

            BetterBlockDisplay display = new BetterBlockDisplay(EntityType.BLOCK_DISPLAY, level);
            display.setPos(initialX, initialY, initialZ);
            display.setBlock(resolveDisplayBlock(random, palette, visualOptions));
            display.setScale(initialScale);
            display.setTranslation(new Vector3f(CENTER_OFFSET_VECTOR));
            if (randomRotation) {
                display.setRightRotation(randomUnitRotation(random));
            }
            display.setInterpolation(tickSpeed);
            applyVisualOptions(display, visualOptions);
            if (randomDecay) {
                display.setLifetime(random.nextInt(lifetime));
            } else {
                display.setLifetime(lifetime);
            }

            level.addFreshEntity(display);

            PENDING_TRANSFORMS.add(new PendingTransform(display, translation, finalScale,level.getServer().getTickCount() + TRANSFORM_APPLY_DELAY_TICKS));
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
            boolean randomRotation,
            BlockDisplayVisualOptions visualOptions) {
        if (level == null || position == null) {
            return;
        }

        RandomSource random = level.getRandom();
        if (shouldUseRgbaRenderer(visualOptions)) {
            RgbaDisplayEntity rgbaDisplay = createRgbaDisplay(
                    level,
                    random,
                    position.x,
                    position.y,
                    position.z,
                    initialScale,
                    lifetime,
                    randomDecay,
                    randomRotation,
                    visualOptions
            );
            level.addFreshEntity(rgbaDisplay);
            PENDING_RGBA_TRANSFORMS.add(
                    new PendingRgbaTransform(
                            rgbaDisplay,
                            driftOffset == null ? new Vector3f() : new Vector3f(driftOffset),
                            finalScale == null ? new Vector3f(1.0f, 1.0f, 1.0f) : new Vector3f(finalScale),
                            level.getServer().getTickCount() + TRANSFORM_APPLY_DELAY_TICKS,
                            Math.max(1, interpolationTicks)
                    )
            );
            return;
        }

        BetterBlockDisplay display = new BetterBlockDisplay(EntityType.BLOCK_DISPLAY, level);
        display.setPos(position.x, position.y, position.z);
        display.setBlock(resolveDisplayBlock(random, palette, visualOptions));
        display.setScale(initialScale);
        display.setTranslation(new Vector3f(CENTER_OFFSET_VECTOR));

        if (randomRotation) {
            display.setRightRotation(randomUnitRotation(random));
        }

        display.setInterpolation(Math.max(0, interpolationTicks));
        applyVisualOptions(display, visualOptions);

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
                        level.getServer().getTickCount() + TRANSFORM_APPLY_DELAY_TICKS
                )
        );
    }


    public static void summonHollowDome(
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
            boolean useRelative,
            BlockDisplayVisualOptions visualOptions
    ) {

        RandomSource random = player.getRandom();

        double centerX = player.getX();
        double centerY = player.getY();
        double centerZ = player.getZ();


        int[] shellSteps = resolveDomeShellSteps(density, endRadius);
        int verticalSteps = shellSteps[0];
        int horizontalSteps = shellSteps[1];


        // Track the vertical slice
            // horizontal slices ->
        for (int i = 0; i <= verticalSteps; i++) {
            double theta = ((double) i / verticalSteps) * Math.PI / 2;

            for (int j = 0; j < horizontalSteps; j++) {
                double phi = ((double) j / horizontalSteps) * 2 * Math.PI;

                double x = centerX + endRadius * Math.sin(theta) * Math.cos(phi);
                double y = centerY + endRadius * Math.sin(theta) * Math.sin(phi);
                double z = centerZ + endRadius * Math.cos(theta);

                double dx = x - centerX;
                double dy = y - centerY;
                double dz = z - centerZ;

                double newY = dz;
                double newZ = dy;

                Vector3f startPos = new Vector3f((float)centerX, (float)centerY, (float)centerZ);
                Vector3f endPos = new Vector3f((float) ((float)centerX + dx), (float) ((float)centerY + newY), (float) ((float)centerZ + newZ));
                Vector3f translation = endPos.sub(startPos, endPos);

                if (shouldUseRgbaRenderer(visualOptions)) {
                    RgbaDisplayEntity rgbaDisplay = createRgbaDisplay(
                            level,
                            random,
                            centerX,
                            centerY,
                            centerZ,
                            initialScale,
                            lifetime,
                            randomDecay,
                            randomRotation,
                            visualOptions
                    );
                    level.addFreshEntity(rgbaDisplay);
                    PENDING_RGBA_TRANSFORMS.add(new PendingRgbaTransform(rgbaDisplay, translation, finalScale, level.getServer().getTickCount() + TRANSFORM_APPLY_DELAY_TICKS, tickSpeed));
                    continue;
                }

                BetterBlockDisplay display = new BetterBlockDisplay(EntityType.BLOCK_DISPLAY, level);
                display.setPos(centerX, centerY, centerZ);
                display.setBlock(resolveDisplayBlock(random, palette, visualOptions));
                display.setScale(initialScale);
                display.setLifetime(200);
                display.setInterpolation(tickSpeed);
                applyVisualOptions(display, visualOptions);

                if (randomRotation) {
                    display.setRightRotation(randomUnitRotation(random));
                }

                level.addFreshEntity(display);
                PENDING_TRANSFORMS.add(new PendingTransform(display, translation, finalScale,level.getServer().getTickCount() + TRANSFORM_APPLY_DELAY_TICKS));
            }
        }

    }

    //TODO make an anti-nametag field
    public static void summonFilledDome(
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
            boolean useRelative,
            BlockDisplayVisualOptions visualOptions
    ) {

        RandomSource random = player.getRandom();

        double centerX = player.getX();
        double centerY = player.getY();
        double centerZ = player.getZ();

        int totalPoints = resolvePointCount(density, endRadius, 1);


        for (int i = 0; i < totalPoints; i++) {
            double u = random.nextDouble();
            double v = random.nextDouble();

            double theta = Math.acos(1 - u); // vertical pos
            double phi = 2 * Math.PI * v; // horizontal pos

            double radius = endRadius * Math.cbrt(random.nextDouble());

            double x = centerX + radius * Math.sin(theta) * Math.cos(phi);
            double y = centerY + radius * Math.sin(theta) * Math.sin(phi);
            double z = centerZ + radius * Math.cos(theta);

            double dx = x - centerX;
            double dy = y - centerY;
            double dz = z - centerZ;

            double newY = dz;
            double newZ = dy;

            Vector3f startPos = new Vector3f((float)centerX, (float)centerY, (float)centerZ);
            Vector3f endPos = new Vector3f((float) ((float)centerX + dx), (float) ((float)centerY + newY), (float) ((float)centerZ + newZ));
            Vector3f translation = endPos.sub(startPos, endPos);

            if (shouldUseRgbaRenderer(visualOptions)) {
                RgbaDisplayEntity rgbaDisplay = createRgbaDisplay(
                        level,
                        random,
                        centerX,
                        centerY,
                        centerZ,
                        initialScale,
                        lifetime,
                        randomDecay,
                        randomRotation,
                        visualOptions
                );
                rgbaDisplay.setLifetime(randomDecay ? lifetime + random.nextInt(60) : lifetime);
                rgbaDisplay.enableIdleRotation(
                        tickSpeed + 1,
                        FILLED_DOME_IDLE_MIN_INTERVAL_TICKS,
                        FILLED_DOME_IDLE_MAX_INTERVAL_TICKS,
                        FILLED_DOME_IDLE_MIN_DEGREES,
                        FILLED_DOME_IDLE_MAX_DEGREES
                );
                level.addFreshEntity(rgbaDisplay);
                PENDING_RGBA_TRANSFORMS.add(new PendingRgbaTransform(rgbaDisplay, translation, finalScale, level.getServer().getTickCount() + TRANSFORM_APPLY_DELAY_TICKS, tickSpeed));
                continue;
            }

            BetterBlockDisplay display = new BetterBlockDisplay(EntityType.BLOCK_DISPLAY, level);
            display.setPos(centerX, centerY, centerZ);
            display.setBlock(resolveDisplayBlock(random, palette, visualOptions));
            display.setScale(initialScale);

            if (randomDecay) {
                display.setLifetime(lifetime + random.nextInt(60));
            } else {
                display.setLifetime(lifetime);
            }

            display.setInterpolation(tickSpeed);
            applyVisualOptions(display, visualOptions);

            if (randomRotation) {
                display.setRightRotation(randomUnitRotation(random));
            }

            display.enableIdleRotation(
                    tickSpeed + 1,
                    FILLED_DOME_IDLE_MIN_INTERVAL_TICKS,
                    FILLED_DOME_IDLE_MAX_INTERVAL_TICKS,
                    FILLED_DOME_IDLE_MIN_DEGREES,
                    FILLED_DOME_IDLE_MAX_DEGREES
            );

            level.addFreshEntity(display);
            PENDING_TRANSFORMS.add(new PendingTransform(display, translation, finalScale,level.getServer().getTickCount() + TRANSFORM_APPLY_DELAY_TICKS));
        }

        int[] shellSteps = resolveDomeShellSteps(density, endRadius);
        int verticalSteps = shellSteps[0];
        int horizontalSteps = shellSteps[1];

        // Track the vertical slice
        // horizontal slices ->
        for (int i = 0; i <= verticalSteps; i++) {
            double theta = ((double) i / verticalSteps) * Math.PI / 2;

            for (int j = 0; j < horizontalSteps; j++) {
                double phi = ((double) j / horizontalSteps) * 2 * Math.PI;

                double x = centerX + endRadius * Math.sin(theta) * Math.cos(phi);
                double y = centerY + endRadius * Math.sin(theta) * Math.sin(phi);
                double z = centerZ + endRadius * Math.cos(theta);

                double dx = x - centerX;
                double dy = y - centerY;
                double dz = z - centerZ;

                double newY = dz;
                double newZ = dy;

                Vector3f startPos = new Vector3f((float)centerX, (float)centerY, (float)centerZ);
                Vector3f endPos = new Vector3f((float) ((float)centerX + dx), (float) ((float)centerY + newY), (float) ((float)centerZ + newZ));
                Vector3f translation = endPos.sub(startPos, endPos);

                if (shouldUseRgbaRenderer(visualOptions)) {
                    RgbaDisplayEntity rgbaDisplay = createRgbaDisplay(
                            level,
                            random,
                            centerX,
                            centerY,
                            centerZ,
                            initialScale,
                            lifetime,
                            randomDecay,
                            randomRotation,
                            visualOptions
                    );
                    rgbaDisplay.setLifetime(randomDecay ? lifetime + random.nextInt(60) : lifetime);
                    rgbaDisplay.enableIdleRotation(
                            tickSpeed + 1,
                            FILLED_DOME_IDLE_MIN_INTERVAL_TICKS,
                            FILLED_DOME_IDLE_MAX_INTERVAL_TICKS,
                            FILLED_DOME_IDLE_MIN_DEGREES,
                            FILLED_DOME_IDLE_MAX_DEGREES
                    );
                    level.addFreshEntity(rgbaDisplay);
                    PENDING_RGBA_TRANSFORMS.add(new PendingRgbaTransform(rgbaDisplay, translation, finalScale, level.getServer().getTickCount() + TRANSFORM_APPLY_DELAY_TICKS, tickSpeed));
                    continue;
                }

                BetterBlockDisplay display = new BetterBlockDisplay(EntityType.BLOCK_DISPLAY, level);
                display.setPos(centerX, centerY, centerZ);
                display.setBlock(resolveDisplayBlock(random, palette, visualOptions));
                display.setScale(initialScale);
                if (randomDecay) {
                    display.setLifetime(lifetime + random.nextInt(60));
                } else {
                    display.setLifetime(lifetime);
                }
                display.setInterpolation(tickSpeed);
                applyVisualOptions(display, visualOptions);

                if (randomRotation) {
                    display.setRightRotation(randomUnitRotation(random));
                }

                display.enableIdleRotation(
                        tickSpeed + 1,
                        FILLED_DOME_IDLE_MIN_INTERVAL_TICKS,
                        FILLED_DOME_IDLE_MAX_INTERVAL_TICKS,
                        FILLED_DOME_IDLE_MIN_DEGREES,
                        FILLED_DOME_IDLE_MAX_DEGREES
                );

                level.addFreshEntity(display);
                PENDING_TRANSFORMS.add(new PendingTransform(display, translation, finalScale,level.getServer().getTickCount() + TRANSFORM_APPLY_DELAY_TICKS));
            }
        }

    }


    public static void summonTrailDisplay(
            ServerLevel level,
            Vec3 position,
            Vector3f locationOffset,
            Vector3f rotationOffset,
            Vector3f initialScale,
            Vector3f finalScale,
            List<BlockState> palette,
            int interpolationTicks,
            int lifetime,
            boolean randomDecay,
            boolean randomRotation,
            boolean relative,
            BDTrailAbility.IdleRotationConfig idleConfig,
            BlockDisplayVisualOptions visualOptions) {

        if (level == null || position == null) {
            return;
        }

        RandomSource random = level.getRandom();

        double x = position.x;
        double y = position.y;
        double z = position.z;

        Vector3f startPos = new Vector3f((float) x, (float) y, (float) z);
        Vector3f endPos = new Vector3f(
                (float) (x + locationOffset.x()),
                (float) (y + locationOffset.y()),
                (float) (z + locationOffset.z())
        );
        Vector3f translation = endPos.sub(startPos, new Vector3f());

        if (shouldUseRgbaRenderer(visualOptions)) {
            RgbaDisplayEntity rgbaDisplay = createRgbaDisplay(
                    level,
                    random,
                    x,
                    y,
                    z,
                    initialScale,
                    lifetime,
                    randomDecay,
                    randomRotation,
                    visualOptions
            );
            rgbaDisplay.setLifetime(randomDecay ? lifetime + random.nextInt(60) : lifetime);
            rgbaDisplay.enableIdleRotation(
                    interpolationTicks + 1,
                    idleConfig.intervalMin,
                    idleConfig.intervalMax,
                    idleConfig.degreesMin,
                    idleConfig.degreesMax
            );
            level.addFreshEntity(rgbaDisplay);
            PENDING_RGBA_TRANSFORMS.add(new PendingRgbaTransform(rgbaDisplay, translation, finalScale, level.getServer().getTickCount() + TRANSFORM_APPLY_DELAY_TICKS, interpolationTicks));
            return;
        }

        BetterBlockDisplay display = new BetterBlockDisplay(EntityType.BLOCK_DISPLAY, level);
        display.setPos(x, y, z);
        display.setBlock(resolveDisplayBlock(random, palette, visualOptions));
        display.setScale(initialScale);
        display.setTranslation(new Vector3f(CENTER_OFFSET_VECTOR));

        if (randomRotation) {
            display.setRightRotation(randomUnitRotation(random));
        }

        display.setInterpolation(Math.max(0, interpolationTicks));
        applyVisualOptions(display, visualOptions);

        if (randomDecay) {
            display.setLifetime(lifetime + random.nextInt(60));
        } else {
            display.setLifetime(lifetime);
        }

        display.enableIdleRotation(
                interpolationTicks + 1,
                idleConfig.intervalMin,
                idleConfig.intervalMax,
                idleConfig.degreesMin,
                idleConfig.degreesMax
        );

        level.addFreshEntity(display);
        PENDING_TRANSFORMS.add(new PendingTransform(display, translation, finalScale, level.getServer().getTickCount() + TRANSFORM_APPLY_DELAY_TICKS));
    }

    // TODO Summon Implosion

    /**
     * We have to schedule the block displays update of the "start_interpolation" value, because otherwise it doesn't move
     * @param event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_TRANSFORMS.isEmpty() && PENDING_RGBA_TRANSFORMS.isEmpty()) {
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

        Iterator<PendingRgbaTransform> rgbaIterator = PENDING_RGBA_TRANSFORMS.iterator();
        while (rgbaIterator.hasNext()) {
            PendingRgbaTransform pending = rgbaIterator.next();
            if (tick < pending.applyAtTick()) {
                continue;
            }

            if (!pending.display().isRemoved()) {
                pending.display().startAnimation(pending.translation(), pending.scale(), pending.interpolationTicks());
            }

            rgbaIterator.remove();
        }
    }
}
