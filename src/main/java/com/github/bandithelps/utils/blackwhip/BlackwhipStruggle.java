package com.github.bandithelps.utils.blackwhip;

import com.github.bandithelps.network.BlackwhipStruggleStatusPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side "break free" minigame. A restrained player accumulates taps; once they reach a threshold
 * (scaled by the strongest restrainer's quirk factor) they snap free of every whipper holding them.
 *
 * <p>Restrain abilities call {@link #mark} every tick while they hold a player; if marking stops, the
 * struggle expires after a short grace window (handled by {@link #cleanup}).</p>
 */
public final class BlackwhipStruggle {

    private static final int GRACE_TICKS = 8;
    private static final int BASE_THRESHOLD = 12;
    private static final int PER_QF_THRESHOLD = 6;

    private static final class State {
        int taps;
        int threshold;
        long lastMark;
        final Set<UUID> whippers = ConcurrentHashMap.newKeySet();
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private BlackwhipStruggle() {
    }

    /** Marks a victim as actively restrained by a whipper this tick (refreshes/begins the minigame). */
    public static void mark(ServerPlayer victim, ServerPlayer whipper, double whipperQuirkFactor) {
        long now = victim.level().getGameTime();
        State state = STATES.computeIfAbsent(victim.getUUID(), k -> new State());
        boolean wasActive = state.lastMark > 0 && (now - state.lastMark) <= GRACE_TICKS;

        state.whippers.add(whipper.getUUID());
        int threshold = BASE_THRESHOLD + (int) Math.floor(Math.max(0, whipperQuirkFactor)) * PER_QF_THRESHOLD;
        state.threshold = Math.max(state.threshold, threshold);
        state.lastMark = now;

        if (!wasActive) {
            state.taps = 0;
            sendStatus(victim, true, state.taps, state.threshold);
        }
    }

    /** Registers one struggle tap from the victim; frees them if the threshold is reached. */
    public static void tap(ServerPlayer victim) {
        State state = STATES.get(victim.getUUID());
        if (state == null) {
            return;
        }
        state.taps++;
        if (state.taps >= state.threshold) {
            free(victim, state);
        } else {
            sendStatus(victim, true, state.taps, state.threshold);
        }
    }

    private static void free(ServerPlayer victim, State state) {
        MinecraftServer server = victim.level().getServer();
        if (server != null) {
            for (UUID whipperId : state.whippers) {
                ServerPlayer whipper = server.getPlayerList().getPlayer(whipperId);
                if (whipper != null) {
                    BlackwhipTagStore.removeTag(whipper, victim.getId());
                }
            }
        }
        STATES.remove(victim.getUUID());
        sendStatus(victim, false, 0, state.threshold);
        victim.level().playSound(null, victim.blockPosition(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    public static boolean isStruggling(ServerPlayer victim) {
        State state = STATES.get(victim.getUUID());
        return state != null;
    }

    /** Expires struggles that are no longer being refreshed by a restrain ability. */
    public static void cleanup(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> entry = it.next();
            State state = entry.getValue();
            if (now - state.lastMark > GRACE_TICKS) {
                it.remove();
                ServerPlayer victim = server.getPlayerList().getPlayer(entry.getKey());
                if (victim != null) {
                    sendStatus(victim, false, 0, state.threshold);
                }
            }
        }
    }

    private static void sendStatus(ServerPlayer victim, boolean active, int taps, int threshold) {
        PacketDistributor.sendToPlayer(victim, new BlackwhipStruggleStatusPayload(active, taps, threshold));
    }
}
