package com.github.bandithelps.commands;

import com.github.bandithelps.capabilities.dna.DNAAttachments;
import com.github.bandithelps.gene.DNA;
import com.github.bandithelps.gene.Gene;
import com.github.bandithelps.gene.GeneCategory;
import com.github.bandithelps.gene.GeneRegistry;
import com.github.bandithelps.gene.GeneType;
import com.github.bandithelps.gene.SideEffect;
import com.github.bandithelps.gene.combination.CombinationGraph;
import com.github.bandithelps.gene.combination.CombinationManager;
import com.github.bandithelps.gene.combination.ResolvedCombinationRecipe;
import com.github.bandithelps.items.GeneVialItem;
import com.github.bandithelps.network.OpenGeneCombinationBrowserPayload;
import com.github.bandithelps.utils.gene.GeneAliasUtil;
import com.github.bandithelps.utils.gene.GeneUtil;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class GeneCommand {

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext context) {
        builder.then(Commands.literal("gene")
                .then(Commands.literal("generate")
                        .executes(c -> generateDna(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> generateDna(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("get")
                        .executes(c -> getDna(c.getSource(), c.getSource().getPlayerOrException())))
                .then(Commands.literal("clear")
                        .executes(c -> clearDna(c.getSource(), c.getSource().getPlayerOrException())))
                .then(Commands.literal("list")
                        .executes(c -> listGenes(c.getSource())))
                .then(Commands.literal("reload")
                        .executes(c -> reloadGenes(c.getSource())))
                .then(Commands.literal("combinations")
                        .then(Commands.literal("list")
                                .executes(c -> listCombinations(c.getSource())))
                        .then(Commands.literal("inspect")
                                .then(Commands.argument("geneId", StringArgumentType.string())
                                        .executes(c -> inspectCombination(c.getSource(), StringArgumentType.getString(c, "geneId")))))
                        .then(Commands.literal("open")
                                .executes(c -> openCombinationBrowser(c.getSource()))))
                .then(Commands.literal("create")
                        .then(Commands.argument("geneId", StringArgumentType.string())
                                .executes(c -> createGeneVial(c.getSource(), c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "geneId"), 50))
                                .then(Commands.argument("quality", IntegerArgumentType.integer(1, 100))
                                        .executes(c -> createGeneVial(c.getSource(), c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "geneId"), IntegerArgumentType.getInteger(c, "quality"))))))
                .then(Commands.literal("info")
                        .executes(c -> showDnaInfo(c.getSource(), c.getSource().getPlayerOrException()))));
    }

    private static int generateDna(CommandSourceStack source, Player player) throws CommandSyntaxException {
        DNA dna = GeneUtil.generateRandomDNA(player.getName().getString());
        String dnaString = GeneUtil.serializeDNA(dna);
        DNAAttachments.get(player).setDNA(dnaString);
        DNAAttachments.get(player).setDNAFatigued(false);
        source.sendSuccess(() -> Component.literal("Generated DNA for " + player.getName().getString() + " with " + dna.getGeneCount() + " genes."), true);
        return dna.getGeneCount();
    }

    private static int getDna(CommandSourceStack source, Player player) throws CommandSyntaxException {
        String dnaString = DNAAttachments.get(player).getDNA();
        if (dnaString == null || dnaString.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No DNA found for " + player.getName().getString() + "."), true);
            return 0;
        }
        DNA dna = GeneUtil.parseDNA(dnaString);
        if (dna == null) {
            source.sendSuccess(() -> Component.literal("Invalid DNA data for " + player.getName().getString() + "."), true);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("DNA for " + player.getName().getString() + ": " + dna.getGeneCount() + " genes from " + dna.getSourceName()), true);
        return dna.getGeneCount();
    }

    private static int clearDna(CommandSourceStack source, Player player) throws CommandSyntaxException {
        DNAAttachments.get(player).setDNA("");
        DNAAttachments.get(player).setDNAFatigued(false);
        source.sendSuccess(() -> Component.literal("Cleared DNA for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static int listGenes(CommandSourceStack source) {
        List<GeneType> geneTypes = GeneRegistry.getInstance().getAllGeneTypes();
        if (geneTypes.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No genes registered."), true);
            return 0;
        }

        Map<GeneCategory, List<GeneType>> byCategory = new EnumMap<>(GeneCategory.class);
        for (GeneType geneType : geneTypes) {
            byCategory.computeIfAbsent(geneType.getCategory(), key -> new ArrayList<>()).add(geneType);
        }

        for (Map.Entry<GeneCategory, List<GeneType>> entry : byCategory.entrySet()) {
            entry.getValue().sort(Comparator.comparing(GeneType::getId));
        }

        source.sendSuccess(() -> Component.literal("Registered genes by category:"), false);
        for (GeneCategory category : GeneCategory.values()) {
            List<GeneType> categoryGenes = byCategory.get(category);
            if (categoryGenes == null || categoryGenes.isEmpty()) {
                continue;
            }

            source.sendSuccess(() -> Component.literal("- " + category.name() + " (" + categoryGenes.size() + ")"), false);
            for (GeneType geneType : categoryGenes) {
                StringBuilder line = new StringBuilder("  * ")
                        .append(geneType.getId())
                        .append(" [")
                        .append(geneType.getRarity().name())
                        .append(", Q")
                        .append(geneType.getQualityMin())
                        .append("-")
                        .append(geneType.getQualityMax())
                        .append("]");
                if (geneType.isCombinable()) {
                    line.append(" combinable");
                }
                source.sendSuccess(() -> Component.literal(line.toString()), false);
            }
        }
        return geneTypes.size();
    }

    private static int reloadGenes(CommandSourceStack source) {
        int loaded = GeneRegistry.getInstance().reload(source.getServer().getResourceManager());
        CombinationGraph graph = CombinationManager.rebuildForServer(source.getServer());
        source.sendSuccess(() -> Component.literal(
                "Reloaded genes. Registered " + loaded + " entries; resolved "
                        + graph.getAllRecipes().size() + " combination recipes (invalid: "
                        + graph.getInvalidCount() + ", overlaps: " + graph.getOverlapGroups().size() + ")."
        ), true);
        return loaded;
    }

    private static int listCombinations(CommandSourceStack source) {
        CombinationGraph graph = CombinationManager.getGraph();
        List<ResolvedCombinationRecipe> recipes = graph.getAllRecipes().stream()
                .sorted(Comparator.comparing(ResolvedCombinationRecipe::getOutputGeneId))
                .toList();
        if (recipes.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No resolved combinations are available."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Resolved combinations: " + recipes.size()
                        + " (invalid: " + graph.getInvalidCount()
                        + ", overlaps: " + graph.getOverlapGroups().size() + ")"
        ), false);
        for (ResolvedCombinationRecipe recipe : recipes) {
            source.sendSuccess(() -> Component.literal(describeRecipe(recipe)), false);
        }
        return recipes.size();
    }

    private static int inspectCombination(CommandSourceStack source, String geneId) {
        ResolvedCombinationRecipe recipe = CombinationManager.getGraph().getRecipe(geneId);
        if (recipe == null) {
            source.sendSuccess(() -> Component.literal("No resolved recipe found for " + geneId), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(describeRecipe(recipe)), false);
        return 1;
    }

    private static int openCombinationBrowser(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CombinationGraph graph = CombinationManager.getGraph();
        PacketDistributor.sendToPlayer(player, new OpenGeneCombinationBrowserPayload(buildBrowserLines(graph)));
        source.sendSuccess(() -> Component.literal("Opened resolved combination browser."), false);
        return 1;
    }

    private static int createGeneVial(CommandSourceStack source, Player player, String geneId, int quality) throws CommandSyntaxException {
        GeneType geneType = GeneRegistry.getInstance().getGeneType(geneId).orElse(null);
        Gene gene;
        if (geneType == null) {
            GeneCategory category = GeneCategory.values()[(int) (Math.random() * GeneCategory.values().length)];
            String name = geneId.toUpperCase();
            GeneType type = new GeneType("yha:gene_" + geneId.toLowerCase(), "Custom gene", 1, 100);
            gene = new Gene(name, category, type, "Custom created gene", quality, java.util.Collections.emptyList());
        } else {
            String name = displayNameFromId(geneType.getId());
            int clampedQuality = Math.max(geneType.getQualityMin(), Math.min(geneType.getQualityMax(), quality));
            gene = new Gene(
                    UUID.randomUUID(),
                    name,
                    geneType.getCategory(),
                    geneType,
                    geneType.getDescription(),
                    clampedQuality,
                    GeneUtil.generateSideEffects(geneType.getCategory())
            );
        }

        ItemStack vialStack = new ItemStack(Items.POTION);
        GeneVialItem.setGenes(vialStack, GeneUtil.serializeGene(gene));

        player.getInventory().add(vialStack);
//        source.sendSuccess(() -> Component.literal("Created gene vial for " + player.getName().getString() + " with gene: " + gene.getName() + " (Quality: " + quality + ")"), true);
        return 1;
    }

    private static String displayNameFromId(String id) {
        String path = id;
        int separator = id.indexOf(':');
        if (separator >= 0 && separator < id.length() - 1) {
            path = id.substring(separator + 1);
        }

        return java.util.Arrays.stream(path.split("_"))
                .filter(part -> !part.isEmpty())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    private static String describeRecipe(ResolvedCombinationRecipe recipe) {
        String requirements = recipe.getRequirements().stream()
                .map(req -> (req.builderResolved() ? "builder:" : "req:")
                        + req.geneId() + " q>=" + req.minQuality())
                .collect(Collectors.joining(", "));
        if (requirements.isEmpty()) {
            requirements = "none";
        }
        String status = recipe.isValid() ? "valid" : "invalid(" + recipe.getInvalidReason() + ")";
        return recipe.getOutputGeneId() + " <- [" + requirements + "] success=" + recipe.getSuccessRate() + "% " + status;
    }

    private static List<String> buildBrowserLines(CombinationGraph graph) {
        List<String> lines = new ArrayList<>();
        lines.add("seed=" + graph.getWorldSeed()
                + " recipes=" + graph.getAllRecipes().size()
                + " invalid=" + graph.getInvalidCount()
                + " overlaps=" + graph.getOverlapGroups().size());
        lines.add("------------------------------------------------");
        List<ResolvedCombinationRecipe> ordered = graph.getAllRecipes().stream()
                .sorted(Comparator.comparing(ResolvedCombinationRecipe::getOutputGeneId))
                .toList();
        for (ResolvedCombinationRecipe recipe : ordered) {
            lines.add(describeRecipe(recipe));
        }
        if (!graph.getOverlapGroups().isEmpty()) {
            lines.add("------------------------------------------------");
            lines.add("Overlaps:");
            graph.getOverlapGroups().forEach((signature, outputs) ->
                    lines.add("sig=[" + signature + "] -> " + String.join(", ", outputs)));
        }
        return lines;
    }

    private static int showDnaInfo(CommandSourceStack source, Player player) throws CommandSyntaxException {
        String dnaString = DNAAttachments.get(player).getDNA();
        if (dnaString == null || dnaString.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No DNA data for " + player.getName().getString() + "."), true);
            return 0;
        }
        DNA dna = GeneUtil.parseDNA(dnaString);
        if (dna == null) {
            source.sendSuccess(() -> Component.literal("Invalid DNA data for " + player.getName().getString() + "."), true);
            return 0;
        }

        StringBuilder info = new StringBuilder();
        info.append("Source: ").append(dna.getSourceName()).append("\n");
        info.append("UUID: ").append(dna.getSourceUuid()).append("\n");
        info.append("Gene Count: ").append(dna.getGeneCount()).append("\n");
        info.append("Harvest Time: ").append(dna.getHarvestTime()).append("\n");
        info.append("DNA Fatigued: ").append(DNAAttachments.get(player).isDNAFatigued()).append("\n");
        info.append("Genes:\n");
        String sourceUuid = dna.getSourceUuid().toString();
        for (Gene gene : dna.getGenes()) {
            Gene resolved = GeneAliasUtil.applyAlias(source.getLevel(), sourceUuid, gene);
            info.append("  - ").append(resolved.getName())
                    .append(" [").append(resolved.getCategory()).append("]")
                    .append(" Q").append(resolved.getQuality());
            if (resolved.hasSideEffects()) {
                info.append(" (Side Effects: ");
                info.append(resolved.getSideEffects().stream()
                        .map(SideEffect::name)
                        .collect(Collectors.joining(", ")));
                info.append(")");
            }
            info.append("\n");
        }

        source.sendSuccess(() -> Component.literal(info.toString()), false);
        return dna.getGeneCount();
    }
}