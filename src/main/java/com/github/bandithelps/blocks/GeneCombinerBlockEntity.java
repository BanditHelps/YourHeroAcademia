package com.github.bandithelps.blocks;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.gene.Gene;
import com.github.bandithelps.gene.combination.CombinationManager;
import com.github.bandithelps.items.GeneVialItem;
import com.github.bandithelps.network.GeneCombinerSyncPayload;
import com.github.bandithelps.utils.gene.GeneUtil;
import com.github.bandithelps.utils.player.PlayerUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public class GeneCombinerBlockEntity extends BlockEntity {
    public static final int INPUT_SLOTS = 4;
    public static final int SLOT_OUTPUT = 4;
    private static final int PROCESS_TICKS = 60;

    private final ItemStackSlot inventory = new ItemStackSlot(5);
    private boolean processing;
    private int processingProgress;
    private int processingTotalTicks;
    private boolean pendingSlop;
    private final List<String> pendingGenes = new ArrayList<>();
    private String lastResultKind = "empty";
    private int lastResultGeneCount = 0;
    private String lastResultLabel = "";
    private UUID processingPlayer;

    public GeneCombinerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENE_COMBINER.get(), pos, state);
    }

    public void loadFromTag() {
        CompoundTag tag = getPersistentData();
        this.processing = tag.getBoolean("processing").orElse(false);
        this.processingProgress = tag.getInt("processingProgress").orElse(0);
        this.processingTotalTicks = tag.getInt("processingTotalTicks").orElse(0);
        this.pendingSlop = tag.getBoolean("pendingSlop").orElse(false);
        this.lastResultKind = tag.getString("lastResultKind").orElse("empty");
        this.lastResultGeneCount = tag.getInt("lastResultGeneCount").orElse(0);
        this.lastResultLabel = tag.getString("lastResultLabel").orElse("");
        String processingPlayerRaw = tag.getString("processingPlayer").orElse("");
        this.processingPlayer = null;
        if (!processingPlayerRaw.isBlank()) {
            try {
                this.processingPlayer = UUID.fromString(processingPlayerRaw);
            } catch (IllegalArgumentException ignored) {
                this.processingPlayer = null;
            }
        }
        this.pendingGenes.clear();
        if (tag.contains("pendingGenes")) {
            ListTag pendingList = tag.getList("pendingGenes").orElse(new ListTag());
            for (int i = 0; i < pendingList.size(); i++) {
                String raw = pendingList.getString(i).orElse("");
                if (!raw.isBlank()) {
                    this.pendingGenes.add(raw);
                }
            }
        }
        if (tag.contains("inventory")) {
            this.inventory.deserialize(tag.getCompound("inventory").orElse(new CompoundTag()));
        }
    }

    public void saveToTag() {
        CompoundTag tag = getPersistentData();
        tag.putBoolean("processing", this.processing);
        tag.putInt("processingProgress", this.processingProgress);
        tag.putInt("processingTotalTicks", this.processingTotalTicks);
        tag.putBoolean("pendingSlop", this.pendingSlop);
        tag.putString("lastResultKind", this.lastResultKind);
        tag.putInt("lastResultGeneCount", this.lastResultGeneCount);
        tag.putString("lastResultLabel", this.lastResultLabel);
        tag.putString("processingPlayer", this.processingPlayer == null ? "" : this.processingPlayer.toString());
        ListTag pending = new ListTag();
        for (String gene : this.pendingGenes) {
            pending.add(StringTag.valueOf(gene));
        }
        tag.put("pendingGenes", pending);
        tag.put("inventory", this.inventory.serialize());
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide() || !this.processing) {
            return;
        }
        this.processingProgress = Math.min(this.processingProgress + 1, this.processingTotalTicks);
        if (this.processingProgress >= this.processingTotalTicks) {
            finishProcess();
        }
        setChanged();
        syncToClient();
    }

    public GeneCombinerStartResult tryStart(ServerPlayer player) {
        if (this.processing) {
            return GeneCombinerStartResult.BUSY;
        }
        if (!isUsableBy(player)) {
            return GeneCombinerStartResult.TOO_FAR;
        }
        int loadedVials = countLoadedInputVials();
        if (loadedVials < 2) {
            return GeneCombinerStartResult.NO_INPUT;
        }
        List<String> pooledGenes = gatherInputGenes();
        Random random = this.level == null ? new Random() : new Random(this.level.getGameTime() ^ getBlockPos().asLong());
        CombinationManager.CombinationAttemptResult result = pooledGenes.isEmpty()
                ? null
                : CombinationManager.evaluateAndRoll(pooledGenes, random);

        clearInputs();
        this.pendingSlop = result == null || !result.hasAnyMatch() || !result.success();
        this.pendingGenes.clear();
        this.lastResultKind = "empty";
        this.lastResultGeneCount = 0;
        this.lastResultLabel = "";
        this.processingPlayer = player.getUUID();
        if (result != null && result.success()) {
            for (Gene gene : result.producedGenes()) {
                this.pendingGenes.add(GeneUtil.serializeGene(gene));
            }
        }
        this.processing = true;
        this.processingProgress = 0;
        this.processingTotalTicks = PROCESS_TICKS;
        setChanged();
        syncToClient();
        return GeneCombinerStartResult.STARTED;
    }

    public boolean handleSlotInteraction(ServerPlayer player, int slot) {
        if (!isUsableBy(player)) {
            return false;
        }
        if (slot < 0 || slot > SLOT_OUTPUT) {
            return false;
        }

        if (slot == SLOT_OUTPUT) {
            return false;
        }

        ItemStack held = player.getMainHandItem();
        ItemStack slotStack = this.inventory.getStack(slot);

        if (slotStack.isEmpty()) {
            if (this.processing || held.isEmpty() || held.getItem() != YourHeroAcademia.GENE_VIAL.get()) {
                return false;
            }
            ItemStack inserted = held.copy();
            inserted.setCount(1);
            held.shrink(1);
            this.inventory.setStack(slot, inserted);
            setChanged();
            syncToClient();
            return true;
        }

        if (held.isEmpty()) {
            this.inventory.setStack(slot, ItemStack.EMPTY);
            PlayerUtils.giveItem(player, slotStack);
            setChanged();
            syncToClient();
            return true;
        }

        return false;
    }

    private void finishProcess() {
        this.processing = false;
        this.processingProgress = this.processingTotalTicks;
        ItemStack result;
        if (this.pendingSlop) {
            result = new ItemStack(YourHeroAcademia.GENETIC_SLOP.get());
        } else {
            result = buildOutputVial();
        }
        updateLastResult(result);
        boolean delivered = deliverResultToProcessingPlayer(result.copy());
        this.inventory.setStack(SLOT_OUTPUT, ItemStack.EMPTY);
        if (!delivered) {
            this.inventory.setStack(SLOT_OUTPUT, result);
        }
        this.pendingSlop = false;
        this.pendingGenes.clear();
        this.processingPlayer = null;
        setChanged();
        syncToClient();
    }

    public boolean transferInventoryVialToInput(ServerPlayer player, int playerInventorySlot, int inputSlot) {
        if (!isUsableBy(player) || this.processing) {
            return false;
        }
        if (inputSlot < 0 || inputSlot >= INPUT_SLOTS) {
            return false;
        }
        if (!this.inventory.getStack(inputSlot).isEmpty()) {
            return false;
        }
        Inventory inventory = player.getInventory();
        if (playerInventorySlot < 0 || playerInventorySlot >= inventory.getContainerSize()) {
            return false;
        }
        ItemStack source = inventory.getItem(playerInventorySlot);
        if (source.isEmpty() || source.getItem() != YourHeroAcademia.GENE_VIAL.get()) {
            return false;
        }
        ItemStack inserted = source.copy();
        inserted.setCount(1);
        source.shrink(1);
        inventory.setItem(playerInventorySlot, source.isEmpty() ? ItemStack.EMPTY : source);
        this.inventory.setStack(inputSlot, inserted);
        setChanged();
        syncToClient();
        return true;
    }

    public boolean clearInputSlot(ServerPlayer player, int inputSlot) {
        if (!isUsableBy(player) || this.processing) {
            return false;
        }
        if (inputSlot < 0 || inputSlot >= INPUT_SLOTS) {
            return false;
        }
        ItemStack existing = this.inventory.getStack(inputSlot);
        if (existing.isEmpty()) {
            return false;
        }
        this.inventory.setStack(inputSlot, ItemStack.EMPTY);
        PlayerUtils.giveItem(player, existing);
        setChanged();
        syncToClient();
        return true;
    }

    private ItemStack buildOutputVial() {
        ItemStack vial = new ItemStack(YourHeroAcademia.GENE_VIAL.get());
        List<String> slots = new ArrayList<>(Arrays.asList("", "", ""));
        List<Integer> free = new ArrayList<>(Arrays.asList(0, 1, 2));
        Random random = this.level == null ? new Random() : new Random((this.level.getGameTime() << 2) ^ getBlockPos().asLong());
        for (String gene : this.pendingGenes) {
            if (gene.isBlank() || free.isEmpty()) {
                continue;
            }
            int pick = random.nextInt(free.size());
            int slot = free.remove(pick);
            slots.set(slot, gene);
        }
        GeneVialItem.setGenes(vial, slots, "Gene Combiner", "");
        return vial;
    }

    private List<String> gatherInputGenes() {
        List<String> pooled = new ArrayList<>();
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stack = this.inventory.getStack(i);
            if (stack.isEmpty() || stack.getItem() != YourHeroAcademia.GENE_VIAL.get()) {
                continue;
            }
            for (String raw : GeneVialItem.getStoredGeneList(stack)) {
                if (raw != null && !raw.isBlank()) {
                    pooled.add(raw);
                }
            }
        }
        return pooled;
    }

    private int countLoadedInputVials() {
        int count = 0;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stack = this.inventory.getStack(i);
            if (!stack.isEmpty() && stack.getItem() == YourHeroAcademia.GENE_VIAL.get()) {
                count++;
            }
        }
        return count;
    }

    private void clearInputs() {
        for (int i = 0; i < INPUT_SLOTS; i++) {
            this.inventory.setStack(i, ItemStack.EMPTY);
        }
    }

    public boolean isUsableBy(Player player) {
        if (this.level == null || player == null) {
            return false;
        }
        if (player.level() != this.level) {
            return false;
        }
        return player.distanceToSqr(
                this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 0.5D,
                this.worldPosition.getZ() + 0.5D
        ) <= 64.0D;
    }

    private void updateLastResult(ItemStack result) {
        if (result.isEmpty()) {
            this.lastResultKind = "empty";
            this.lastResultGeneCount = 0;
            this.lastResultLabel = "";
            return;
        }
        if (result.getItem() == YourHeroAcademia.GENETIC_SLOP.get()) {
            this.lastResultKind = "slop";
            this.lastResultGeneCount = 0;
            this.lastResultLabel = "Genetic Slop";
            return;
        }
        if (result.getItem() == YourHeroAcademia.GENE_VIAL.get()) {
            this.lastResultKind = "vial";
            this.lastResultGeneCount = GeneVialItem.getGeneCount(result);
            this.lastResultLabel = this.lastResultGeneCount + " gene" + (this.lastResultGeneCount == 1 ? "" : "s");
            return;
        }
        this.lastResultKind = "item";
        this.lastResultGeneCount = 0;
        this.lastResultLabel = result.getHoverName().getString();
    }

    private int[] getInputGeneCounts() {
        int[] counts = new int[INPUT_SLOTS];
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stack = this.inventory.getStack(i);
            if (!stack.isEmpty() && stack.getItem() == YourHeroAcademia.GENE_VIAL.get()) {
                counts[i] = GeneVialItem.getGeneCount(stack);
            } else {
                counts[i] = 0;
            }
        }
        return counts;
    }

    private boolean deliverResultToProcessingPlayer(ItemStack stack) {
        if (stack.isEmpty() || this.processingPlayer == null || this.level == null || this.level.isClientSide()) {
            return false;
        }
        ServerPlayer player = ((ServerLevel) this.level)
                .getServer()
                .getPlayerList()
                .getPlayer(this.processingPlayer);
        if (player == null || !isUsableBy(player)) {
            return false;
        }
        PlayerUtils.giveItem(player, stack);
        return true;
    }

    private String[] getInputSlotLabels() {
        String[] labels = new String[INPUT_SLOTS];
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stack = this.inventory.getStack(i);
            if (stack.isEmpty() || stack.getItem() != YourHeroAcademia.GENE_VIAL.get()) {
                labels[i] = "";
                continue;
            }
            List<String> genes = GeneVialItem.getStoredGeneList(stack);
            int geneCount = GeneVialItem.getGeneCount(stack);
            String firstName = "";
            for (String raw : genes) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                Gene parsed = GeneUtil.parseGene(raw);
                if (parsed != null) {
                    firstName = parsed.getName();
                    break;
                }
            }
            if (!firstName.isBlank()) {
                labels[i] = firstName + " +" + Math.max(0, geneCount - 1);
            } else {
                labels[i] = geneCount + " gene" + (geneCount == 1 ? "" : "s");
            }
        }
        return labels;
    }

    private String[] getInputSlotTooltips() {
        String[] tooltips = new String[INPUT_SLOTS];
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stack = this.inventory.getStack(i);
            if (stack.isEmpty() || stack.getItem() != YourHeroAcademia.GENE_VIAL.get()) {
                tooltips[i] = "";
                continue;
            }
            List<String> lines = new ArrayList<>();
            lines.add("Gene Vial");
            List<String> genes = GeneVialItem.getStoredGeneList(stack);
            int index = 1;
            for (String raw : genes) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                Gene parsed = GeneUtil.parseGene(raw);
                if (parsed == null) {
                    continue;
                }
                lines.add(index + ". " + parsed.getName()
                        + " [" + parsed.getCategory().name() + "]"
                        + " (" + parsed.getType().getId() + ", q:" + parsed.getQuality() + ")");
                if (parsed.hasSideEffects()) {
                    parsed.getSideEffects().forEach(sideEffect ->
                            lines.add("   - Side effect: " + sideEffect.getDisplayName()));
                }
                index++;
            }
            if (index == 1) {
                lines.add("No genes stored");
            }
            tooltips[i] = String.join("\n", lines);
        }
        return tooltips;
    }

    private void syncToClient() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) this.level;
        PacketDistributor.sendToPlayersNear(
                serverLevel,
                null,
                getBlockPos().getX(),
                getBlockPos().getY(),
                getBlockPos().getZ(),
                64.0D,
                new GeneCombinerSyncPayload(
                        getBlockPos(),
                        this.processing,
                        this.processingProgress,
                        this.processingTotalTicks,
                        getInputGeneCounts(),
                        getInputSlotLabels(),
                        getInputSlotTooltips(),
                        this.lastResultKind,
                        this.lastResultGeneCount,
                        this.lastResultLabel
                )
        );
    }

    public void syncToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new GeneCombinerSyncPayload(
                getBlockPos(),
                this.processing,
                this.processingProgress,
                this.processingTotalTicks,
                getInputGeneCounts(),
                getInputSlotLabels(),
                getInputSlotTooltips(),
                this.lastResultKind,
                this.lastResultGeneCount,
                this.lastResultLabel
        ));
    }

    public void clearLastResultDisplay() {
        if (this.processing) {
            return;
        }
        if ("empty".equals(this.lastResultKind) && this.lastResultGeneCount == 0 && (this.lastResultLabel == null || this.lastResultLabel.isBlank())) {
            return;
        }
        this.lastResultKind = "empty";
        this.lastResultGeneCount = 0;
        this.lastResultLabel = "";
        setChanged();
        syncToClient();
    }

    public enum GeneCombinerStartResult {
        STARTED,
        BUSY,
        OUTPUT_BLOCKED,
        NO_INPUT,
        NO_RECIPE,
        TOO_FAR
    }
}
