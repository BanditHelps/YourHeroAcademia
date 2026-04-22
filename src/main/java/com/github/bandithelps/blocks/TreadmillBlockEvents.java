package com.github.bandithelps.blocks;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.utils.stamina.StaminaUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class TreadmillBlockEvents {
    private static final String TREADMILL_POS_KEY = "YhaTreadmillPos";
    private static final String TREADMILL_DIM_KEY = "YhaTreadmillDim";
    private static final int TRAINING_INTERVAL_TICKS = 10;
    private static final int TRAINING_USAGE_PER_INTERVAL = 1;
    private static final float FOOD_EXHAUSTION_PER_INTERVAL = 0.18F;
    private static final double LOCK_DRIFT_DISTANCE_SQR = 0.005D;

    private TreadmillBlockEvents() {
    }

    @SubscribeEvent
    public static void onTreadmillUsed(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() != YourHeroAcademia.TREADMILL_BLOCK.get()) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
            return;
        }

        long activePos = player.getPersistentData().getLong(TREADMILL_POS_KEY).orElse(Long.MIN_VALUE);
        String activeDim = player.getPersistentData().getString(TREADMILL_DIM_KEY).orElse("");
        String currentDim = level.dimension().toString();

        if (activePos == pos.asLong() && activeDim.equals(currentDim)) {
            stopTraining(player);
        } else {
            startTraining(player, pos);
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }

        long treadmillPosValue = player.getPersistentData().getLong(TREADMILL_POS_KEY).orElse(Long.MIN_VALUE);
        if (treadmillPosValue == Long.MIN_VALUE) {
            return;
        }

        String treadmillDim = player.getPersistentData().getString(TREADMILL_DIM_KEY).orElse("");
        String currentDim = player.level().dimension().toString();
        if (!currentDim.equals(treadmillDim)) {
            stopTraining(player);
            return;
        }

        BlockPos treadmillPos = BlockPos.of(treadmillPosValue);
        if (player.level().getBlockState(treadmillPos).getBlock() != YourHeroAcademia.TREADMILL_BLOCK.get()) {
            stopTraining(player);
            return;
        }

        if (player.isShiftKeyDown()) {
            stopTraining(player);
            return;
        }

        // Keep the player standing in place on top of the treadmill.
        double targetX = treadmillPos.getX() + 0.5D;
        double targetY = treadmillPos.getY();
        double targetZ = treadmillPos.getZ() + 0.6D;

        double dx = player.getX() - targetX;
        double dz = player.getZ() - targetZ;
        double horizontalDrift = (dx * dx) + (dz * dz);
        if (horizontalDrift > LOCK_DRIFT_DISTANCE_SQR) {
            player.teleportTo(targetX, targetY, targetZ);
        }

        player.setDeltaMovement(0.0D, Math.min(0.0D, player.getDeltaMovement().y), 0.0D);
        player.fallDistance = 0.0F;
        player.setPos(targetX, targetY, targetZ);
        player.setSprinting(true);

        if (player.tickCount % TRAINING_INTERVAL_TICKS == 0) {
            StaminaUtil.addPassiveUsage(player, TRAINING_USAGE_PER_INTERVAL);
            player.causeFoodExhaustion(FOOD_EXHAUSTION_PER_INTERVAL);
        }
    }

    private static void startTraining(ServerPlayer player, BlockPos pos) {
        player.getPersistentData().putLong(TREADMILL_POS_KEY, pos.asLong());
        player.getPersistentData().putString(TREADMILL_DIM_KEY, player.level().dimension().toString());
        player.setPos(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        player.setSprinting(true);
    }

    private static void stopTraining(ServerPlayer player) {
        player.getPersistentData().remove(TREADMILL_POS_KEY);
        player.getPersistentData().remove(TREADMILL_DIM_KEY);
        player.setSprinting(false);
    }
}
