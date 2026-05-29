package com.github.bandithelps.capabilities.dna;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;

public class DNAData implements IDNAData {
    private static final String DNA_KEY = "dna";
    private static final String INTELLIGENCE_KEY = "intelligence";
    private static final String DNA_FATIGUED_KEY = "dnaFatigued";

    private String dna = "";
    private int intelligence;
    private boolean dnaFatigued;

    public static final MapCodec<DNAData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf(DNA_KEY).forGetter(DNAData::getDNA),
            Codec.INT.fieldOf(INTELLIGENCE_KEY).forGetter(DNAData::getIntelligence),
            Codec.BOOL.fieldOf(DNA_FATIGUED_KEY).forGetter(DNAData::isDNAFatigued)
    ).apply(instance, DNAData::fromCodec));

    private static DNAData fromCodec(String dna, int intelligence, boolean dnaFatigued) {
        DNAData data = new DNAData();
        data.setDNA(dna);
        data.setIntelligence(intelligence);
        data.setDNAFatigued(dnaFatigued);
        return data;
    }

    @Override
    public String getDNA() {
        return dna;
    }

    @Override
    public void setDNA(String dna) {
        this.dna = dna != null ? dna : "";
    }

    @Override
    public boolean hasDNA() {
        return dna != null && !dna.isEmpty();
    }

    @Override
    public int getIntelligence() {
        return intelligence;
    }

    @Override
    public void setIntelligence(int intelligence) {
        this.intelligence = Math.max(0, Math.min(100, intelligence));
    }

    @Override
    public boolean isDNAFatigued() {
        return dnaFatigued;
    }

    @Override
    public void setDNAFatigued(boolean fatigued) {
        this.dnaFatigued = fatigued;
    }

    @Override
    public void saveNBTData(CompoundTag nbt) {
        nbt.putString(DNA_KEY, dna);
        nbt.putInt(INTELLIGENCE_KEY, intelligence);
        nbt.putBoolean(DNA_FATIGUED_KEY, dnaFatigued);
    }

    @Override
    public void loadNBTData(CompoundTag nbt) {
        setDNA(nbt.getString(DNA_KEY).orElse(dna));
        setIntelligence(nbt.getInt(INTELLIGENCE_KEY).orElse(intelligence));
        setDNAFatigued(nbt.getBoolean(DNA_FATIGUED_KEY).orElse(dnaFatigued));
    }
}