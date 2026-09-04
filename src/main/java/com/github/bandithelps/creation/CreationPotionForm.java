package com.github.bandithelps.creation;

import java.util.Locale;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum CreationPotionForm {
    DRINKABLE(1.0f, Items.POTION),
    SPLASH(1.25f, Items.SPLASH_POTION),
    LINGERING(1.5f, Items.LINGERING_POTION),
    ARROW(0.5f, Items.TIPPED_ARROW);

    private final float factor;
    private final Item item;

    CreationPotionForm(float factor, Item item) {
        this.factor = factor;
        this.item = item;
    }

    public float factor() {
        return this.factor;
    }

    public Item item() {
        return this.item;
    }

    public String itemNameKey() {
        return switch (this) {
            case DRINKABLE -> "item.yha.creation.potion";
            case SPLASH -> "item.yha.creation.splash_potion";
            case LINGERING -> "item.yha.creation.lingering_potion";
            case ARROW -> "item.yha.creation.tipped_arrow";
        };
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static CreationPotionForm fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return DRINKABLE;
        }
        try {
            return CreationPotionForm.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DRINKABLE;
        }
    }
}
