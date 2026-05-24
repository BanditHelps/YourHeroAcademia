package com.github.bandithelps.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.github.bandithelps.YourHeroAcademia;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(YourHeroAcademia.MODID);

    // Creates a new Block with the id "yourheroacademia:example_block", combining the namespace and path
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", p -> p.mapColor(MapColor.STONE));

    public static final DeferredBlock<ConfigurableFaceColorBlock> CONFIGURABLE_FACE_BLOCK = BLOCKS.registerBlock(
            "configurable_face_block",
            ConfigurableFaceColorBlock::new,
            properties -> properties
                    .mapColor(MapColor.SNOW)
                    .strength(1.0F)
                    .noOcclusion()
    );

    public static final DeferredBlock<TreadmillBlock> TREADMILL_BLOCK = BLOCKS.registerBlock(
            "treadmill",
            TreadmillBlock::new,
            p -> p.mapColor(MapColor.METAL)
                    .strength(2.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
    );

    public static final DeferredBlock<DNAAnalyzerBlock> DNA_ANALYZER = BLOCKS.registerBlock(
            "dna_analyzer",
            DNAAnalyzerBlock::new,
            p -> p.mapColor(MapColor.METAL)
                    .strength(2.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
    );

    public static final DeferredBlock<DNASplicerBlock> DNA_SPLICER = BLOCKS.registerBlock(
            "dna_splicer",
            DNASplicerBlock::new,
            p -> p.mapColor(MapColor.METAL)
                    .strength(2.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
    );

    public static final DeferredBlock<GeneCombinerBlock> GENE_COMBINER = BLOCKS.registerBlock(
            "gene_combiner",
            GeneCombinerBlock::new,
            p -> p.mapColor(MapColor.METAL)
                    .strength(2.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
    );

    public static final DeferredBlock<BioPrinterBlock> BIO_PRINTER = BLOCKS.registerBlock(
            "bio_printer",
            BioPrinterBlock::new,
            p -> p.mapColor(MapColor.METAL)
                    .strength(2.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
    );

    private ModBlocks() {
    }
}