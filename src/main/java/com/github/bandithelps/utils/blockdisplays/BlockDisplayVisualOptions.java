package com.github.bandithelps.utils.blockdisplays;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.blocks.ConfigurableFaceColorBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public record BlockDisplayVisualOptions(
        boolean glowing,
        Optional<Integer> glowColorOverride,
        Optional<Integer> brightnessBlock,
        Optional<Integer> brightnessSky,
        boolean useFaceColorBlock,
        Optional<DyeColor> faceColor,
        Optional<RgbaColor> rgbaColor,
        RgbaBlendMode rgbaBlendMode) {

    public static final BlockDisplayVisualOptions DEFAULT = new BlockDisplayVisualOptions(
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            false,
            Optional.empty(),
            Optional.empty(),
            RgbaBlendMode.NORMAL
    );

    public static final Codec<BlockDisplayVisualOptions> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.optionalFieldOf("glowing", false).forGetter(BlockDisplayVisualOptions::glowing),
                    Codec.INT.optionalFieldOf("glow_color_override").forGetter(BlockDisplayVisualOptions::glowColorOverride),
                    ExtraCodecs.intRange(0, 15).optionalFieldOf("brightness_block").forGetter(BlockDisplayVisualOptions::brightnessBlock),
                    ExtraCodecs.intRange(0, 15).optionalFieldOf("brightness_sky").forGetter(BlockDisplayVisualOptions::brightnessSky),
                    Codec.BOOL.optionalFieldOf("use_face_color_block", false).forGetter(BlockDisplayVisualOptions::useFaceColorBlock),
                    DyeColor.CODEC.optionalFieldOf("face_color").forGetter(BlockDisplayVisualOptions::faceColor),
                    RgbaColor.CODEC.optionalFieldOf("rgba_color").forGetter(BlockDisplayVisualOptions::rgbaColor),
                    RgbaBlendMode.CODEC.optionalFieldOf("rgba_blend_mode", RgbaBlendMode.NORMAL).forGetter(BlockDisplayVisualOptions::rgbaBlendMode)
            ).apply(instance, BlockDisplayVisualOptions::new)
    );

    public void applyTo(BetterBlockDisplay display) {
        if (display == null) {
            return;
        }

        display.setGlowingEnabled(this.glowing);
        this.glowColorOverride.ifPresent(display::setGlowColorOverride);

        if (this.brightnessBlock.isPresent() && this.brightnessSky.isPresent()) {
            int packed = packLight(this.brightnessBlock.get(), this.brightnessSky.get());
            display.setPackedBrightnessOverride(packed);
        }
    }

    private static int packLight(int blockLight, int skyLight) {
        return (blockLight & 15) << 4 | (skyLight & 15) << 20;
    }

    public Optional<BlockState> getFaceColorBlockStateOverride() {
        if (!this.useFaceColorBlock) {
            return Optional.empty();
        }

        DyeColor color = this.faceColor.orElse(DyeColor.WHITE);
        BlockState state = YourHeroAcademia.CONFIGURABLE_FACE_BLOCK.get()
                .defaultBlockState()
                .setValue(ConfigurableFaceColorBlock.FACE_COLOR, color);
        return Optional.of(state);
    }

    public Optional<Integer> getRgbaArgb() {
        return this.rgbaColor.map(RgbaColor::toArgb);
    }

    public RgbaBlendMode getRgbaBlendMode() {
        return this.rgbaBlendMode;
    }
}
