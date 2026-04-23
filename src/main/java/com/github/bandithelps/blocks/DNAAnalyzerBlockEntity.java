package com.github.bandithelps.blocks;

import com.github.bandithelps.network.DNAAnalyzerSyncPayload;
import com.github.bandithelps.utils.player.PlayerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class DNAAnalyzerBlockEntity extends BlockEntity {
    public static final String TAG_ANALYZE_PROGRESS = "analyzeProgress";
    public static final String TAG_ANALYZED = "analyzed";
    public static final String TAG_SOURCE_NAME = "sourceName";
    public static final String TAG_SOURCE_UUID = "sourceUuid";
    public static final String TAG_GENE_SLOTS = "geneSlots";
    public static final String TAG_INVENTORY = "inventory";

    public static final int SLOT_SAMPLE = 0;
    public static final int ANALYZE_TICKS = 100;

    private final ItemStackSlot inventory = new ItemStackSlot(1);
    private int analyzeProgress = 0;
    private boolean analyzed = false;
    private String[] geneSlots = new String[6];
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
            String[] stored = new String[6];
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

        CompoundTag dnaData = com.github.bandithelps.items.TissueSampleItem.getDNAData(sample);
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

    public boolean insertSample(ItemStack sampleStack) {
        if (!inventory.getStack(SLOT_SAMPLE).isEmpty()) {
            return false;
        }
        ItemStack toInsert = sampleStack.copy();
        toInsert.setCount(1);
        sampleStack.shrink(1);
        inventory.setStack(SLOT_SAMPLE, toInsert);
        analyzeProgress = 0;
        analyzed = false;
        sourceName = "";
        sourceUuid = "";
        geneSlots = new String[6];

        BlockState state = getBlockState();
        if (state.hasProperty(DNAAnalyzerBlock.ANALYZED)) {
            this.level.setBlock(getBlockPos(), state.setValue(DNAAnalyzerBlock.ANALYZED, false), 3);
        }
        setChanged();
        syncToClient();
        return true;
    }

    public void extractGenes(int count, int side, Player player) {
        if (!analyzed) {
            return;
        }

        String[] sideGenes;
        if (side == 0) {
            sideGenes = new String[]{geneSlots[0], geneSlots[1], geneSlots[2]};
        } else {
            sideGenes = new String[]{geneSlots[3], geneSlots[4], geneSlots[5]};
        }

        int actual = Math.min(count, 3);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < actual; i++) {
            if (sideGenes[i] != null && !sideGenes[i].isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(sideGenes[i]);
            }
        }

        if (sb.isEmpty()) {
            return;
        }

        ItemStack vial = new ItemStack(com.github.bandithelps.YourHeroAcademia.GENE_VIAL.get());
        com.github.bandithelps.items.GeneVialItem.setGenes(vial, sb.toString());

        if (player != null) {
            PlayerUtils.giveItem(player, vial);
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
        geneSlots = new String[6];
        sourceName = "";
        sourceUuid = "";

        BlockState state = getBlockState();
        if (state.hasProperty(DNAAnalyzerBlock.ANALYZED)) {
            this.level.setBlock(getBlockPos(), state.setValue(DNAAnalyzerBlock.ANALYZED, false), 3);
        }
        setChanged();
        syncToClient();
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

    public ItemStackSlot getInventory() {
        return inventory;
    }
}