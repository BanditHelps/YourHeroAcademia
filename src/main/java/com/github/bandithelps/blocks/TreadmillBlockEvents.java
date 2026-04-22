package com.github.bandithelps.blocks;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.network.TreadmillMinigameStatePayload;
import com.github.bandithelps.network.TreadmillMountStatePayload;
import com.github.bandithelps.utils.stamina.StaminaUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class TreadmillBlockEvents {
    private static final String TREADMILL_POS_KEY = "YhaTreadmillPos";
    private static final String TREADMILL_DIM_KEY = "YhaTreadmillDim";
    private static final int TRAINING_INTERVAL_TICKS = 10;
    private static final int TRAINING_USAGE_PER_INTERVAL = 1;
    private static final float FOOD_EXHAUSTION_PER_INTERVAL = 0.18F;
    private static final double BELT_STAND_HEIGHT = 0.2D;
    private static final int MIN_SEQUENCE_LENGTH = 3;
    private static final int MAX_SEQUENCE_LENGTH = 6;
    private static final int MIN_INTERVAL_BETWEEN_QTE_TICKS = 60;
    private static final int MAX_INTERVAL_BETWEEN_QTE_TICKS = 140;
    /** ~2.5s base, plus ~1s per key in the sequence (3–6 keys → ~5.5s–8.5s total at 20 tps). */
    private static final int BASE_QTE_DURATION_TICKS = 150;
    private static final int QTE_TICKS_PER_KEY = 20;
    private static final double FLING_HORIZONTAL_SPEED = 1.0D;
    private static final double FLING_UPWARD_SPEED = 0.45D;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<UUID, MinigameState> MINIGAME_STATES = new HashMap<>();

    private TreadmillBlockEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            MINIGAME_STATES.remove(event.getEntity().getUUID());
        }
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

        MinigameState state = MINIGAME_STATES.computeIfAbsent(player.getUUID(), id -> new MinigameState());
        long gameTime = player.level().getGameTime();
        if (state.active) {
            if (gameTime > state.deadlineTick) {
                failMinigame(player, treadmillPos, true);
            }
            return;
        }

        if (gameTime >= state.nextStartTick) {
            startNewMinigame(player, state, gameTime);
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
        MinigameState minigameState = new MinigameState();
        minigameState.nextStartTick = player.level().getGameTime() + randomIntervalTicks();
        MINIGAME_STATES.put(player.getUUID(), minigameState);
        sendMinigameState(player, minigameState, false, player.level().getGameTime());
    }

    private static void stopTraining(ServerPlayer player) {
        player.getPersistentData().remove(TREADMILL_POS_KEY);
        player.getPersistentData().remove(TREADMILL_DIM_KEY);
        player.setSprinting(false);
        MINIGAME_STATES.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new TreadmillMountStatePayload(false));
        PacketDistributor.sendToPlayer(player, new TreadmillMinigameStatePayload(false, 0, 0, 0, 0L));
    }

    public static void handleMinigameInput(ServerPlayer player, int keyIndex) {
        if (keyIndex < 0 || keyIndex > 3) {
            return;
        }

        long treadmillPosValue = player.getPersistentData().getLong(TREADMILL_POS_KEY).orElse(Long.MIN_VALUE);
        if (treadmillPosValue == Long.MIN_VALUE) {
            return;
        }

        MinigameState state = MINIGAME_STATES.get(player.getUUID());
        if (state == null || !state.active || state.sequenceLength <= 0) {
            return;
        }

        long gameTime = player.level().getGameTime();
        if (gameTime - state.inputStartTick <= 2) {
            return;
        }

        BlockPos treadmillPos = BlockPos.of(treadmillPosValue);
        if (gameTime > state.deadlineTick) {
            failMinigame(player, treadmillPos, true);
            return;
        }

        int expected = getPackedKeyAt(state.packedSequence, state.progressIndex);
        if (expected != keyIndex) {
            failMinigame(player, treadmillPos, true);
            return;
        }

        state.progressIndex++;
        if (state.progressIndex >= state.sequenceLength) {
            state.active = false;
            state.sequenceLength = 0;
            state.progressIndex = 0;
            state.packedSequence = 0;
            state.deadlineTick = 0L;
            state.nextStartTick = gameTime + randomIntervalTicks();
            sendMinigameState(player, state, false, gameTime);
            return;
        }

        sendMinigameState(player, state, true, gameTime);
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

    private static void startNewMinigame(ServerPlayer player, MinigameState state, long gameTime) {
        int length = Mth.nextInt(RANDOM, MIN_SEQUENCE_LENGTH, MAX_SEQUENCE_LENGTH);
        int packed = 0;
        for (int i = 0; i < length; i++) {
            int keyIndex = RANDOM.nextInt(4);
            packed |= (keyIndex & 0b11) << (i * 2);
        }

        state.active = true;
        state.packedSequence = packed;
        state.sequenceLength = length;
        state.progressIndex = 0;
        state.inputStartTick = gameTime;
        state.deadlineTick = gameTime + BASE_QTE_DURATION_TICKS + (long) length * QTE_TICKS_PER_KEY;
        sendMinigameState(player, state, true, gameTime);
    }

    private static void sendMinigameState(ServerPlayer player, MinigameState state, boolean active, long gameTime) {
        long deadline = active ? state.deadlineTick : 0L;
        PacketDistributor.sendToPlayer(player, new TreadmillMinigameStatePayload(
                active,
                state.packedSequence,
                state.sequenceLength,
                state.progressIndex,
                deadline
        ));
    }

    private static int randomIntervalTicks() {
        return Mth.nextInt(RANDOM, MIN_INTERVAL_BETWEEN_QTE_TICKS, MAX_INTERVAL_BETWEEN_QTE_TICKS);
    }

    private static int getPackedKeyAt(int packedSequence, int index) {
        return (packedSequence >> (index * 2)) & 0b11;
    }

    private static void failMinigame(ServerPlayer player, BlockPos treadmillPos, boolean flingPlayer) {
        BlockState treadmillState = player.level().getBlockState(treadmillPos);
        Direction facing = treadmillState.hasProperty(TreadmillBlock.FACING)
                ? treadmillState.getValue(TreadmillBlock.FACING)
                : Direction.NORTH;

        stopTraining(player);
        if (!flingPlayer) {
            return;
        }

        player.setDeltaMovement(
                facing.getStepX() * FLING_HORIZONTAL_SPEED,
                FLING_UPWARD_SPEED,
                facing.getStepZ() * FLING_HORIZONTAL_SPEED
        );
        player.hurtMarked = true;
    }

    private static final class MinigameState {
        private boolean active;
        private int packedSequence;
        private int sequenceLength;
        private int progressIndex;
        private long deadlineTick;
        private long inputStartTick;
        /** Avoid default 0 (would schedule a QTE every tick before startTraining overwrites this). */
        private long nextStartTick = Long.MAX_VALUE;
    }
}
