package com.github.bandithelps.capabilities.dna;

import net.minecraft.nbt.CompoundTag;

public interface IDNAData {
    String getDNA();
    void setDNA(String dna);

    boolean hasDNA();

    int getIntelligence();
    void setIntelligence(int intelligence);

    boolean isDNAFatigued();
    void setDNAFatigued(boolean fatigued);

    void saveNBTData(CompoundTag nbt);
    void loadNBTData(CompoundTag nbt);
}