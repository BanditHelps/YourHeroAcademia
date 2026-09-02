package com.github.bandithelps.loot;

import com.github.bandithelps.YourHeroAcademia;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModLootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, YourHeroAcademia.MODID);

    public static final Supplier<MapCodec<AddBookOfKnowledgeLootModifier>> ADD_BOOK_OF_KNOWLEDGE =
            SERIALIZERS.register("add_book_of_knowledge", () -> AddBookOfKnowledgeLootModifier.CODEC);

    private ModLootModifiers() {
    }
}
