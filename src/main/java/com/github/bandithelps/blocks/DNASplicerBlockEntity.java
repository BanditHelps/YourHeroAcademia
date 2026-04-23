package com.github.bandithelps.blocks;

import com.github.bandithelps.items.TissueSampleItem;
import com.github.bandithelps.network.DNASplicerSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class DNASplicerBlockEntity extends BlockEntity {
    public static final int SLOT_DNA = 0;
    public static final int SLOT_VIAL = 1;

    private final ItemStackSlot inventory = new ItemStackSlot(2);
    private String[] geneSlots = new String[6];
    private String sourceName = "";
    private String sourceUuid = "";

    public DNASplicerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DNA_SPLICER.get(), pos, state);
    }

    public void loadFromTag() {
        CompoundTag tag = getPersistentData();
        sourceName = tag.getString("sourceName").orElse("");
        sourceUuid = tag.getString("sourceUuid").orElse("");
        if (tag.contains("geneSlots")) {
            ListTag listTag = tag.getList("geneSlots").orElse(new ListTag());
            String[] stored = new String[6];
            for (int i = 0; i < Math.min(listTag.size(), 6); i++) {
                stored[i] = listTag.getString(i).orElse("");
            }
            geneSlots = stored;
        }
        if (tag.contains("inventory")) {
            inventory.deserialize(tag.getCompound("inventory").orElse(new CompoundTag()));
        }
    }

    public void saveToTag() {
        CompoundTag tag = getPersistentData();
        tag.putString("sourceName", sourceName);
        tag.putString("sourceUuid", sourceUuid);
        ListTag list = new ListTag();
        for (String gene : geneSlots) {
            list.add(StringTag.valueOf(gene != null ? gene : ""));
        }
        tag.put("geneSlots", list);
        tag.put("inventory", inventory.serialize());
    }

    public boolean insertDNA(ItemStack sampleStack) {
        if (!inventory.getStack(SLOT_DNA).isEmpty()) {
            return false;
        }
        if (!TissueSampleItem.hasDNA(sampleStack)) {
            return false;
        }

        CompoundTag dnaData = TissueSampleItem.getDNAData(sampleStack);
        sourceName = dnaData.getString("sourceName").orElse("Unknown");
        sourceUuid = dnaData.getString("sourceUuid").orElse("");
        String genesRaw = dnaData.getString("genes").orElse("");
        geneSlots = parseGeneSlots(genesRaw);

        ItemStack toInsert = sampleStack.copy();
        toInsert.setCount(1);
        sampleStack.shrink(1);
        inventory.setStack(SLOT_DNA, toInsert);

        setChanged();
        syncToClient();
        return true;
    }

    public boolean insertVial(ItemStack vialStack) {
        if (!inventory.getStack(SLOT_VIAL).isEmpty()) {
            return false;
        }
        ItemStack toInsert = vialStack.copy();
        toInsert.setCount(1);
        vialStack.shrink(1);
        inventory.setStack(SLOT_VIAL, toInsert);

        setChanged();
        syncToClient();
        return true;
    }

    public ItemStack removeDNA() {
        ItemStack dna = inventory.getStack(SLOT_DNA);
        if (!dna.isEmpty()) {
            inventory.setStack(SLOT_DNA, ItemStack.EMPTY);
            clearDNAData();
            setChanged();
            syncToClient();
        }
        return dna;
    }

    public ItemStack removeVial() {
        ItemStack vial = inventory.getStack(SLOT_VIAL);
        if (!vial.isEmpty()) {
            inventory.setStack(SLOT_VIAL, ItemStack.EMPTY);
            setChanged();
            syncToClient();
        }
        return vial;
    }

    private void clearDNAData() {
        geneSlots = new String[6];
        sourceName = "";
        sourceUuid = "";
    }

    private static String[] parseGeneSlots(String genesRaw) {
        String[] allGenes = genesRaw.isEmpty() ? new String[0] : genesRaw.split(",");
        String[] slots = new String[6];
        for (int i = 0; i < 6; i++) {
            slots[i] = i < allGenes.length ? allGenes[i].trim() : "";
        }
        return slots;
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

    public boolean hasDNA() {
        return !inventory.getStack(SLOT_DNA).isEmpty();
    }

    public boolean hasVial() {
        return !inventory.getStack(SLOT_VIAL).isEmpty();
    }

    public String[] getVialGeneSlots() {
        ItemStack vial = inventory.getStack(SLOT_VIAL);
        if (vial.isEmpty()) {
            return new String[3];
        }
        String vialGenes = com.github.bandithelps.items.GeneVialItem.getStoredGenes(vial);
        return parseGeneSlots(vialGenes);
    }

    public ItemStack createInjector(boolean useLeftSide, Player player) {
        if (!hasDNA() || !hasVial()) {
            return ItemStack.EMPTY;
        }

        ItemStack vial = inventory.getStack(SLOT_VIAL);
        String vialGenes = com.github.bandithelps.items.GeneVialItem.getStoredGenes(vial);

        String[] splicedGenes = new String[6];
        for (int i = 0; i < 6; i++) {
            if (useLeftSide && i < 3) {
                splicedGenes[i] = getVialGeneAt(vialGenes, i);
            } else if (!useLeftSide && i >= 3) {
                splicedGenes[i] = getVialGeneAt(vialGenes, i - 3);
            } else {
                splicedGenes[i] = geneSlots[i];
            }
        }

        String splicedGenesStr = buildGeneString(splicedGenes);

        ItemStack dnaSample = inventory.getStack(SLOT_DNA);
        String uuid = TissueSampleItem.getSourceUuid(dnaSample);

        ItemStack injector = new ItemStack(com.github.bandithelps.YourHeroAcademia.DNA_INJECTOR.get());
        com.github.bandithelps.items.DNAInjectorItem.setDNA(injector, sourceName, uuid, splicedGenesStr);

        inventory.setStack(SLOT_DNA, ItemStack.EMPTY);
        inventory.setStack(SLOT_VIAL, ItemStack.EMPTY);
        clearDNAData();

        setChanged();
        syncToClient();

        return injector;
    }

    private String getVialGeneAt(String vialGenes, int index) {
        if (vialGenes == null || vialGenes.isEmpty()) {
            return "";
        }
        String[] genes = vialGenes.split(",");
        if (index >= genes.length) {
            return "";
        }
        String gene = genes[index].trim();
        return gene.isEmpty() ? "" : gene;
    }

    private String buildGeneString(String[] genes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < genes.length; i++) {
            if (genes[i] != null && !genes[i].isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(genes[i]);
            }
        }
        return sb.toString();
    }

    private void syncToClient() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        String[] vialGenes = getVialGeneSlots();
        var serverLevel = (net.minecraft.server.level.ServerLevel) this.level;
        PacketDistributor.sendToPlayersNear(
                serverLevel,
                null,
                getBlockPos().getX(),
                getBlockPos().getY(),
                getBlockPos().getZ(),
                64.0D,
                new DNASplicerSyncPayload(
                        getBlockPos(),
                        sourceName,
                        sourceUuid,
                        geneSlots,
                        vialGenes
                )
        );
    }
}