package com.github.bandithelps.commands;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.capabilities.dna.DNAAttachments;
import com.github.bandithelps.capabilities.dna.DNAUpdateService;
import com.github.bandithelps.gene.DNA;
import com.github.bandithelps.gene.Gene;
import com.github.bandithelps.gene.GeneCategory;
import com.github.bandithelps.gene.GeneRarity;
import com.github.bandithelps.gene.GeneRegistry;
import com.github.bandithelps.gene.GeneType;
import com.github.bandithelps.gene.SideEffect;
import com.github.bandithelps.gene.combination.CombinationGraph;
import com.github.bandithelps.gene.combination.CombinationManager;
import com.github.bandithelps.gene.combination.ResolvedCombinationRecipe;
import com.github.bandithelps.items.GeneVialItem;
import com.github.bandithelps.network.GeneCombinationBrowserDataPayload;
import com.github.bandithelps.utils.gene.GeneAliasUtil;
import com.github.bandithelps.utils.gene.GeneUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.threetag.palladium.network.OpenScreenPacket;

import java.util.*;
import java.util.stream.Collectors;

public class GeneCommand {
    private static final int MAX_DNA_SLOTS = 6;
    private static final String EMPTY_SLOT_TYPE_ID = "yha:empty_slot";
    private static final Identifier GENE_COMBINATION_BROWSER_SCREEN_ID =
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "power/gene_combinations_browser");
    private static final SuggestionProvider<CommandSourceStack> GENE_ID_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (GeneType type : GeneRegistry.getInstance().getAllGeneTypes()) {
            String id = type.getId();
            String quoted = "\"" + id + "\"";
            boolean namespaced = id.contains(":");
            if (quoted.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(quoted);
                continue;
            }
            if (!namespaced && id.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    };

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext context) {
        builder.then(Commands.literal("gene")
                .then(Commands.literal("dna")
                        .then(Commands.literal("generate")
                                .executes(c -> generateDna(c.getSource(), c.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> generateDna(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                        .then(Commands.literal("info")
                                .executes(c -> showDnaInfo(c.getSource(), c.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> showDnaInfo(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                        .then(Commands.literal("clearall")
                                .executes(c -> clearDna(c.getSource(), c.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> clearDna(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                        .then(Commands.literal("clearslot")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, MAX_DNA_SLOTS))
                                                .executes(c -> clearDnaSlot(
                                                        c.getSource(),
                                                        EntityArgument.getPlayer(c, "player"),
                                                        IntegerArgumentType.getInteger(c, "slot")
                                                )))))
                        .then(Commands.literal("setslot")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, MAX_DNA_SLOTS))
                                                .then(Commands.argument("geneId", StringArgumentType.string()).suggests(GENE_ID_SUGGESTIONS)
                                                        .executes(c -> setDnaSlot(
                                                                c.getSource(),
                                                                EntityArgument.getPlayer(c, "player"),
                                                                IntegerArgumentType.getInteger(c, "slot"),
                                                                StringArgumentType.getString(c, "geneId"),
                                                                50
                                                        ))
                                                        .then(Commands.argument("quality", IntegerArgumentType.integer(1, 100))
                                                                .executes(c -> setDnaSlot(
                                                                        c.getSource(),
                                                                        EntityArgument.getPlayer(c, "player"),
                                                                        IntegerArgumentType.getInteger(c, "slot"),
                                                                        StringArgumentType.getString(c, "geneId"),
                                                                        IntegerArgumentType.getInteger(c, "quality")
                                                                ))))))))
                .then(Commands.literal("list")
                        .executes(c -> listGenes(c.getSource())))
                .then(Commands.literal("reload")
                        .executes(c -> reloadGenes(c.getSource())))
                .then(Commands.literal("combinations")
                        .then(Commands.literal("list")
                                .executes(c -> listCombinations(c.getSource())))
                        .then(Commands.literal("inspect")
                                .then(Commands.argument("geneId", StringArgumentType.string()).suggests(GENE_ID_SUGGESTIONS)
                                        .executes(c -> inspectCombination(c.getSource(), StringArgumentType.getString(c, "geneId")))))
                        .then(Commands.literal("open")
                                .executes(c -> openCombinationBrowser(c.getSource()))))
                .then(Commands.literal("create")
                        .then(Commands.argument("geneId", StringArgumentType.string()).suggests(GENE_ID_SUGGESTIONS)
                                .executes(c -> createGeneVial(c.getSource(), c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "geneId"), 50))
                                .then(Commands.argument("quality", IntegerArgumentType.integer(1, 100))
                                        .executes(c -> createGeneVial(c.getSource(), c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "geneId"), IntegerArgumentType.getInteger(c, "quality")))))));
    }

    private static int generateDna(CommandSourceStack source, Player player) throws CommandSyntaxException {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return 0;
        }
        DNA dna = GeneUtil.generateRandomDNA(player.getName().getString());
        String dnaString = GeneUtil.serializeDNA(dna);
        DNAUpdateService.setDNA(serverPlayer, dnaString, false);
        source.sendSuccess(() -> Component.literal("Generated DNA for " + player.getName().getString() + " with " + dna.getGeneCount() + " genes."), true);
        return dna.getGeneCount();
    }

    private static int clearDna(CommandSourceStack source, Player player) throws CommandSyntaxException {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return 0;
        }
        DNAUpdateService.setDNA(serverPlayer, "", false);
        source.sendSuccess(() -> Component.literal("Cleared DNA for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static int setDnaSlot(CommandSourceStack source, ServerPlayer player, int slot, String geneId, int quality) {
        DNA dna = getOrCreateDNA(player);
        List<Gene> genes = normalizeSlotGenes(dna);
        int index = slot - 1;

        GeneType geneType = GeneRegistry.getInstance().getGeneType(geneId).orElse(null);
        if (geneType == null) {
            source.sendFailure(Component.literal("Unknown gene id: " + geneId));
            return 0;
        }

        int clampedQuality = Math.max(geneType.getQualityMin(), Math.min(geneType.getQualityMax(), quality));
        Gene replacement = new Gene(
                UUID.randomUUID(),
                displayNameFromId(geneType.getId()),
                geneType.getCategory(),
                geneType,
                geneType.getDescription(),
                clampedQuality,
                List.of()
        );

        genes.set(index, replacement);

        saveDNA(player, dna.getSourceName(), dna.getSourceUuid(), dna.getHarvestTime(), genes);
        source.sendSuccess(() -> Component.literal("Set slot " + slot + " for " + player.getName().getString()
                + " to " + geneType.getId() + " (q" + clampedQuality + ")."), true);
        return 1;
    }

    private static int clearDnaSlot(CommandSourceStack source, ServerPlayer player, int slot) {
        DNA dna = getOrCreateDNA(player);
        List<Gene> genes = normalizeSlotGenes(dna);
        int index = slot - 1;
        Gene removed = genes.get(index);
        genes.set(index, createEmptySlotGene(index + 1));
        saveDNA(player, dna.getSourceName(), dna.getSourceUuid(), dna.getHarvestTime(), genes);
        String removedType = isEmptySlotGene(removed) ? "empty" : removed.getType().getId();
        source.sendSuccess(() -> Component.literal("Cleared slot " + slot + " (removed " + removedType
                + ") for " + player.getName().getString() + "."), true);
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
        PacketDistributor.sendToPlayer(player, new GeneCombinationBrowserDataPayload(buildBrowserPayloadEntries(graph)));
        PacketDistributor.sendToPlayer(player, new OpenScreenPacket(GENE_COMBINATION_BROWSER_SCREEN_ID));
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
            gene = new Gene(name, category, type, "Custom created gene", quality, Collections.emptyList());
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

        ItemStack vialStack = new ItemStack(YourHeroAcademia.GENE_VIAL.get());
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

        return Arrays.stream(path.split("_"))
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

    private static List<String> buildBrowserPayloadEntries(CombinationGraph graph) {
        List<ResolvedCombinationRecipe> ordered = graph.getAllRecipes().stream()
                .sorted(Comparator.comparing(ResolvedCombinationRecipe::getOutputGeneId))
                .toList();
        List<String> encoded = new ArrayList<>(ordered.size());
        for (ResolvedCombinationRecipe recipe : ordered) {
            JsonObject object = new JsonObject();
            GeneType outputType = GeneRegistry.getInstance().getGeneType(recipe.getOutputGeneId()).orElse(null);
            object.addProperty("outputId", recipe.getOutputGeneId());
            object.addProperty("displayName", outputType == null ? displayNameFromId(recipe.getOutputGeneId()) : displayNameFromId(outputType.getId()));
            object.addProperty("category", outputType == null ? "" : outputType.getCategory().name());
            addGeneMetadata(object, outputType);
            object.addProperty("successRate", recipe.getSuccessRate());
            object.addProperty("valid", recipe.isValid());
            object.addProperty("invalidReason", recipe.getInvalidReason());

            JsonArray requirements = new JsonArray();
            for (ResolvedCombinationRecipe.ResolvedRequirement requirement : recipe.getRequirements()) {
                JsonObject requirementObject = new JsonObject();
                GeneType requirementType = GeneRegistry.getInstance().getGeneType(requirement.geneId()).orElse(null);
                requirementObject.addProperty("geneId", requirement.geneId());
                requirementObject.addProperty("displayName",
                        requirementType == null ? displayNameFromId(requirement.geneId()) : displayNameFromId(requirementType.getId()));
                requirementObject.addProperty("category", requirementType == null ? "" : requirementType.getCategory().name());
                addGeneMetadata(requirementObject, requirementType);
                requirementObject.addProperty("minQuality", requirement.minQuality());
                requirementObject.addProperty("builderResolved", requirement.builderResolved());
                requirements.add(requirementObject);
            }
            object.add("requirements", requirements);
            encoded.add(object.toString());
        }
        return encoded;
    }

    private static void addGeneMetadata(JsonObject target, GeneType geneType) {
        if (geneType == null) {
            target.addProperty("rarity", "");
            target.addProperty("qualityMin", 1);
            target.addProperty("qualityMax", 100);
            target.addProperty("description", "");
            target.add("mobs", new JsonArray());
            return;
        }
        target.addProperty("rarity", geneType.getRarity().name());
        target.addProperty("qualityMin", geneType.getQualityMin());
        target.addProperty("qualityMax", geneType.getQualityMax());
        target.addProperty("description", geneType.getDescription());
        JsonArray mobs = new JsonArray();
        for (String mobId : geneType.getMobs()) {
            mobs.add(mobId);
        }
        target.add("mobs", mobs);
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

        String sourceUuid = dna.getSourceUuid().toString();
        source.sendSuccess(() -> Component.literal("=== DNA Profile: " + player.getName().getString() + " ===")
                .withColor(0x55FFFF), false);
        source.sendSuccess(() -> Component.literal("Source: ").withColor(0xAAAAAA)
                .append(Component.literal(dna.getSourceName()).withColor(0xFFFFFF)), false);
        source.sendSuccess(() -> Component.literal("Source UUID: ").withColor(0xAAAAAA)
                .append(Component.literal(sourceUuid).withColor(0xBBBBBB)), false);
        source.sendSuccess(() -> Component.literal("Harvest Time: ").withColor(0xAAAAAA)
                .append(Component.literal(String.valueOf(dna.getHarvestTime())).withColor(0xBBBBBB)), false);
        source.sendSuccess(() -> Component.literal("DNA Fatigued: ").withColor(0xAAAAAA)
                .append(Component.literal(String.valueOf(DNAAttachments.get(player).isDNAFatigued()))
                        .withColor(DNAAttachments.get(player).isDNAFatigued() ? 0xFF5555 : 0x55FF55)), false);
        List<Gene> slotGenes = buildDisplaySlotGenes(dna);
        source.sendSuccess(() -> Component.literal("Genes (" + countOccupiedGenes(slotGenes) + "/" + MAX_DNA_SLOTS + "):").withColor(0xFFD700), false);

        int slot = 1;
        for (Gene gene : slotGenes) {
            if (isEmptySlotGene(gene)) {
                int slotIndex = slot;
                MutableComponent emptyLine = Component.literal("[" + slot + "] ").withColor(0x888888)
                        .append(Component.literal("Empty").withColor(0x666666))
                        .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(
                                Component.literal("Slot ").withColor(0xAAAAAA)
                                        .append(Component.literal(String.valueOf(slotIndex)).withColor(0xFFFFFF))
                                        .append(Component.literal("\nStatus: ").withColor(0xAAAAAA))
                                        .append(Component.literal("Empty").withColor(0x777777))
                        )));
                source.sendSuccess(() -> emptyLine.copy(), false);
                slot++;
                continue;
            }
            Gene resolved = GeneAliasUtil.applyAlias(source.getLevel(), sourceUuid, gene);
            int qualityColor = getQualityColor(resolved.getQuality());
            String sideEffects = resolved.hasSideEffects()
                    ? resolved.getSideEffects().stream().map(SideEffect::name).collect(Collectors.joining(", "))
                    : "None";
            MutableComponent line = Component.literal("[" + slot + "] ").withColor(0x888888)
                    .append(Component.literal(resolved.getName()).withColor(getCategoryColor(resolved.getCategory())))
                    .append(Component.literal(" (" + resolved.getType().getId() + ") ").withColor(0xAAAAAA))
                    .append(Component.literal("Q" + resolved.getQuality()).withColor(qualityColor))
                    .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(
                            Component.literal("Gene ID: ").withColor(0xAAAAAA)
                                    .append(Component.literal(resolved.getId().toString()).withColor(0xFFFFFF))
                                    .append(Component.literal("\nCategory: ").withColor(0xAAAAAA))
                                    .append(Component.literal(resolved.getCategory().name()).withColor(getCategoryColor(resolved.getCategory())))
                                    .append(Component.literal("\nType: ").withColor(0xAAAAAA))
                                    .append(Component.literal(resolved.getType().getId()).withColor(0xFFFFFF))
                                    .append(Component.literal("\nQuality: ").withColor(0xAAAAAA))
                                    .append(Component.literal(String.valueOf(resolved.getQuality())).withColor(qualityColor))
                                    .append(Component.literal("\nDescription: ").withColor(0xAAAAAA))
                                    .append(Component.literal(resolved.getDescription().isBlank() ? "None" : resolved.getDescription()).withColor(0xDDDDDD))
                                    .append(Component.literal("\nSide Effects: ").withColor(0xAAAAAA))
                                    .append(Component.literal(sideEffects).withColor(resolved.hasSideEffects() ? 0xFF5555 : 0x55FF55))
                    )));
            source.sendSuccess(() -> line.copy(), false);
            slot++;
        }
        return countOccupiedGenes(slotGenes);
    }

    private static DNA getOrCreateDNA(ServerPlayer player) {
        String dnaRaw = DNAAttachments.get(player).getDNA();
        DNA parsed = GeneUtil.parseDNA(dnaRaw);
        if (parsed != null) {
            return parsed;
        }
        return new DNA(player.getName().getString(), player.getUUID(), List.of(), System.currentTimeMillis());
    }

    private static void saveDNA(ServerPlayer player, String sourceName, UUID sourceUuid, long harvestTime, List<Gene> genes) {
        DNA next = new DNA(sourceName, sourceUuid, genes, harvestTime);
        DNAUpdateService.setDNA(player, GeneUtil.serializeDNA(next), DNAAttachments.get(player).isDNAFatigued());
    }

    private static List<Gene> normalizeSlotGenes(DNA dna) {
        List<Gene> slots = new ArrayList<>(MAX_DNA_SLOTS);
        List<Gene> existing = dna.getGenes();
        for (int i = 0; i < MAX_DNA_SLOTS; i++) {
            if (i < existing.size()) {
                slots.add(existing.get(i));
            } else {
                slots.add(createEmptySlotGene(i + 1));
            }
        }
        return slots;
    }

    private static List<Gene> buildDisplaySlotGenes(DNA dna) {
        List<Gene> normalized = normalizeSlotGenes(dna);
        List<Gene> existing = dna.getGenes();
        if (existing.isEmpty() || hasExplicitSlotLayout(existing)) {
            return normalized;
        }

        List<Gene> resolved = new ArrayList<>(MAX_DNA_SLOTS);
        for (int i = 0; i < MAX_DNA_SLOTS; i++) {
            resolved.add(createEmptySlotGene(i + 1));
        }

        int[] slotOrder = GeneUtil.getDeterministicSlotOrderForSource(dna.getSourceUuid());
        int limit = Math.min(MAX_DNA_SLOTS, existing.size());
        for (int i = 0; i < limit; i++) {
            resolved.set(slotOrder[i], existing.get(i));
        }
        return resolved;
    }

    private static boolean hasExplicitSlotLayout(List<Gene> genes) {
        if (genes.size() < MAX_DNA_SLOTS) {
            return false;
        }
        for (Gene gene : genes) {
            if (isEmptySlotGene(gene)) {
                return true;
            }
        }
        return false;
    }

    private static Gene createEmptySlotGene(int slot) {
        GeneType emptyType = new GeneType(
                EMPTY_SLOT_TYPE_ID,
                GeneCategory.BUILDER,
                GeneRarity.COMMON,
                "Command-managed empty DNA slot",
                1,
                1,
                false,
                null,
                List.of(),
                List.of()
        );
        return new Gene(
                UUID.nameUUIDFromBytes(("yha_empty_slot_" + slot).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "Empty Slot " + slot,
                GeneCategory.BUILDER,
                emptyType,
                emptyType.getDescription(),
                1,
                List.of()
        );
    }

    private static boolean isEmptySlotGene(Gene gene) {
        return gene != null && gene.getType() != null && EMPTY_SLOT_TYPE_ID.equalsIgnoreCase(gene.getType().getId());
    }

    private static int countOccupiedGenes(List<Gene> genes) {
        int count = 0;
        for (Gene gene : genes) {
            if (!isEmptySlotGene(gene)) {
                count++;
            }
        }
        return count;
    }

    private static int getQualityColor(int quality) {
        if (quality >= 85) return 0xAA00FF;
        if (quality >= 65) return 0x55FFFF;
        if (quality >= 40) return 0x55FF55;
        if (quality >= 20) return 0xFFFF55;
        return 0xFF5555;
    }

    private static int getCategoryColor(GeneCategory category) {
        return switch (category) {
            case ATTRIBUTE -> 0x55FF55;
            case RESISTANCE -> 0x55FFFF;
            case COSMETIC -> 0xFF55FF;
            case ABILITY -> 0xFFD700;
            case QUIRK -> 0xFF5555;
            case BUILDER -> 0xAAAAAA;
        };
    }
}