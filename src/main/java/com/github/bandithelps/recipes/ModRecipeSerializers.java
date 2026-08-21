package com.github.bandithelps.recipes;

import com.github.bandithelps.YourHeroAcademia;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, YourHeroAcademia.MODID);

    private static final MapCodec<SmokeCanisterInfusionRecipe> SMOKE_CANISTER_INFUSION_CODEC = MapCodec.unit(new SmokeCanisterInfusionRecipe());
    private static final StreamCodec<RegistryFriendlyByteBuf, SmokeCanisterInfusionRecipe> SMOKE_CANISTER_INFUSION_STREAM_CODEC = StreamCodec.unit(new SmokeCanisterInfusionRecipe());
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SmokeCanisterInfusionRecipe>> SMOKE_CANISTER_INFUSION =
            RECIPE_SERIALIZERS.register("smoke_canister_infusion", () -> new RecipeSerializer<>(SMOKE_CANISTER_INFUSION_CODEC, SMOKE_CANISTER_INFUSION_STREAM_CODEC));

    private ModRecipeSerializers() {
    }
}
