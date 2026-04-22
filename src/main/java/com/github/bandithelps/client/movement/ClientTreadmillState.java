package com.github.bandithelps.client.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public final class ClientTreadmillState {
    private static volatile boolean mounted;
    private static volatile boolean minigameActive;
    private static volatile int packedSequence;
    private static volatile int sequenceLength;
    private static volatile int progressIndex;
    /** Server level game time of minigame end; 0 if inactive. Remaining = deadline - level.getGameTime() (no local tick drift). */
    private static volatile long deadlineGameTime;

    private ClientTreadmillState() {
    }

    public static boolean isMounted() {
        return mounted;
    }

    public static void setMounted(boolean treadmillMounted) {
        mounted = treadmillMounted;
        if (!treadmillMounted) {
            clearMinigame();
        }
    }

    public static boolean isMinigameActive() {
        return minigameActive;
    }

    public static int getSequenceLength() {
        return sequenceLength;
    }

    public static int getProgressIndex() {
        return progressIndex;
    }

    public static int getRemainingTicks() {
        if (!minigameActive) {
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0;
        }
        return (int) Mth.clamp(deadlineGameTime - mc.level.getGameTime(), 0L, Integer.MAX_VALUE);
    }

    public static int getSequenceKeyAt(int index) {
        if (index < 0 || index >= sequenceLength) {
            return -1;
        }
        return (packedSequence >> (index * 2)) & 0b11;
    }

    public static void setMinigameState(boolean active, int sequenceData, int length, int progress, long serverDeadlineGameTime) {
        minigameActive = active;
        packedSequence = sequenceData;
        sequenceLength = Math.max(0, Math.min(8, length));
        progressIndex = Math.max(0, Math.min(sequenceLength, progress));
        deadlineGameTime = serverDeadlineGameTime;
    }

    public static void clearMinigame() {
        minigameActive = false;
        packedSequence = 0;
        sequenceLength = 0;
        progressIndex = 0;
        deadlineGameTime = 0L;
    }
}
