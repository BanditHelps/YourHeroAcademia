package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.attributes.QuirkAttributes;
import com.github.bandithelps.client.attributes.ClientAttributeState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.threetag.palladium.client.gui.ui.UiAlignment;
import net.threetag.palladium.client.gui.ui.component.*;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.util.PalladiumCodecs;

import java.util.Locale;

public class PlayerAttributeValueUiComponent extends RenderableUiComponent {

    private static final int BASE_VALUE_COLOR = 0xFFB0B0B0;
    private static final int BUFFED_VALUE_COLOR = 0xFF55FF55;
    private static final int NERFED_VALUE_COLOR = 0xFFFF5555;
    private static final double COMPARISON_EPSILON = 1.0E-6D;

    public static final MapCodec<PlayerAttributeValueUiComponent> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
            Codec.STRING.fieldOf("attribute").forGetter(PlayerAttributeValueUiComponent::getAttributeId),
            Codec.STRING.optionalFieldOf("label", "").forGetter(PlayerAttributeValueUiComponent::getLabel),
            Codec.INT.optionalFieldOf("decimals", 2).forGetter(PlayerAttributeValueUiComponent::getDecimals),
            PalladiumCodecs.COLOR_INT_CODEC.optionalFieldOf("color", 0xFFFFFFFF).forGetter(PlayerAttributeValueUiComponent::getColor),
            Codec.BOOL.optionalFieldOf("shadow", true).forGetter(PlayerAttributeValueUiComponent::hasShadow),
            AbstractStringUiComponent.TEXT_ALIGNMENT_CODEC.optionalFieldOf("alignment", TextAlignment.LEFT).forGetter(PlayerAttributeValueUiComponent::getTextAlignment),
            propertiesCodec()
    ).apply(instance, PlayerAttributeValueUiComponent::new));

    private final String attributeId;
    private final String label;
    private final int decimals;
    private final int color;
    private final boolean shadow;
    private final TextAlignment alignment;

    public PlayerAttributeValueUiComponent(
            String attributeId,
            String label,
            int decimals,
            int color,
            boolean shadow,
            TextAlignment alignment,
            UiComponentProperties properties
    ) {
        super(properties);
        this.attributeId = attributeId;
        this.label = label == null ? "" : label.trim();
        this.decimals = Mth.clamp(decimals, 0, 4);
        this.color = color;
        this.shadow = shadow;
        this.alignment = alignment;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor gui, DataContext context, int x, int y, int width, int height, int mouseX, int mouseY, UiAlignment alignment) {
        boolean hasLabel = !this.label.isEmpty();

        if (minecraft.player == null) {
            String fallback = hasLabel ? this.label + ": --" : "--";
            gui.text(minecraft.font, fallback, alignedX(minecraft, fallback, x, width), y, this.color, this.shadow);
            return;
        }

        Holder<Attribute> attribute = resolveAttribute(this.attributeId);
        if (attribute == null) {
            String fallback = hasLabel ? this.label + ": ?" : "?";
            gui.text(minecraft.font, fallback, alignedX(minecraft, fallback, x, width), y, this.color, this.shadow);
            return;
        }

        double value = getDisplayValue(minecraft, attribute, this.attributeId);
        double baseValue = getBaseValue(this.attributeId, attribute);
        int valueColor = getValueColor(value, baseValue);
        String valueText = formatValue(value);

        if (!hasLabel) {
            gui.text(minecraft.font, valueText, alignedX(minecraft, valueText, x, width), y, valueColor, this.shadow);
        } else {
            String labelPart = this.label + ": ";
            int totalWidth = minecraft.font.width(labelPart) + minecraft.font.width(valueText);
            int startX = switch (this.alignment) {
                case CENTER -> x + (width - totalWidth) / 2;
                case RIGHT -> x + width - totalWidth;
                default -> x;
            };
            gui.text(minecraft.font, labelPart, startX, y, this.color, this.shadow);
            gui.text(minecraft.font, valueText, startX + minecraft.font.width(labelPart), y, valueColor, this.shadow);
        }
    }

    private int alignedX(Minecraft minecraft, String text, int x, int width) {
        int textWidth = minecraft.font.width(text);
        return switch (this.alignment) {
            case CENTER -> x + (width - textWidth) / 2;
            case RIGHT -> x + width - textWidth;
            default -> x;
        };
    }

    private static double getDisplayValue(Minecraft minecraft, Holder<Attribute> attribute, String attributeId) {
        if ("attack_damage".equals(attributeId) && ClientAttributeState.isAttackDamageInitialized()) {
            return ClientAttributeState.getAttackDamage();
        }
        return minecraft.player.getAttributeValue(attribute);
    }

    private static double getBaseValue(String attributeId, Holder<Attribute> attribute) {
        return switch (attributeId) {
            case "attack_damage" -> 1.0;
            case "movement_speed" -> 0.1;
            default -> attribute.value().getDefaultValue();
        };
    }

    private int getValueColor(double value, double baseValue) {
        if (value > baseValue + COMPARISON_EPSILON) return BUFFED_VALUE_COLOR;
        if (value < baseValue - COMPARISON_EPSILON) return NERFED_VALUE_COLOR;
        return BASE_VALUE_COLOR;
    }

    private String formatValue(double value) {
        return String.format(Locale.ROOT, "%." + this.decimals + "f", value);
    }

    private static Holder<Attribute> resolveAttribute(String attributeId) {
        return switch (attributeId) {
            case "quirk_factor" -> QuirkAttributes.QUIRK_FACTOR;
            case "attack_damage" -> Attributes.ATTACK_DAMAGE;
            case "attack_speed" -> Attributes.ATTACK_SPEED;
            case "movement_speed" -> Attributes.MOVEMENT_SPEED;
            case "jump_height" -> Attributes.JUMP_STRENGTH;
            case "max_health" -> Attributes.MAX_HEALTH;
            case "armor" -> Attributes.ARMOR;
            case "armor_toughness" -> Attributes.ARMOR_TOUGHNESS;
            case "block_break_speed" -> Attributes.BLOCK_BREAK_SPEED;
            default -> null;
        };
    }

    public String getAttributeId() { return attributeId; }
    public String getLabel() { return label; }
    public int getDecimals() { return decimals; }
    public int getColor() { return color; }
    public boolean hasShadow() { return shadow; }
    public TextAlignment getTextAlignment() { return alignment; }

    @Override
    public UiComponentSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.PLAYER_ATTRIBUTE_VALUE;
    }

    public static class Serializer extends UiComponentSerializer<PlayerAttributeValueUiComponent> {
        @Override
        public MapCodec<PlayerAttributeValueUiComponent> codec() {
            return PlayerAttributeValueUiComponent.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiComponent, PlayerAttributeValueUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("Player Attribute Value")
                    .setDescription("Renders a labeled player attribute value and colors it based on vanilla player defaults.")
                    .add("attribute", TYPE_STRING, "Attribute id to render (e.g. quirk_factor, attack_strength, movement_speed).")
                    .add("label", TYPE_STRING, "Optional text label shown before the numeric value.")
                    .add("decimals", TYPE_INT, "Number of decimals used when formatting the value.");
        }
    }
}
