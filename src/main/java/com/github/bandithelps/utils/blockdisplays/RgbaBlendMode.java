package com.github.bandithelps.utils.blockdisplays;

import com.mojang.serialization.Codec;

public enum RgbaBlendMode {
    NORMAL("normal"),
    ADDITIVE("additive");

    public static final Codec<RgbaBlendMode> CODEC = Codec.STRING.xmap(RgbaBlendMode::fromName, RgbaBlendMode::serializedName);

    private final String serializedName;

    RgbaBlendMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public static RgbaBlendMode fromName(String name) {
        for (RgbaBlendMode mode : values()) {
            if (mode.serializedName.equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return NORMAL;
    }
}
