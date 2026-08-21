package com.github.bandithelps;

import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue TISSUE_EXTRACTOR_COOLDOWN = BUILDER
            .comment("Cooldown time in ticks for the Tissue Extractor (20 ticks = 1 second)")
            .defineInRange("tissueExtractorCooldown", 30, 0, 7200);

    public static final ModConfigSpec.IntValue MOB_DNA_MIN_GENES = BUILDER
            .comment("Minimum number of genes generated for mob DNA")
            .defineInRange("mobDnaMinGenes", 2, 1, 6);

    public static final ModConfigSpec.IntValue MOB_DNA_MAX_GENES = BUILDER
            .comment("Maximum number of genes generated for mob DNA")
            .defineInRange("mobDnaMaxGenes", 6, 1, 6);

    public static final ModConfigSpec.DoubleValue MOB_DNA_HAS_GENES_CHANCE = BUILDER
            .comment("Chance that a mob has genes at all. On success it gets at least 2 genes.")
            .defineInRange("mobDnaHasGenesChance", 0.90D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue MOB_DNA_EXTRA_GENE_3_CHANCE = BUILDER
            .comment("Chance to add a 3rd gene to mob DNA")
            .defineInRange("mobDnaExtraGene3Chance", 0.50D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue MOB_DNA_EXTRA_GENE_4_CHANCE = BUILDER
            .comment("Chance to add a 4th gene to mob DNA")
            .defineInRange("mobDnaExtraGene4Chance", 0.30D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue MOB_DNA_EXTRA_GENE_5_CHANCE = BUILDER
            .comment("Chance to add a 5th gene to mob DNA")
            .defineInRange("mobDnaExtraGene5Chance", 0.25D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue MOB_DNA_EXTRA_GENE_6_CHANCE = BUILDER
            .comment("Chance to add a 6th gene to mob DNA")
            .defineInRange("mobDnaExtraGene6Chance", 0.15D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue GENE_SIDE_EFFECT_CHANCE = BUILDER
            .comment("Flat per-gene chance to apply one random side effect")
            .defineInRange("geneSideEffectChance", 0.30D, 0.0D, 1.0D);

    public static final ModConfigSpec.IntValue GENE_FALLBACK_QUALITY_BOOST = BUILDER
            .comment("Quality bonus per rarity step dropped when selecting a fallback gene rarity")
            .defineInRange("geneFallbackQualityBoost", 10, 0, 100);

    public static final ModConfigSpec.IntValue BIO_PRINTER_PROCESS_TICKS = BUILDER
            .comment("Bio Printer print duration in ticks (20 ticks = 1 second)")
            .defineInRange("bioPrinterProcessTicks", 240, 20, 72000);

    static final ModConfigSpec SPEC = BUILDER.build();
}
