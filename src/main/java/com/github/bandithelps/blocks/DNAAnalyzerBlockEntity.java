package com.github.bandithelps.blocks;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.attributes.IntelligenceAttributes;
import com.github.bandithelps.gene.Gene;
import com.github.bandithelps.items.GeneVialItem;
import com.github.bandithelps.items.TissueSampleItem;
import com.github.bandithelps.network.DNAAnalyzerSyncPayload;
import com.github.bandithelps.utils.gene.GeneAliasUtil;
import com.github.bandithelps.utils.gene.GeneUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;

public class DNAAnalyzerBlockEntity extends BlockEntity {
    public static final String TAG_ANALYZE_PROGRESS = "analyzeProgress";
    public static final String TAG_ANALYZED = "analyzed";
    public static final String TAG_SOURCE_NAME = "sourceName";
    public static final String TAG_SOURCE_UUID = "sourceUuid";
    public static final String TAG_GENE_SLOTS = "geneSlots";
    public static final String TAG_INVENTORY = "inventory";

    public static final int SLOT_SAMPLE = 0;
    public static final int ANALYZE_TICKS = 30;
    private static final int EXTRACT_COUNT_LOW_INTELLIGENCE = 3;
    private static final int EXTRACT_COUNT_MID_INTELLIGENCE = 2;
    private static final int EXTRACT_COUNT_HIGH_INTELLIGENCE = 1;
    private static final double MID_INTELLIGENCE_THRESHOLD = 25.0D;
    private static final double HIGH_INTELLIGENCE_THRESHOLD = 60.0D;

    private final ItemStackSlot inventory = new ItemStackSlot(1);
    private int analyzeProgress = 0;
    private boolean analyzed = false;
    private String[] geneSlots = createEmptyGeneSlots();
    private String sourceName = "";
    private String sourceUuid = "";

    public DNAAnalyzerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DNA_ANALYZER.get(), pos, state);
    }

    public void loadFromTag() {
        CompoundTag tag = getPersistentData();
        analyzeProgress = tag.getInt(TAG_ANALYZE_PROGRESS).orElse(0);
        analyzed = tag.getBoolean(TAG_ANALYZED).orElse(false);
        sourceName = tag.getString(TAG_SOURCE_NAME).orElse("");
        sourceUuid = tag.getString(TAG_SOURCE_UUID).orElse("");

        if (tag.contains(TAG_GENE_SLOTS)) {
            ListTag listTag = tag.getList(TAG_GENE_SLOTS).orElse(new ListTag());
            String[] stored = createEmptyGeneSlots();
            for (int i = 0; i < Math.min(listTag.size(), 6); i++) {
                stored[i] = listTag.getString(i).orElse("");
            }
            geneSlots = stored;
        }

        if (tag.contains(TAG_INVENTORY)) {
            inventory.deserialize(tag.getCompound(TAG_INVENTORY).orElse(new CompoundTag()));
        }
    }

    public void saveToTag() {
        CompoundTag tag = getPersistentData();
        tag.putInt(TAG_ANALYZE_PROGRESS, analyzeProgress);
        tag.putBoolean(TAG_ANALYZED, analyzed);
        tag.putString(TAG_SOURCE_NAME, sourceName);
        tag.putString(TAG_SOURCE_UUID, sourceUuid);

        ListTag list = new ListTag();
        for (String gene : geneSlots) {
            list.add(StringTag.valueOf(gene != null ? gene : ""));
        }
        tag.put(TAG_GENE_SLOTS, list);
        tag.put(TAG_INVENTORY, inventory.serialize());
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        if (!analyzed) {
            if (!inventory.getStack(SLOT_SAMPLE).isEmpty()) {
                analyzeProgress++;
                if (analyzeProgress >= ANALYZE_TICKS) {
                    finishAnalysis();
                }
                setChanged();
            }
        }
    }

    private void finishAnalysis() {
        ItemStack sample = inventory.getStack(SLOT_SAMPLE);
        if (sample.isEmpty()) {
            return;
        }

        CompoundTag dnaData = TissueSampleItem.getDNAData(sample);
        sourceName = dnaData.getString("sourceName").orElse("Unknown");
        sourceUuid = dnaData.getString("sourceUuid").orElse("");
        String genesRaw = dnaData.getString("genes").orElse("");

        geneSlots = parseGeneSlots(genesRaw);
        analyzeProgress = 0;
        analyzed = true;

        BlockState state = getBlockState();
        if (state.hasProperty(DNAAnalyzerBlock.ANALYZED)) {
            this.level.setBlock(getBlockPos(), state.setValue(DNAAnalyzerBlock.ANALYZED, true), 3);
        }
        this.level.gameEvent(GameEvent.BLOCK_ACTIVATE, getBlockPos(), GameEvent.Context.of(state));

        syncToClient();
    }

    private static String[] parseGeneSlots(String genesRaw) {
        String[] allGenes = genesRaw.isEmpty() ? new String[0] : genesRaw.split(",");
        String[] slots = new String[6];
        for (int i = 0; i < 6; i++) {
            slots[i] = i < allGenes.length ? allGenes[i].trim() : "";
        }
        return slots;
    }

    public boolean insertSample(ItemStack sampleStack, Player player) {
        if (!inventory.getStack(SLOT_SAMPLE).isEmpty()) {
            return false;
        }
        if (!TissueSampleItem.hasDNA(sampleStack)) {
            return false;
        }

        DNAView dnaView = buildGeneView(sampleStack, player);
        if (dnaView == null) {
            return false;
        }

        ItemStack toInsert = sampleStack.copy();
        toInsert.setCount(1);
        sampleStack.shrink(1);
        inventory.setStack(SLOT_SAMPLE, toInsert);
        analyzeProgress = 0;
        analyzed = true;
        sourceName = dnaView.sourceName();
        sourceUuid = dnaView.sourceUuid();
        geneSlots = dnaView.geneSlots();

        BlockState state = getBlockState();
        if (state.hasProperty(DNAAnalyzerBlock.ANALYZED)) {
            this.level.setBlock(getBlockPos(), state.setValue(DNAAnalyzerBlock.ANALYZED, true), 3);
        }
        setChanged();
        syncToClient();
        return true;
    }

    public void renameGene(int slotIndex, String newName, Player player) {
        if (!analyzed || slotIndex < 0 || slotIndex >= geneSlots.length) {
            return;
        }

        String rawGene = geneSlots[slotIndex];
        if (rawGene == null || rawGene.isEmpty()) {
            return;
        }

        Gene gene = GeneUtil.parseGene(rawGene);
        if (gene == null) {
            return;
        }

        String normalizedName = newName == null ? "" : newName.trim();
        if (normalizedName.isEmpty()) {
            return;
        }

        Gene renamedGene = new Gene(
                gene.getId(),
                normalizedName,
                gene.getCategory(),
                gene.getType(),
                gene.getDescription(),
                gene.getQuality(),
                gene.getSideEffects()
        );
        geneSlots[slotIndex] = GeneUtil.serializeGene(renamedGene);
        GeneAliasUtil.setAlias(player, sourceUuid, gene.getType().getId(), normalizedName);
        setChanged();
        syncToClient();
    }

    public void extractGenes(Player player, int[] selectedSlots) {
        if (!analyzed) {
            return;
        }

        int extractCount = getExtractionCountForPlayer(player);
        int[] normalizedSelection = normalizeSelection(selectedSlots);
        if (!isValidSelectionForExtractCount(extractCount, normalizedSelection)) {
            return;
        }

        String[] selectedGenes = new String[normalizedSelection.length];
        for (int i = 0; i < normalizedSelection.length; i++) {
            selectedGenes[i] = geneSlots[normalizedSelection[i]];
        }
        StringBuilder serializedGenes = new StringBuilder();
        for (String rawGene : selectedGenes) {
            if (rawGene == null || rawGene.isEmpty()) {
                continue;
            }
            Gene gene = GeneUtil.parseGene(rawGene);
            if (gene == null) {
                continue;
            }
            Gene aliasedGene = GeneAliasUtil.applyAlias(player, sourceUuid, gene);
            if (serializedGenes.length() > 0) {
                serializedGenes.append(",");
            }
            serializedGenes.append(GeneUtil.serializeGene(aliasedGene));
        }

        if (serializedGenes.isEmpty()) {
            return;
        }

        ItemStack vial = new ItemStack(YourHeroAcademia.GENE_VIAL.get());
        GeneVialItem.setGenes(vial, serializedGenes.toString());

        if (this.level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            ItemEntity vialEntity = new ItemEntity(
                    serverLevel,
                    this.worldPosition.getX() + 0.5D,
                    this.worldPosition.getY() + 1.05D,
                    this.worldPosition.getZ() + 0.5D,
                    vial
            );
            vialEntity.setDeltaMovement(0.0D, 0.1D, 0.0D);
            serverLevel.addFreshEntity(vialEntity);
        }

        ItemStack sample = inventory.getStack(SLOT_SAMPLE);
        if (!sample.isEmpty()) {
            sample.shrink(1);
            if (sample.isEmpty()) {
                inventory.setStack(SLOT_SAMPLE, ItemStack.EMPTY);
            }
        }

        analyzed = false;
        analyzeProgress = 0;
        geneSlots = createEmptyGeneSlots();
        sourceName = "";
        sourceUuid = "";

        BlockState state = getBlockState();
        if (state.hasProperty(DNAAnalyzerBlock.ANALYZED)) {
            this.level.setBlock(getBlockPos(), state.setValue(DNAAnalyzerBlock.ANALYZED, false), 3);
        }
        setChanged();
        syncToClient();
    }

    private static int[] normalizeSelection(int[] selectedSlots) {
        if (selectedSlots == null || selectedSlots.length == 0) {
            return new int[0];
        }
        int[] filtered = Arrays.stream(selectedSlots)
                .filter(index -> index >= 0 && index < 6)
                .distinct()
                .sorted()
                .toArray();
        return filtered;
    }

    private static boolean isValidSelectionForExtractCount(int extractCount, int[] selection) {
        if (extractCount == EXTRACT_COUNT_LOW_INTELLIGENCE) {
            return matchesSelection(selection, 0, 1, 2) || matchesSelection(selection, 3, 4, 5);
        }
        if (extractCount == EXTRACT_COUNT_MID_INTELLIGENCE) {
            return matchesSelection(selection, 0, 1)
                    || matchesSelection(selection, 1, 2)
                    || matchesSelection(selection, 3, 4)
                    || matchesSelection(selection, 4, 5);
        }
        if (extractCount == EXTRACT_COUNT_HIGH_INTELLIGENCE) {
            return selection.length == 1;
        }
        return false;
    }

    private static boolean matchesSelection(int[] selection, int... expected) {
        return Arrays.equals(selection, expected);
    }

    private int getExtractionCountForPlayer(Player player) {
        if (player == null) {
            return EXTRACT_COUNT_LOW_INTELLIGENCE;
        }
        double intelligence = player.getAttributeValue(IntelligenceAttributes.INTELLIGENCE);
        if (intelligence >= HIGH_INTELLIGENCE_THRESHOLD) {
            return EXTRACT_COUNT_HIGH_INTELLIGENCE;
        }
        if (intelligence >= MID_INTELLIGENCE_THRESHOLD) {
            return EXTRACT_COUNT_MID_INTELLIGENCE;
        }
        return EXTRACT_COUNT_LOW_INTELLIGENCE;
    }

    private DNAView buildGeneView(ItemStack sample, Player player) {
        CompoundTag dnaData = TissueSampleItem.getDNAData(sample);
        String parsedSourceName = dnaData.getString("sourceName").orElse("");
        String parsedSourceUuid = dnaData.getString("sourceUuid").orElse("");
        String genesRaw = dnaData.getString("genes").orElse("");
        if (parsedSourceUuid.isEmpty() || genesRaw.isEmpty()) {
            return null;
        }

        String[] rawSlots = parseGeneSlots(genesRaw);
        String[] resolvedSlots = createEmptyGeneSlots();
        for (int i = 0; i < rawSlots.length; i++) {
            String rawGene = rawSlots[i];
            if (rawGene == null || rawGene.isEmpty()) {
                resolvedSlots[i] = "";
                continue;
            }
            Gene gene = GeneUtil.parseGene(rawGene);
            if (gene == null) {
                resolvedSlots[i] = "";
                continue;
            }
            Gene aliasedGene = GeneAliasUtil.applyAlias(player, parsedSourceUuid, gene);
            resolvedSlots[i] = GeneUtil.serializeGene(aliasedGene);
        }
        return new DNAView(parsedSourceName, parsedSourceUuid, resolvedSlots);
    }

    private void syncToClient() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        var serverLevel = (net.minecraft.server.level.ServerLevel) this.level;
        PacketDistributor.sendToPlayersNear(
                serverLevel,
                null,
                getBlockPos().getX(),
                getBlockPos().getY(),
                getBlockPos().getZ(),
                64.0D,
                new DNAAnalyzerSyncPayload(
                        getBlockPos(),
                        analyzed,
                        sourceName,
                        sourceUuid,
                        geneSlots
                )
        );
    }

    private static String[] createEmptyGeneSlots() {
        String[] slots = new String[6];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = "";
        }
        return slots;
    }

    public boolean hasSample() {
        return !inventory.getStack(SLOT_SAMPLE).isEmpty();
    }

    public boolean isAnalyzed() {
        return analyzed;
    }

    public int getAnalyzeProgress() {
        return analyzeProgress;
    }

    public String[] getGeneSlots() {
        return geneSlots;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourceUuid() {
        return sourceUuid;
    }

    public ItemStackSlot getInventory() {
        return inventory;
    }

    public void syncToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new DNAAnalyzerSyncPayload(
                getBlockPos(),
                analyzed,
                sourceName,
                sourceUuid,
                geneSlots
        ));
    }

    private record DNAView(String sourceName, String sourceUuid, String[] geneSlots) {
    }
}