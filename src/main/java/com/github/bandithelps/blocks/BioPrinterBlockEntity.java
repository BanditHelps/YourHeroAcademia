package com.github.bandithelps.blocks;

import com.github.bandithelps.Config;
import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.capabilities.dna.DNAAttachments;
import com.github.bandithelps.gene.Gene;
import com.github.bandithelps.items.DNAInjectorItem;
import com.github.bandithelps.items.GeneVialItem;
import com.github.bandithelps.network.BioPrinterSyncPayload;
import com.github.bandithelps.utils.gene.GeneAliasUtil;
import com.github.bandithelps.utils.gene.GeneUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public class BioPrinterBlockEntity extends BlockEntity {
    private static final int SLOT_COUNT = 6;

    private final String[] genomeSlots = createEmptySlots();
    private final String[] importedGenomeSlots = createEmptySlots();
    private final boolean[] slotModifiedByVial = new boolean[SLOT_COUNT];
    private boolean baseImported;
    private String sourceName = "";
    private String sourceUuid = "";
    private boolean processing;
    private int processingProgress;
    private int processingTotalTicks;
    private boolean awaitingInjectorExtraction;
    private String[] printedGenome = createEmptySlots();

    public BioPrinterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BIO_PRINTER.get(), pos, state);
    }

    public void loadFromTag() {
        CompoundTag tag = getPersistentData();
        this.sourceName = tag.getString("sourceName").orElse("");
        this.sourceUuid = tag.getString("sourceUuid").orElse("");
        this.processing = tag.getBoolean("processing").orElse(false);
        this.processingProgress = tag.getInt("processingProgress").orElse(0);
        this.processingTotalTicks = tag.getInt("processingTotalTicks").orElse(0);
        this.awaitingInjectorExtraction = tag.getBoolean("awaitingInjectorExtraction").orElse(false);
        this.baseImported = tag.getBoolean("baseImported").orElse(false);

        readSlots(tag.getList("genomeSlots").orElse(new ListTag()), this.genomeSlots);
        readSlots(tag.getList("importedGenomeSlots").orElse(new ListTag()), this.importedGenomeSlots);
        readSlots(tag.getList("printedGenome").orElse(new ListTag()), this.printedGenome);
        readFlags(tag.getList("slotModifiedByVial").orElse(new ListTag()), this.slotModifiedByVial);
    }

    public void saveToTag() {
        CompoundTag tag = getPersistentData();
        tag.putString("sourceName", this.sourceName);
        tag.putString("sourceUuid", this.sourceUuid);
        tag.putBoolean("processing", this.processing);
        tag.putInt("processingProgress", this.processingProgress);
        tag.putInt("processingTotalTicks", this.processingTotalTicks);
        tag.putBoolean("awaitingInjectorExtraction", this.awaitingInjectorExtraction);
        tag.putBoolean("baseImported", this.baseImported);
        tag.put("genomeSlots", writeSlots(this.genomeSlots));
        tag.put("importedGenomeSlots", writeSlots(this.importedGenomeSlots));
        tag.put("printedGenome", writeSlots(this.printedGenome));
        tag.put("slotModifiedByVial", writeFlags(this.slotModifiedByVial));
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide() || !this.processing) {
            return;
        }
        if (this.processingTotalTicks <= 0) {
            this.processingTotalTicks = Math.max(20, Config.BIO_PRINTER_PROCESS_TICKS.getAsInt());
        }
        this.processingProgress = Math.min(this.processingProgress + 1, this.processingTotalTicks);
        if (this.processingProgress >= this.processingTotalTicks) {
            finishPrinting();
        }
        setChanged();
        syncToClient();
    }

    public boolean isUsableBy(ServerPlayer player) {
        if (this.level == null || player == null || player.level() != this.level) {
            return false;
        }
        return player.distanceToSqr(
                this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 0.5D,
                this.worldPosition.getZ() + 0.5D
        ) <= 64.0D;
    }

    public boolean importFromPlayerDNA(ServerPlayer player) {
        if (!isUsableBy(player) || this.processing || this.awaitingInjectorExtraction) {
            return false;
        }
        String dna = DNAAttachments.get(player).getDNA();
        String[] parsed = parseDnaSlots(dna);
        boolean hasAny = false;
        for (String slot : parsed) {
            if (slot != null && !slot.isBlank()) {
                hasAny = true;
                break;
            }
        }
        if (!hasAny) {
            return false;
        }
        copySlots(parsed, this.genomeSlots);
        copySlots(parsed, this.importedGenomeSlots);
        clearFlags(this.slotModifiedByVial);
        this.baseImported = true;
        this.sourceName = player.getName().getString();
        this.sourceUuid = player.getUUID().toString();
        this.awaitingInjectorExtraction = false;
        this.processing = false;
        this.processingProgress = 0;
        this.processingTotalTicks = 0;
        setChanged();
        syncToClient();
        return true;
    }

    public boolean transferInventoryVialToGenome(ServerPlayer player, int playerInventorySlot, int targetSlot) {
        if (!isUsableBy(player) || this.processing || this.awaitingInjectorExtraction || !this.baseImported || targetSlot < 0 || targetSlot >= SLOT_COUNT) {
            return false;
        }
        var inventory = player.getInventory();
        if (playerInventorySlot < 0 || playerInventorySlot >= inventory.getContainerSize()) {
            return false;
        }
        var stack = inventory.getItem(playerInventorySlot);
        if (stack.isEmpty() || stack.getItem() != YourHeroAcademia.GENE_VIAL.get()) {
            return false;
        }
        List<String> vialGenes = GeneVialItem.getStoredGeneList(stack);
        int slotLength = Math.max(1, Math.min(3, vialGenes.size()));
        int placementStart = normalizePlacementStart(slotLength, targetSlot);
        if (placementStart < 0 || !isValidPlacementStart(slotLength, placementStart) || placementStart + slotLength > SLOT_COUNT) {
            return false;
        }

        String vialSourceUuid = GeneVialItem.getSourceUuid(stack);
        for (int i = 0; i < slotLength; i++) {
            String raw = i < vialGenes.size() ? vialGenes.get(i) : "";
            if (raw == null || raw.isBlank()) {
                continue;
            }
            Gene parsed = GeneUtil.parseGene(raw);
            if (parsed == null) {
                continue;
            }
            Gene aliased = GeneAliasUtil.applyAlias(this.level, vialSourceUuid, parsed);
            this.genomeSlots[placementStart + i] = GeneUtil.serializeGene(aliased);
            this.slotModifiedByVial[placementStart + i] = true;
        }

        setChanged();
        syncToClient();
        return true;
    }

    public boolean clearGenomeSlot(ServerPlayer player, int slot) {
        if (!isUsableBy(player) || this.processing || this.awaitingInjectorExtraction || slot < 0 || slot >= SLOT_COUNT) {
            return false;
        }
        if (!this.slotModifiedByVial[slot]) {
            return false;
        }
        this.genomeSlots[slot] = this.importedGenomeSlots[slot] == null ? "" : this.importedGenomeSlots[slot];
        this.slotModifiedByVial[slot] = false;
        setChanged();
        syncToClient();
        return true;
    }

    public BioPrinterStartResult tryStartPrinting(ServerPlayer player) {
        if (!isUsableBy(player)) {
            return BioPrinterStartResult.TOO_FAR;
        }
        if (this.processing) {
            return BioPrinterStartResult.BUSY;
        }
        if (this.awaitingInjectorExtraction) {
            return BioPrinterStartResult.RESULT_PENDING_EXTRACTION;
        }
        if (!hasAnyGene(this.genomeSlots)) {
            return BioPrinterStartResult.NO_GENOME;
        }

        copySlots(this.genomeSlots, this.printedGenome);
        this.processing = true;
        this.processingProgress = 0;
        this.processingTotalTicks = Math.max(20, Config.BIO_PRINTER_PROCESS_TICKS.getAsInt());
        setChanged();
        syncToClient();
        return BioPrinterStartResult.STARTED;
    }

    public boolean extractToInjector(ServerPlayer player) {
        if (!isUsableBy(player) || !this.awaitingInjectorExtraction || this.processing || !player.isShiftKeyDown()) {
            return false;
        }
        var held = player.getMainHandItem();
        if (held.isEmpty() || held.getItem() != YourHeroAcademia.DNA_INJECTOR.get()) {
            return false;
        }
        String dna = buildDnaString(this.printedGenome);
        if (dna.isBlank()) {
            return false;
        }
        String resolvedSourceName = this.sourceName == null || this.sourceName.isBlank() ? "Bio Printer" : this.sourceName;
        String resolvedSourceUuid = this.sourceUuid == null ? "" : this.sourceUuid;
        DNAInjectorItem.setDNA(held, resolvedSourceName, resolvedSourceUuid, dna);
        resetMachineState();
        setChanged();
        syncToClient();
        return true;
    }

    public void syncToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new BioPrinterSyncPayload(
                getBlockPos(),
                this.processing,
                this.processingProgress,
                this.processingTotalTicks,
                this.awaitingInjectorExtraction,
                this.sourceName,
                this.sourceUuid,
                Arrays.copyOf(this.genomeSlots, this.genomeSlots.length),
                buildGenomeLabels(this.genomeSlots),
                buildGenomeTooltips(this.genomeSlots),
                Arrays.copyOf(this.slotModifiedByVial, this.slotModifiedByVial.length)
        ));
    }

    private void finishPrinting() {
        this.processing = false;
        this.processingProgress = this.processingTotalTicks;
        this.awaitingInjectorExtraction = true;
    }

    private void resetMachineState() {
        this.awaitingInjectorExtraction = false;
        this.processing = false;
        this.processingProgress = 0;
        this.processingTotalTicks = 0;
        this.baseImported = false;
        this.sourceName = "";
        this.sourceUuid = "";
        copySlots(createEmptySlots(), this.genomeSlots);
        copySlots(createEmptySlots(), this.importedGenomeSlots);
        copySlots(createEmptySlots(), this.printedGenome);
        clearFlags(this.slotModifiedByVial);
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
                new BioPrinterSyncPayload(
                        getBlockPos(),
                        this.processing,
                        this.processingProgress,
                        this.processingTotalTicks,
                        this.awaitingInjectorExtraction,
                        this.sourceName,
                        this.sourceUuid,
                        Arrays.copyOf(this.genomeSlots, this.genomeSlots.length),
                        buildGenomeLabels(this.genomeSlots),
                        buildGenomeTooltips(this.genomeSlots),
                        Arrays.copyOf(this.slotModifiedByVial, this.slotModifiedByVial.length)
                )
        );
    }

    private static boolean isValidPlacementStart(int slotLength, int startSlot) {
        if (slotLength == 3) {
            return startSlot == 0 || startSlot == 3;
        }
        if (slotLength == 2) {
            return startSlot == 0 || startSlot == 1 || startSlot == 3 || startSlot == 4;
        }
        return slotLength == 1;
    }

    private static int normalizePlacementStart(int slotLength, int hoveredSlot) {
        if (hoveredSlot < 0 || hoveredSlot >= SLOT_COUNT) {
            return -1;
        }
        if (slotLength == 3) {
            return hoveredSlot < 3 ? 0 : 3;
        }
        if (slotLength == 2) {
            return switch (hoveredSlot) {
                case 0 -> 0;
                case 1, 2 -> 1;
                case 3 -> 3;
                case 4, 5 -> 4;
                default -> -1;
            };
        }
        return hoveredSlot;
    }

    private static boolean hasAnyGene(String[] slots) {
        for (String slot : slots) {
            if (slot != null && !slot.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static String[] parseDnaSlots(String dna) {
        String[] parsed = createEmptySlots();
        if (dna == null || dna.isBlank()) {
            return parsed;
        }
        String[] pieces = dna.split(",");
        for (int i = 0; i < Math.min(SLOT_COUNT, pieces.length); i++) {
            String trimmed = pieces[i] == null ? "" : pieces[i].trim();
            parsed[i] = trimmed;
        }
        return parsed;
    }

    private static String buildDnaString(String[] slots) {
        List<String> genes = new ArrayList<>();
        for (String slot : slots) {
            if (slot != null && !slot.isBlank()) {
                genes.add(slot);
            }
        }
        return String.join(",", genes);
    }

    private static String[] buildGenomeLabels(String[] slots) {
        String[] labels = new String[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            String raw = slots[i];
            if (raw == null || raw.isBlank()) {
                labels[i] = "";
                continue;
            }
            Gene parsed = GeneUtil.parseGene(raw);
            labels[i] = parsed == null ? "Unknown gene" : parsed.getName();
        }
        return labels;
    }

    private static String[] buildGenomeTooltips(String[] slots) {
        String[] tooltips = new String[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            String raw = slots[i];
            if (raw == null || raw.isBlank()) {
                tooltips[i] = "";
                continue;
            }
            Gene parsed = GeneUtil.parseGene(raw);
            if (parsed == null) {
                tooltips[i] = "Invalid gene data";
                continue;
            }
            List<String> lines = new ArrayList<>();
            lines.add(parsed.getName());
            lines.add("Category: " + parsed.getCategory().name());
            lines.add("Type: " + parsed.getType().getId());
            lines.add("Quality: " + parsed.getQuality());
            if (parsed.hasSideEffects()) {
                parsed.getSideEffects().forEach(effect -> lines.add("Side effect: " + effect.getDisplayName()));
            }
            tooltips[i] = String.join("\n", lines);
        }
        return tooltips;
    }

    private static void copySlots(String[] from, String[] to) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            to[i] = (from != null && i < from.length && from[i] != null) ? from[i] : "";
        }
    }

    private static String[] createEmptySlots() {
        String[] slots = new String[SLOT_COUNT];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = "";
        }
        return slots;
    }

    private static void readSlots(ListTag listTag, String[] out) {
        for (int i = 0; i < out.length; i++) {
            out[i] = i < listTag.size() ? listTag.getString(i).orElse("") : "";
        }
    }

    private static ListTag writeSlots(String[] slots) {
        ListTag listTag = new ListTag();
        for (int i = 0; i < SLOT_COUNT; i++) {
            String value = slots[i] == null ? "" : slots[i];
            listTag.add(StringTag.valueOf(value));
        }
        return listTag;
    }

    private static void readFlags(ListTag listTag, boolean[] out) {
        for (int i = 0; i < out.length; i++) {
            String value = i < listTag.size() ? listTag.getString(i).orElse("0") : "0";
            out[i] = "1".equals(value);
        }
    }

    private static ListTag writeFlags(boolean[] flags) {
        ListTag listTag = new ListTag();
        for (boolean flag : flags) {
            listTag.add(StringTag.valueOf(flag ? "1" : "0"));
        }
        return listTag;
    }

    private static void clearFlags(boolean[] flags) {
        Arrays.fill(flags, false);
    }

    public enum BioPrinterStartResult {
        STARTED,
        BUSY,
        NO_GENOME,
        RESULT_PENDING_EXTRACTION,
        TOO_FAR
    }
}
