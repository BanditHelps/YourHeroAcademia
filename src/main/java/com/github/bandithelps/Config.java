package com.github.bandithelps;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

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

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(Identifier.parse(itemName));
    }
}
