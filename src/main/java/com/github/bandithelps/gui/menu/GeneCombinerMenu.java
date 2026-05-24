package com.github.bandithelps.gui.menu;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.blocks.GeneCombinerBlockEntity;
import com.github.bandithelps.blocks.ModBlocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class GeneCombinerMenu extends AbstractContainerMenu {
    private static final int SLOT_INPUT_START = 0;
    private static final int SLOT_INPUT_END = 3;
    private static final int SLOT_OUTPUT = 4;
    private static final int PLAYER_INV_START = 5;
    private static final int PLAYER_INV_END = 31;
    private static final int PLAYER_HOTBAR_START = 32;
    private static final int PLAYER_HOTBAR_END = 40;

    private final Container container;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final GeneCombinerBlockEntity blockEntity;

    public GeneCombinerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(5), new SimpleContainerData(3), ContainerLevelAccess.NULL, null);
    }

    public GeneCombinerMenu(int containerId, Inventory playerInventory, GeneCombinerBlockEntity blockEntity, ContainerData data) {
        this(containerId, playerInventory, blockEntity, data, ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), blockEntity);
    }

    private GeneCombinerMenu(
            int containerId,
            Inventory playerInventory,
            Container container,
            ContainerData data,
            ContainerLevelAccess access,
            GeneCombinerBlockEntity blockEntity
    ) {
        super(ModMenus.GENE_COMBINER.get(), containerId);
        this.container = container;
        this.data = data;
        this.access = access;
        this.blockEntity = blockEntity;

        checkContainerSize(container, 5);
        checkContainerDataCount(data, 3);
        container.startOpen(playerInventory.player);
        addDataSlots(data);

        this.addSlot(new InputSlot(container, 0, 26, 20));
        this.addSlot(new InputSlot(container, 1, 44, 20));
        this.addSlot(new InputSlot(container, 2, 62, 20));
        this.addSlot(new InputSlot(container, 3, 80, 20));
        this.addSlot(new OutputSlot(container, 4, 134, 20));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + (row * 9) + 9, 8 + (col * 18), 84 + (row * 18)));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + (col * 18), 142));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || this.blockEntity == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        GeneCombinerBlockEntity.GeneCombinerStartResult result = this.blockEntity.tryStart(serverPlayer);
        if (result == GeneCombinerBlockEntity.GeneCombinerStartResult.STARTED) {
            return true;
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(switch (result) {
            case BUSY -> "Gene Combiner is already running.";
            case OUTPUT_BLOCKED -> "Gene Combiner output slot is blocked.";
            case NO_INPUT -> "Gene Combiner requires gene vials in the input slots.";
            case NO_RECIPE -> "No valid combination recipe matches those genes.";
            case TOO_FAR -> "You are too far from this Gene Combiner.";
            default -> "Unable to start Gene Combiner.";
        }));
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copied = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        copied = stack.copy();

        if (index >= SLOT_INPUT_START && index <= SLOT_OUTPUT) {
            if (!this.moveItemStackTo(stack, PLAYER_INV_START, PLAYER_HOTBAR_END + 1, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, copied);
        } else {
            if (stack.getItem() == YourHeroAcademia.GENE_VIAL.get()) {
                if (!this.moveItemStackTo(stack, SLOT_INPUT_START, SLOT_INPUT_END + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= PLAYER_INV_START && index <= PLAYER_INV_END) {
                if (!this.moveItemStackTo(stack, PLAYER_HOTBAR_START, PLAYER_HOTBAR_END + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= PLAYER_HOTBAR_START && index <= PLAYER_HOTBAR_END) {
                if (!this.moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, PLAYER_INV_START, PLAYER_HOTBAR_END + 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stack.getCount() == copied.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stack);
        return copied;
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(this.access, player, ModBlocks.GENE_COMBINER.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    public boolean isProcessing() {
        return this.data.get(2) > 0;
    }

    public int getScaledProgress(int width) {
        int progress = this.data.get(0);
        int total = this.data.get(1);
        if (progress <= 0 || total <= 0 || width <= 0) {
            return 0;
        }
        return Math.min(width, (progress * width) / total);
    }

    private static final class InputSlot extends Slot {
        private InputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() == YourHeroAcademia.GENE_VIAL.get();
        }
    }

    private static final class OutputSlot extends Slot {
        private OutputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
