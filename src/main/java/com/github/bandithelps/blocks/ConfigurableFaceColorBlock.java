package com.github.bandithelps.blocks;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class ConfigurableFaceColorBlock extends Block {
    public static final EnumProperty<DyeColor> FACE_COLOR = EnumProperty.create("face_color", DyeColor.class);

    public ConfigurableFaceColorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACE_COLOR, DyeColor.WHITE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE_COLOR);
    }
}
