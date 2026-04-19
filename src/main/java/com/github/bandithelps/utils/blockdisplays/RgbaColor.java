package com.github.bandithelps.utils.blockdisplays;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

public record RgbaColor(int red, int green, int blue, int alpha) {
    public static final Codec<RgbaColor> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ExtraCodecs.intRange(0, 255).fieldOf("r").forGetter(RgbaColor::red),
                    ExtraCodecs.intRange(0, 255).fieldOf("g").forGetter(RgbaColor::green),
                    ExtraCodecs.intRange(0, 255).fieldOf("b").forGetter(RgbaColor::blue),
                    ExtraCodecs.intRange(0, 255).optionalFieldOf("a", 255).forGetter(RgbaColor::alpha)
            ).apply(instance, RgbaColor::new)
    );

    public int toArgb() {
        return ((this.alpha & 0xFF) << 24)
                | ((this.red & 0xFF) << 16)
                | ((this.green & 0xFF) << 8)
                | (this.blue & 0xFF);
    }

    public static DataResult<RgbaColor> fromArgbInt(int argb) {
        return DataResult.success(new RgbaColor(
                (argb >> 16) & 0xFF,
                (argb >> 8) & 0xFF,
                argb & 0xFF,
                (argb >> 24) & 0xFF
        ));
    }
}
