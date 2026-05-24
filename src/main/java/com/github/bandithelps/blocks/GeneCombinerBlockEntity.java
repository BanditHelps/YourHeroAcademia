package com.github.bandithelps.blocks;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.gene.Gene;
import com.github.bandithelps.gene.combination.CombinationManager;
import com.github.bandithelps.gui.menu.GeneCombinerMenu;
import com.github.bandithelps.items.GeneVialItem;
import com.github.bandithelps.utils.gene.GeneUtil;
import com.github.bandithelps.utils.player.PlayerUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class GeneCombinerBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int INPUT_SLOTS = 4;
    public static final int SLOT_OUTPUT = 4;
    private static final int PROCESS_TICKS = 60;

    private final ItemStackSlot inventory = new ItemStackSlot(5);
    private boolean processing;
    private int processingProgress;
    private int processingTotalTicks;
    private boolean pendingSlop;
    private final List<String> pendingGenes = new ArrayList<>();
    private String outputKind = "empty";
    private int outputGeneCount = 0;
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> GeneCombinerBlockEntity.this.processingProgress;
                case 1 -> GeneCombinerBlockEntity.this.processingTotalTicks;
                case 2 -> GeneCombinerBlockEntity.this.processing ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> GeneCombinerBlockEntity.this.processingProgress = value;
                case 1 -> GeneCombinerBlockEntity.this.processingTotalTicks = value;
                case 2 -> GeneCombinerBlockEntity.this.processing = value > 0;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public GeneCombinerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENE_COMBINER.get(), pos, state);
    }

    public void loadFromTag() {
        CompoundTag tag = getPersistentData();
        this.processing = tag.getBoolean("processing").orElse(false);
        this.processingProgress = tag.getInt("processingProgress").orElse(0);
        this.processingTotalTicks = tag.getInt("processingTotalTicks").orElse(0);
        this.pendingSlop = tag.getBoolean("pendingSlop").orElse(false);
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
        refreshOutputSummary();
    }

    public void saveToTag() {
        CompoundTag tag = getPersistentData();
        tag.putBoolean("processing", this.processing);
        tag.putInt("processingProgress", this.processingProgress);
        tag.putInt("processingTotalTicks", this.processingTotalTicks);
        tag.putBoolean("pendingSlop", this.pendingSlop);
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
        if (!this.inventory.getStack(SLOT_OUTPUT).isEmpty()) {
            return GeneCombinerStartResult.OUTPUT_BLOCKED;
        }
        List<String> pooledGenes = gatherInputGenes();
        if (pooledGenes.isEmpty()) {
            return GeneCombinerStartResult.NO_INPUT;
        }

        Random random = this.level == null ? new Random() : new Random(this.level.getGameTime() ^ getBlockPos().asLong());
        CombinationManager.CombinationAttemptResult result = CombinationManager.evaluateAndRoll(pooledGenes, random);
        if (!result.hasAnyMatch()) {
            return GeneCombinerStartResult.NO_RECIPE;
        }

        clearInputs();
        this.pendingSlop = !result.success();
        this.pendingGenes.clear();
        if (result.success()) {
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
            if (this.processing) {
                return false;
            }
            ItemStack output = this.inventory.getStack(SLOT_OUTPUT);
            if (output.isEmpty()) {
                return false;
            }
            this.inventory.setStack(SLOT_OUTPUT, ItemStack.EMPTY);
            PlayerUtils.giveItem(player, output);
            refreshOutputSummary();
            setChanged();
            syncToClient();
            return true;
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
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, slotStack);
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
        this.inventory.setStack(SLOT_OUTPUT, result);
        this.pendingSlop = false;
        this.pendingGenes.clear();
        refreshOutputSummary();
        setChanged();
        syncToClient();
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

    private void refreshOutputSummary() {
        ItemStack output = this.inventory.getStack(SLOT_OUTPUT);
        if (output.isEmpty()) {
            this.outputKind = "empty";
            this.outputGeneCount = 0;
            return;
        }
        if (output.getItem() == YourHeroAcademia.GENETIC_SLOP.get()) {
            this.outputKind = "slop";
            this.outputGeneCount = 0;
            return;
        }
        if (output.getItem() == YourHeroAcademia.GENE_VIAL.get()) {
            this.outputKind = "vial";
            this.outputGeneCount = GeneVialItem.getGeneCount(output);
            return;
        }
        this.outputKind = "item";
        this.outputGeneCount = 0;
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

    private void syncToClient() {
        // Container menus sync inventory/data automatically.
    }

    public enum GeneCombinerStartResult {
        STARTED,
        BUSY,
        OUTPUT_BLOCKED,
        NO_INPUT,
        NO_RECIPE,
        TOO_FAR
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.yha.gene_combiner");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new GeneCombinerMenu(containerId, inventory, this, this.menuData);
    }

    @Override
    public int getContainerSize() {
        return SLOT_OUTPUT + 1;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!this.inventory.getStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.inventory.getStack(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack existing = this.inventory.getStack(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = existing.split(amount);
        if (existing.isEmpty()) {
            this.inventory.setStack(slot, ItemStack.EMPTY);
        }
        setChanged();
        syncToClient();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack existing = this.inventory.getStack(slot);
        this.inventory.setStack(slot, ItemStack.EMPTY);
        setChanged();
        syncToClient();
        return existing;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= getContainerSize()) {
            return;
        }
        this.inventory.setStack(slot, stack);
        refreshOutputSummary();
        setChanged();
        syncToClient();
    }

    @Override
    public boolean stillValid(Player player) {
        return isUsableBy(player);
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < getContainerSize(); i++) {
            this.inventory.setStack(i, ItemStack.EMPTY);
        }
        refreshOutputSummary();
        setChanged();
        syncToClient();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < INPUT_SLOTS) {
            return stack.getItem() == YourHeroAcademia.GENE_VIAL.get();
        }
        return false;
    }
}
