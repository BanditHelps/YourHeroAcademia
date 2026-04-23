package com.github.bandithelps.blocks;

import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.github.bandithelps.YourHeroAcademia;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(YourHeroAcademia.MODID);

    public static final DeferredHolder<Block, DNAAnalyzerBlock> DNA_ANALYZER = BLOCKS.register(
            "dna_analyzer",
            DNAAnalyzerBlock::new
    );

    public static final DeferredHolder<Block, DNASplicerBlock> DNA_SPLICER = BLOCKS.register(
            "dna_splicer",
            DNASplicerBlock::new
    );

    private ModBlocks() {
    }
}