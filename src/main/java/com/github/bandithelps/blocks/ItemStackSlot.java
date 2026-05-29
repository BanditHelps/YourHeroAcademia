package com.github.bandithelps.blocks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;

/**
 * Used to help mimic an inventory for Palladium screens. Used mainly by the genetic machines.
 */
public class ItemStackSlot {
    private final ItemStack[] stacks;

    public ItemStackSlot(int slots) {
        this.stacks = new ItemStack[slots];
        for (int i = 0; i < slots; i++) {
            this.stacks[i] = ItemStack.EMPTY;
        }
    }

    public ItemStack getStack(int slot) {
        if (slot < 0 || slot >= this.stacks.length) {
            return ItemStack.EMPTY;
        }
        return this.stacks[slot];
    }

    public void setStack(int slot, ItemStack stack) {
        if (slot >= 0 && slot < this.stacks.length) {
            this.stacks[slot] = stack == null ? ItemStack.EMPTY : stack;
        }
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (ItemStack stack : this.stacks) {
            CompoundTag slotTag = new CompoundTag();
            if (!stack.isEmpty()) {
                Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                slotTag.putString("id", key.toString());
                slotTag.putInt("count", stack.getCount());
            }
            list.add(slotTag);
        }
        tag.put("slots", list);
        return tag;
    }

    public void deserialize(CompoundTag tag) {
        if (!tag.contains("slots")) {
            return;
        }
        ListTag listTag = tag.getList("slots").orElse(new ListTag());
        for (int i = 0; i < Math.min(listTag.size(), this.stacks.length); i++) {
            CompoundTag slotTag = listTag.getCompound(i).orElse(new CompoundTag());
            if (slotTag.contains("id")) {
                String id = slotTag.getString("id").orElse("minecraft:air");
                var optItem = BuiltInRegistries.ITEM.get(Identifier.parse(id));
                if (optItem.isPresent()) {
                    int count = slotTag.getInt("count").orElse(1);
                    if (count <= 0) count = 1;
                    this.stacks[i] = new ItemStack(optItem.get(), count);
                }
            }
        }
    }
}