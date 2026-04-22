package com.github.bandithelps.blocks;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.network.TreadmillMountStatePayload;
import com.github.bandithelps.utils.stamina.StaminaUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class TreadmillBlockEvents {
    private static final String TREADMILL_POS_KEY = "YhaTreadmillPos";
    private static final String TREADMILL_DIM_KEY = "YhaTreadmillDim";
    private static final int TRAINING_INTERVAL_TICKS = 10;
    private static final int TRAINING_USAGE_PER_INTERVAL = 1;
    private static final float FOOD_EXHAUSTION_PER_INTERVAL = 0.18F;
    private static final double BELT_STAND_HEIGHT = 0.2D;

    private TreadmillBlockEvents() {
    }

    @SubscribeEvent
    public static void onTreadmillUsed(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        BlockPos clickedPos = event.getPos();
        BlockState clickedState = event.getLevel().getBlockState(clickedPos);
        if (clickedState.getBlock() != YourHeroAcademia.TREADMILL_BLOCK.get()) {
            return;
        }

        // Consume on both sides so vanilla right-click placement logic does not run.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos anchorPos = getFootPos(clickedPos, clickedState);

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        long activePos = player.getPersistentData().getLong(TREADMILL_POS_KEY).orElse(Long.MIN_VALUE);
        String activeDim = player.getPersistentData().getString(TREADMILL_DIM_KEY).orElse("");
        String currentDim = level.dimension().toString();

        if (activePos == anchorPos.asLong() && activeDim.equals(currentDim)) {
            stopTraining(player);
        } else {
            startTraining(player, anchorPos);
        }

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
        if (!isTreadmillState(player.level().getBlockState(treadmillPos))) {
            stopTraining(player);
            return;
        }

        if (player.isShiftKeyDown() || !isStandingOnTreadmill(player)) {
            stopTraining(player);
            return;
        }

        // Freeze horizontal movement without teleporting to avoid camera nausea.
        player.setDeltaMovement(0.0D, Math.min(0.0D, player.getDeltaMovement().y), 0.0D);
        player.fallDistance = 0.0F;
        player.setSprinting(true);

        if (player.tickCount % TRAINING_INTERVAL_TICKS == 0) {
            StaminaUtil.addPassiveUsage(player, TRAINING_USAGE_PER_INTERVAL);
            player.causeFoodExhaustion(FOOD_EXHAUSTION_PER_INTERVAL);
        }
    }

    private static void startTraining(ServerPlayer player, BlockPos pos) {
        player.getPersistentData().putLong(TREADMILL_POS_KEY, pos.asLong());
        player.getPersistentData().putString(TREADMILL_DIM_KEY, player.level().dimension().toString());
        BlockState state = player.level().getBlockState(pos);
        Direction facing = state.hasProperty(TreadmillBlock.FACING) ? state.getValue(TreadmillBlock.FACING) : Direction.NORTH;

        // Place the player on the moving belt lane, not the block center.
        double targetX = pos.getX() + 0.5D + (facing.getStepX() * 0.35D);
        double targetY = pos.getY() + BELT_STAND_HEIGHT;
        double targetZ = pos.getZ() + 0.5D + (facing.getStepZ() * 0.35D);
        player.teleportTo(targetX, targetY, targetZ);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        player.setSprinting(true);
        PacketDistributor.sendToPlayer(player, new TreadmillMountStatePayload(true));
    }

    private static void stopTraining(ServerPlayer player) {
        player.getPersistentData().remove(TREADMILL_POS_KEY);
        player.getPersistentData().remove(TREADMILL_DIM_KEY);
        player.setSprinting(false);
        PacketDistributor.sendToPlayer(player, new TreadmillMountStatePayload(false));
    }

    public static boolean isStandingOnTreadmill(Player player) {
        BlockPos feetPos = player.blockPosition();
        if (isTreadmillOrAdjacent(player, feetPos) || isTreadmillOrAdjacent(player, feetPos.below())) {
            return true;
        }
        return false;
    }

    private static boolean isTreadmillOrAdjacent(Player player, BlockPos pos) {
        if (isTreadmillState(player.level().getBlockState(pos))) {
            return true;
        }
        // Two-block treadmills let players stand across a seam.
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (isTreadmillState(player.level().getBlockState(pos.relative(direction)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTreadmillState(BlockState state) {
        return state.getBlock() == YourHeroAcademia.TREADMILL_BLOCK.get();
    }

    private static BlockPos getFootPos(BlockPos pos, BlockState state) {
        if (!state.hasProperty(TreadmillBlock.PART) || !state.hasProperty(TreadmillBlock.FACING)) {
            return pos;
        }
        if (state.getValue(TreadmillBlock.PART) == BedPart.FOOT) {
            return pos;
        }
        return pos.relative(state.getValue(TreadmillBlock.FACING).getOpposite());
    }
}
