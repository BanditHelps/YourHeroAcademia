package com.github.bandithelps.capabilities.dna;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Used to track when DNA changes, so things like attributes can be updated without
 * constantly checking every tick.
 */
public final class PlayerDNAChangedEvent extends PlayerEvent {
    private final String previousDNA;
    private final String currentDNA;

    public PlayerDNAChangedEvent(ServerPlayer player, String previousDNA, String currentDNA) {
        super(player);
        this.previousDNA = previousDNA == null ? "" : previousDNA;
        this.currentDNA = currentDNA == null ? "" : currentDNA;
    }

    @Override
    public ServerPlayer getEntity() {
        return (ServerPlayer) super.getEntity();
    }

    public String getPreviousDNA() {
        return this.previousDNA;
    }

    public String getCurrentDNA() {
        return this.currentDNA;
    }
}
