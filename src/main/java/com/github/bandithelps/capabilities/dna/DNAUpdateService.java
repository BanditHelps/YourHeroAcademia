package com.github.bandithelps.capabilities.dna;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

public final class DNAUpdateService {

    private DNAUpdateService() {
    }

    public static void setDNA(ServerPlayer player, String dna, boolean dnaFatigued) {
        IDNAData dnaData = DNAAttachments.get(player);
        String normalizedDNA = dna == null ? "" : dna;
        String previousDNA = dnaData.getDNA();
        boolean previousFatigued = dnaData.isDNAFatigued();

        dnaData.setDNA(normalizedDNA);
        dnaData.setDNAFatigued(dnaFatigued);

        if (!Objects.equals(previousDNA, normalizedDNA)) {
            NeoForge.EVENT_BUS.post(new PlayerDNAChangedEvent(player, previousDNA, normalizedDNA));
        }

        if (!Objects.equals(previousDNA, normalizedDNA) || previousFatigued != dnaFatigued) {
            DNASyncEvents.syncNow(player);
        }
    }
}
