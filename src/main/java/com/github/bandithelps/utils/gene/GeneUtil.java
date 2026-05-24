package com.github.bandithelps.utils.gene;

import com.github.bandithelps.Config;
import com.github.bandithelps.gene.DNA;
import com.github.bandithelps.gene.Gene;
import com.github.bandithelps.gene.GeneCategory;
import com.github.bandithelps.gene.GeneRarity;
import com.github.bandithelps.gene.GeneRegistry;
import com.github.bandithelps.gene.GeneType;
import com.github.bandithelps.gene.SideEffect;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public final class GeneUtil {

    private static final Random RANDOM = new SecureRandom();
    private static final int GENE_NAME_LENGTH = 5;
    private static final int GUARANTEED_GENES_ON_SUCCESS = 2;
    private static final int FALLBACK_MAX_GENES = 6;
    private static final String SIDE_EFFECT_DELIMITER = "&";
    private static final List<GeneRarity> RARITY_DESC_ORDER = Arrays.asList(
            GeneRarity.LEGENDARY,
            GeneRarity.EPIC,
            GeneRarity.RARE,
            GeneRarity.UNCOMMON,
            GeneRarity.COMMON
    );

    private GeneUtil() {
    }

    public static Gene parseGene(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        String[] parts = data.split("\\|");
        if (parts.length < 6) {
            return null;
        }
        try {
            UUID id = UUID.fromString(parts[0]);
            String name = parts[1];
            GeneCategory category = GeneCategory.valueOf(parts[2].toUpperCase());
            String typeId = parts[3];
            String description = parts[4];
            int quality = Integer.parseInt(parts[5]);
            List<SideEffect> sideEffects = new ArrayList<>();
            if (parts.length > 6 && !parts[6].isEmpty()) {
                String[] effectNames = parts[6].split(SIDE_EFFECT_DELIMITER);
                for (String effectName : effectNames) {
                    try {
                        sideEffects.add(SideEffect.valueOf(effectName.trim().toUpperCase()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            GeneType type = new GeneType(typeId, description);
            return new Gene(id, name, category, type, description, quality, sideEffects);
        } catch (Exception e) {
            return null;
        }
    }

    public static String serializeGene(Gene gene) {
        if (gene == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(gene.getId()).append("|");
        sb.append(gene.getName()).append("|");
        sb.append(gene.getCategory().name()).append("|");
        sb.append(gene.getType().getId()).append("|");
        sb.append(gene.getDescription()).append("|");
        sb.append(gene.getQuality());
        if (gene.hasSideEffects()) {
            sb.append("|");
            sb.append(gene.getSideEffects().stream()
                    .map(SideEffect::name)
                    .collect(Collectors.joining(SIDE_EFFECT_DELIMITER)));
        }
        return sb.toString();
    }

    public static DNA parseDNA(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        String[] parts = data.split(";");
        if (parts.length < 3) {
            return null;
        }
        try {
            String sourceName = parts[0];
            UUID sourceUuid = UUID.fromString(parts[1]);
            long harvestTime = Long.parseLong(parts[2]);
            List<Gene> genes = new ArrayList<>();
            if (parts.length > 3) {
                String[] geneData = parts[3].split(",");
                for (String geneStr : geneData) {
                    Gene gene = parseGene(geneStr);
                    if (gene != null) {
                        genes.add(gene);
                    }
                }
            }
            return new DNA(sourceName, sourceUuid, genes, harvestTime);
        } catch (Exception e) {
            return null;
        }
    }

    public static String serializeDNA(DNA dna) {
        if (dna == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(dna.getSourceName()).append(";");
        sb.append(dna.getSourceUuid()).append(";");
        sb.append(dna.getHarvestTime()).append(";");
        sb.append(dna.getGenes().stream()
                .map(GeneUtil::serializeGene)
                .collect(Collectors.joining(",")));
        return sb.toString();
    }

    public static DNA generateDNA(UUID entityUuid, String entityName) {
        return generateDNA(entityUuid, entityName, null);
    }

    public static DNA generateDNA(UUID entityUuid, String entityName, String sourceMobId) {
        int hash = entityUuid.toString().hashCode();
        Random seededRandom = new Random(hash);
        List<Gene> genes = new ArrayList<>();
        List<GeneType> availableGeneTypes = GeneRegistry.getInstance().getAllGeneTypes().stream()
                .filter(type -> type.canAppearInMob(sourceMobId))
                .collect(Collectors.toCollection(ArrayList::new));

        if (availableGeneTypes.isEmpty()) {
            availableGeneTypes = GeneRegistry.getInstance().getAllGeneTypes().stream()
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        if (availableGeneTypes.isEmpty()) {
            return new DNA(entityName, entityUuid, genes);
        }

        if (seededRandom.nextDouble() > Config.MOB_DNA_HAS_GENES_CHANCE.get()) {
            return new DNA(entityName, entityUuid, genes);
        }

        int maxGenes = Math.max(GUARANTEED_GENES_ON_SUCCESS, Math.min(FALLBACK_MAX_GENES, Config.MOB_DNA_MAX_GENES.get()));
        int guaranteed = Math.min(GUARANTEED_GENES_ON_SUCCESS, maxGenes);

        for (int i = 0; i < guaranteed && !availableGeneTypes.isEmpty(); i++) {
            Gene selected = selectGeneByRarity(availableGeneTypes, seededRandom);
            if (selected == null) {
                break;
            }
            genes.add(selected);
        }

        if (genes.size() < guaranteed) {
            return new DNA(entityName, entityUuid, genes);
        }

        while (genes.size() < maxGenes && !availableGeneTypes.isEmpty()) {
            if (!passesExtraGeneRoll(genes.size() + 1, seededRandom)) {
                break;
            }
            Gene selected = selectGeneByRarity(availableGeneTypes, seededRandom);
            if (selected == null) {
                break;
            }
            genes.add(selected);
        }

        return new DNA(entityName, entityUuid, genes);
    }

    public static DNA generateRandomDNA(String entityName) {
        return generateDNA(UUID.randomUUID(), entityName);
    }

    private static Gene selectGeneByRarity(List<GeneType> availableGeneTypes, Random random) {
        if (availableGeneTypes.isEmpty()) {
            return null;
        }

        GeneRarity rolledRarity = rollRarity(random);
        int rolledIndex = RARITY_DESC_ORDER.indexOf(rolledRarity);
        if (rolledIndex < 0) {
            rolledIndex = RARITY_DESC_ORDER.size() - 1;
        }

        GeneType selectedType = null;
        int rarityDrops = 0;
        for (int i = rolledIndex; i < RARITY_DESC_ORDER.size(); i++) {
            GeneRarity candidateRarity = RARITY_DESC_ORDER.get(i);
            List<GeneType> matches = availableGeneTypes.stream()
                    .filter(type -> type.getRarity() == candidateRarity)
                    .toList();
            if (!matches.isEmpty()) {
                selectedType = matches.get(random.nextInt(matches.size()));
                rarityDrops = i - rolledIndex;
                break;
            }
        }

        if (selectedType == null) {
            selectedType = availableGeneTypes.get(random.nextInt(availableGeneTypes.size()));
            rarityDrops = 0;
        }

        String selectedTypeId = selectedType.getId();
        availableGeneTypes.removeIf(type -> type.getId().equalsIgnoreCase(selectedTypeId));

        int quality = rollQuality(selectedType, random, rarityDrops);
        List<SideEffect> sideEffects = generateSideEffects(selectedType.getCategory(), random);
        String name = generateGeneName(random);
        return new Gene(name, selectedType.getCategory(), selectedType, selectedType.getDescription(), quality, sideEffects);
    }

    private static GeneRarity rollRarity(Random random) {
        double roll = random.nextDouble();
        if (roll < 0.05D) {
            return GeneRarity.LEGENDARY;
        }
        if (roll < 0.15D) {
            return GeneRarity.EPIC;
        }
        if (roll < 0.35D) {
            return GeneRarity.RARE;
        }
        if (roll < 0.65D) {
            return GeneRarity.UNCOMMON;
        }
        return GeneRarity.COMMON;
    }

    private static int rollQuality(GeneType type, Random random, int rarityDrops) {
        int min = type.getQualityMin();
        int max = type.getQualityMax();
        int range = Math.max(1, max - min + 1);
        int rolled = min + random.nextInt(range);
        int bonus = rarityDrops * Math.max(0, Config.GENE_FALLBACK_QUALITY_BOOST.get());
        return Math.min(max, rolled + bonus);
    }

    private static boolean passesExtraGeneRoll(int nextGeneNumber, Random random) {
        double chance = switch (nextGeneNumber) {
            case 3 -> Config.MOB_DNA_EXTRA_GENE_3_CHANCE.get();
            case 4 -> Config.MOB_DNA_EXTRA_GENE_4_CHANCE.get();
            case 5 -> Config.MOB_DNA_EXTRA_GENE_5_CHANCE.get();
            case 6 -> Config.MOB_DNA_EXTRA_GENE_6_CHANCE.get();
            default -> 0.0D;
        };
        return random.nextDouble() <= chance;
    }

    private static double rarityChance(GeneRarity rarity) {
        return switch (rarity) {
            case COMMON -> 0.90D;
            case UNCOMMON -> 0.65D;
            case RARE -> 0.40D;
            case EPIC -> 0.20D;
            case LEGENDARY -> 0.08D;
        };
    }

    public static String generateGeneName() {
        return generateGeneName(RANDOM);
    }

    private static String generateGeneName(Random random) {
        StringBuilder sb = new StringBuilder(GENE_NAME_LENGTH);
        for (int i = 0; i < GENE_NAME_LENGTH; i++) {
            char c = (char) ('A' + random.nextInt(26));
            sb.append(c);
        }
        return sb.toString();
    }

    public static int[] calculateQualityRange(GeneType type) {
        return new int[]{type.getQualityMin(), type.getQualityMax()};
    }

    public static int calculateQualityRangeSize(GeneType type) {
        return type.getQualityMax() - type.getQualityMin() + 1;
    }

    public static List<SideEffect> generateSideEffects(GeneCategory category, Random random) {
        if (random.nextDouble() > Config.GENE_SIDE_EFFECT_CHANCE.get()) {
            return Collections.emptyList();
        }
        SideEffect[] effects = SideEffect.values();
        List<SideEffect> sideEffects = new ArrayList<>(1);
        sideEffects.add(effects[random.nextInt(effects.length)]);
        return sideEffects;
    }

    public static List<SideEffect> generateSideEffects(GeneCategory category) {
        return generateSideEffects(category, RANDOM);
    }
}