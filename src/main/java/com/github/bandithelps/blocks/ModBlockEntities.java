package com.github.bandithelps.blocks;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.github.bandithelps.YourHeroAcademia;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, YourHeroAcademia.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DNAAnalyzerBlockEntity>> DNA_ANALYZER =
            BLOCK_ENTITIES.register(
                    "dna_analyzer",
                    () -> new BlockEntityType<>(
                            DNAAnalyzerBlockEntity::new,
                            false,
                            YourHeroAcademia.DNA_ANALYZER_BLOCK.get()
                    )
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DNASplicerBlockEntity>> DNA_SPLICER =
            BLOCK_ENTITIES.register(
                    "dna_splicer",
                    () -> new BlockEntityType<>(
                            DNASplicerBlockEntity::new,
                            false,
                            ModBlocks.DNA_SPLICER.get()
                    )
            );

    private ModBlockEntities() {
    }
}