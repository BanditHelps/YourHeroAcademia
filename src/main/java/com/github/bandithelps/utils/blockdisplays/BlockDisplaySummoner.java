package com.github.bandithelps.utils.blockdisplays;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
    private static final Vector3f CENTER_OFFSET_VECTOR = new Vector3f(-0.25f, 0, -0.2f);
    private static final List<PendingTransform> PENDING_TRANSFORMS = new ArrayList<>();

    private static final BlockState[] palette = {
            Blocks.DIRT.defaultBlockState(),
            Blocks.MANGROVE_ROOTS.defaultBlockState(),
            Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState(),
            Blocks.BLACK_STAINED_GLASS.defaultBlockState()
    };

    private record PendingTransform(BetterBlockDisplay display, Vector3f translation, int applyAtTick) {}

    // Shockwave at the player's feet
    public static void summonShockwave(ServerLevel level, ServerPlayer player, double endRadius, double num) {

        RandomSource random = player.getRandom();

        double centerX = player.getX();
        double centerY = player.getY();
        double centerZ = player.getZ();

        player.sendSystemMessage(Component.literal((float) centerX + " " + (float) centerY + " " + (float) centerZ));


        double endPoint = 2 * Math.PI;
        double step = endPoint / num;

        // h = horizontal distance from the center - center point of x
        // k = vertical distance from the center - center point of z
        // x = h + r*cos(theta)
        // z = k + r * sin(theta)

        double initialRadius = 0.5;

        // Spawn on the inner ring and apply transform one tick later.
        // This gives clients a true "previous" state to interpolate from.
        for (double theta = 0; theta < endPoint; theta += step) {
            BetterBlockDisplay display = new BetterBlockDisplay(EntityType.BLOCK_DISPLAY, level);

            double initialX = centerX + initialRadius * Math.cos(theta);
            double initialZ = centerZ + initialRadius * Math.sin(theta);

            double endX = centerX + endRadius * Math.cos(theta);
            double endZ = centerZ + endRadius * Math.sin(theta);

            display.setPos(initialX, centerY, initialZ);
            display.setBlock(palette[random.nextInt(palette.length)]);
            display.setScale(new Vector3f(0.3f, 0.3f, 0.3f));
            display.setTranslation(new Vector3f(CENTER_OFFSET_VECTOR));
            display.setRightRotation(new Quaternionf(random.nextDouble(),random.nextDouble(),random.nextDouble(),0.5));
            display.setInterpolation(40);
            display.setLifetime(random.nextInt(40));


            Vector3f startPos = new Vector3f((float)initialX, (float)centerY, (float)initialZ);
            Vector3f endPos = new Vector3f((float)endX, (float)centerY, (float)endZ);
            Vector3f translation = endPos.sub(startPos, new Vector3f());


            level.addFreshEntity(display);

            PENDING_TRANSFORMS.add(new PendingTransform(display, translation, level.getServer().getTickCount() + 1));
        }
    }

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
                pending.display().setScale(new Vector3f(0.6f, 0.6f, 0.6f));
                pending.display().startInterpolation();
            }

            iterator.remove();
        }
    }
}
