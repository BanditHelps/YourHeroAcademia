package com.github.bandithelps.creation;

import com.github.bandithelps.network.CreationCreatePayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Saved create-button recipe for a quick-craft slot. Legacy saves that stored a plain item id
 * still parse as an item recipe with no enchantments.
 */
public final class CreationQuickSlot {
    private static final String POTION_PREFIX = "potion:";
    private static final String ENCHANT_SEP = "|ench:";
    private static final String FORM_KEY = "form:";
    private static final String TICKS_KEY = "ticks:";
    private static final String AMP_KEY = "amp:";

    public enum Kind {
        ITEM,
        POTION
    }

    private final Kind kind;
    private final Identifier id;
    private final List<CreationCreatePayload.EnchantChoice> enchants;
    private final CreationPotionForm form;
    private final int durationTicks;
    private final int amplifier;

    private CreationQuickSlot(
            Kind kind,
            Identifier id,
            List<CreationCreatePayload.EnchantChoice> enchants,
            CreationPotionForm form,
            int durationTicks,
            int amplifier
    ) {
        this.kind = kind;
        this.id = id;
        this.enchants = List.copyOf(enchants == null ? List.of() : enchants);
        this.form = form == null ? CreationPotionForm.DRINKABLE : form;
        this.durationTicks = Math.max(1, durationTicks);
        this.amplifier = Math.max(0, amplifier);
    }

    public static CreationQuickSlot item(Identifier itemId, List<CreationCreatePayload.EnchantChoice> enchants) {
        if (itemId == null) {
            return null;
        }
        return new CreationQuickSlot(Kind.ITEM, itemId, enchants, CreationPotionForm.DRINKABLE, 1, 0);
    }

    public static CreationQuickSlot item(Identifier itemId) {
        return item(itemId, List.of());
    }

    public static CreationQuickSlot potion(Identifier effectId, CreationPotionForm form, int durationTicks, int amplifier) {
        if (effectId == null) {
            return null;
        }
        return new CreationQuickSlot(Kind.POTION, effectId, List.of(), form, durationTicks, amplifier);
    }

    public static CreationQuickSlot parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith(POTION_PREFIX)) {
            return parsePotion(trimmed.substring(POTION_PREFIX.length()));
        }
        return parseItem(trimmed);
    }

    public Kind kind() {
        return this.kind;
    }

    public boolean isPotion() {
        return this.kind == Kind.POTION;
    }

    public Identifier id() {
        return this.id;
    }

    public List<CreationCreatePayload.EnchantChoice> enchants() {
        return this.enchants;
    }

    public CreationPotionForm form() {
        return this.form;
    }

    public int durationTicks() {
        return this.durationTicks;
    }

    public int amplifier() {
        return this.amplifier;
    }

    public String encode() {
        if (this.kind == Kind.POTION) {
            return POTION_PREFIX + this.id
                    + "|" + FORM_KEY + this.form.id()
                    + "|" + TICKS_KEY + this.durationTicks
                    + "|" + AMP_KEY + this.amplifier;
        }
        String encoded = this.id.toString();
        if (this.enchants.isEmpty()) {
            return encoded;
        }
        StringBuilder builder = new StringBuilder(encoded).append(ENCHANT_SEP);
        boolean first = true;
        for (CreationCreatePayload.EnchantChoice choice : this.enchants) {
            if (choice == null || choice.enchantId() == null || choice.enchantId().isBlank() || choice.level() <= 0) {
                continue;
            }
            if (!first) {
                builder.append(',');
            }
            builder.append(choice.enchantId()).append('=').append(choice.level());
            first = false;
        }
        return first ? this.id.toString() : builder.toString();
    }

    public ItemStack iconStack(HolderLookup.Provider access) {
        if (this.kind == Kind.POTION) {
            return CreationPotions.stackOf(this.id, this.form, this.durationTicks, this.amplifier);
        }
        ItemStack stack = CreationCatalog.stackOf(this.id);
        if (stack.isEmpty() || this.enchants.isEmpty()) {
            return stack;
        }
        stack = stack.copy();
        Map<Identifier, Integer> levels = new LinkedHashMap<>();
        for (CreationCreatePayload.EnchantChoice choice : this.enchants) {
            if (choice == null || choice.enchantId() == null || choice.enchantId().isBlank() || choice.level() <= 0) {
                continue;
            }
            try {
                levels.put(Identifier.parse(choice.enchantId()), choice.level());
            } catch (RuntimeException ignored) {
            }
        }
        CreationEnchantments.apply(stack, access, levels);
        return stack;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CreationQuickSlot other)) {
            return false;
        }
        return Objects.equals(encode(), other.encode());
    }

    @Override
    public int hashCode() {
        return encode().hashCode();
    }

    private static CreationQuickSlot parseItem(String raw) {
        int enchAt = raw.indexOf(ENCHANT_SEP);
        String itemPart = enchAt < 0 ? raw : raw.substring(0, enchAt);
        Identifier itemId = parseId(itemPart);
        if (itemId == null) {
            return null;
        }
        List<CreationCreatePayload.EnchantChoice> enchants = new ArrayList<>();
        if (enchAt >= 0) {
            String rest = raw.substring(enchAt + ENCHANT_SEP.length());
            if (!rest.isBlank()) {
                for (String piece : rest.split(",")) {
                    int eq = piece.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String enchantId = piece.substring(0, eq).trim();
                    if (enchantId.isEmpty()) {
                        continue;
                    }
                    try {
                        int level = Integer.parseInt(piece.substring(eq + 1).trim());
                        if (level > 0) {
                            enchants.add(new CreationCreatePayload.EnchantChoice(enchantId, level));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return item(itemId, enchants);
    }

    private static CreationQuickSlot parsePotion(String raw) {
        String[] parts = raw.split("\\|");
        if (parts.length == 0) {
            return null;
        }
        Identifier effectId = parseId(parts[0]);
        if (effectId == null) {
            return null;
        }
        CreationPotionForm form = CreationPotionForm.DRINKABLE;
        int ticks = CreationPotionEntry.DEFAULT_DURATION_SECONDS * 20;
        int amp = 0;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.startsWith(FORM_KEY)) {
                form = CreationPotionForm.fromId(part.substring(FORM_KEY.length()));
            } else if (part.startsWith(TICKS_KEY)) {
                try {
                    ticks = Integer.parseInt(part.substring(TICKS_KEY.length()));
                } catch (NumberFormatException ignored) {
                }
            } else if (part.startsWith(AMP_KEY)) {
                try {
                    amp = Integer.parseInt(part.substring(AMP_KEY.length()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return potion(effectId, form, ticks, amp);
    }

    private static Identifier parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Identifier.parse(raw.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
