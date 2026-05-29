package com.github.bandithelps.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Work in progress block. Does not do anything as of now.
 */
public class DNASplicerBlockEntity extends BlockEntity {

    public DNASplicerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DNA_SPLICER.get(), pos, state);
    }
}