package com.github.bandithelps.recipes;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.items.SmokeCanisterData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class SmokeCanisterInfusionRecipe extends CustomRecipe {
    public SmokeCanisterInfusionRecipe() {
        super();
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        RecipeInputs parsed = this.parseInput(input);
        return parsed.valid();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        RecipeInputs parsed = this.parseInput(input);
        if (!parsed.valid()) {
            return ItemStack.EMPTY;
        }

        var potionContents = parsed.potion().get(DataComponents.POTION_CONTENTS);
        if (potionContents == null) {
            return ItemStack.EMPTY;
        }

        ItemStack infused = new ItemStack(YourHeroAcademia.INFUSED_SMOKE_CANISTER.get());
        SmokeCanisterData.setTier(infused, SmokeCanisterData.getTier(parsed.canister()));
        SmokeCanisterData.setPotionContents(infused, potionContents);
        return infused;
    }

    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 3;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return ModRecipeSerializers.SMOKE_CANISTER_INFUSION.get();
    }

    private RecipeInputs parseInput(CraftingInput input) {
        ItemStack canister = ItemStack.EMPTY;
        ItemStack potion = ItemStack.EMPTY;
        ItemStack pipette = ItemStack.EMPTY;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            if (isInfusableCanister(stack)) {
                if (!canister.isEmpty()) {
                    return RecipeInputs.INVALID;
                }
                canister = stack;
                continue;
            }

            if (stack.is(YourHeroAcademia.PIPETTE.get())) {
                if (!pipette.isEmpty()) {
                    return RecipeInputs.INVALID;
                }
                pipette = stack;
                continue;
            }

            if (stack.has(DataComponents.POTION_CONTENTS)) {
                if (!potion.isEmpty()) {
                    return RecipeInputs.INVALID;
                }
                potion = stack;
                continue;
            }

            return RecipeInputs.INVALID;
        }

        if (canister.isEmpty() || potion.isEmpty() || pipette.isEmpty()) {
            return RecipeInputs.INVALID;
        }
        return new RecipeInputs(canister, potion, pipette);
    }

    private static boolean isInfusableCanister(ItemStack stack) {
        return stack.is(YourHeroAcademia.EMPTY_CANISTER.get())
                || stack.is(YourHeroAcademia.FILLED_SMOKE_CANISTER.get())
                || stack.is(YourHeroAcademia.INFUSED_SMOKE_CANISTER.get());
    }

    private record RecipeInputs(ItemStack canister, ItemStack potion, ItemStack injector) {
        private static final RecipeInputs INVALID = new RecipeInputs(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);

        private boolean valid() {
            return !this.canister.isEmpty() && !this.potion.isEmpty() && !this.injector.isEmpty();
        }
    }
}
