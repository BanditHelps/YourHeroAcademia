package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.attributes.QuirkAttributes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.threetag.palladium.client.gui.ui.component.AbstractStringUiComponent;
import net.threetag.palladium.client.gui.ui.component.UiComponent;
import net.threetag.palladium.client.gui.ui.component.UiComponentProperties;
import net.threetag.palladium.client.gui.ui.component.UiComponentSerializer;
import net.threetag.palladium.client.gui.ui.screen.UiScreen;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.util.PalladiumCodecs;

import java.util.Locale;

public class PlayerAttributeValueUiComponent extends AbstractStringUiComponent {

    private static final int BASE_VALUE_COLOR = 0xFFB0B0B0;
    private static final int BUFFED_VALUE_COLOR = 0xFF55FF55;
    private static final int NERFED_VALUE_COLOR = 0xFFFF5555;
    private static final double COMPARISON_EPSILON = 1.0E-6D;

    public static final MapCodec<PlayerAttributeValueUiComponent> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
            Codec.STRING.fieldOf("attribute").forGetter(PlayerAttributeValueUiComponent::getAttributeId),
            Codec.STRING.optionalFieldOf("label", "").forGetter(PlayerAttributeValueUiComponent::getLabel),
            Codec.INT.optionalFieldOf("decimals", 2).forGetter(PlayerAttributeValueUiComponent::getDecimals),
            PalladiumCodecs.COLOR_INT_CODEC.optionalFieldOf("color", 0xFFFFFFFF).forGetter(AbstractStringUiComponent::getColor),
            Codec.BOOL.optionalFieldOf("shadow", true).forGetter(AbstractStringUiComponent::hasShadow),
            TEXT_ALIGNMENT_CODEC.optionalFieldOf("alignment", TextAlignment.LEFT).forGetter(AbstractStringUiComponent::getTextAlignment),
            TEXT_OVERFLOW_CODEC.optionalFieldOf("overflow", StringWidget.TextOverflow.CLAMPED).forGetter(AbstractStringUiComponent::getTextOverflow),
            propertiesCodec()
    ).apply(instance, PlayerAttributeValueUiComponent::new));

    private final String attributeId;
    private final String label;
    private final int decimals;

    public PlayerAttributeValueUiComponent(
            String attributeId,
            String label,
            int decimals,
            int color,
            boolean shadow,
            TextAlignment alignment,
            StringWidget.TextOverflow textOverflow,
            UiComponentProperties properties
    ) {
        super(color, shadow, alignment, textOverflow, properties);
        this.attributeId = attributeId;
        this.label = label;
        this.decimals = Mth.clamp(decimals, 0, 4);
    }

    @Override
    public Component getText(UiScreen uiScreen) {
        Minecraft minecraft = Minecraft.getInstance();
        String normalizedLabel = this.label == null ? "" : this.label.trim();
        boolean hasLabel = !normalizedLabel.isEmpty();
        if (minecraft.player == null) {
            return hasLabel
                    ? Component.literal(normalizedLabel + ": --")
                    : Component.literal("--");
        }

        Holder<Attribute> attribute = resolveAttribute(this.attributeId);
        if (attribute == null) {
            return hasLabel
                    ? Component.literal(normalizedLabel + ": ?")
                    : Component.literal("?");
        }

        double value = minecraft.player.getAttributeValue(attribute);
        double baseValue;

        // Custom values for these because minecraft's "default" is a lie apparently
        if (attributeId.equals("attack_strength")) {
            baseValue = 1.0;
        } else if (attributeId.equals("movement_speed")) {
            baseValue = 0.1;
        } else {
            baseValue = attribute.value().getDefaultValue();
        }


        int valueColor = getValueColor(value, baseValue);

        if (!hasLabel) {
            return Component.literal(formatValue(value)).withColor(valueColor);
        }

        return Component.empty()
                .append(Component.literal(normalizedLabel + ": ").withColor(getColor()))
                .append(Component.literal(formatValue(value)).withColor(valueColor));
    }

    private int getValueColor(double value, double baseValue) {
        if (value > baseValue + COMPARISON_EPSILON) {
            return BUFFED_VALUE_COLOR;
        }
        if (value < baseValue - COMPARISON_EPSILON) {
            return NERFED_VALUE_COLOR;
        }
        return BASE_VALUE_COLOR;
    }

    private String formatValue(double value) {
        return String.format(Locale.ROOT, "%." + this.decimals + "f", value);
    }

    private static Holder<Attribute> resolveAttribute(String attributeId) {
        return switch (attributeId) {
            case "quirk_factor" -> QuirkAttributes.QUIRK_FACTOR;
            case "attack_strength" -> Attributes.ATTACK_DAMAGE;
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

    public String getAttributeId() {
        return attributeId;
    }

    public String getLabel() {
        return label;
    }

    public int getDecimals() {
        return decimals;
    }

    @Override
    public UiComponentSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.PLAYER_ATTRIBUTE_VALUE;
    }

    public static class Serializer extends AbstractStringUiComponent.AbstractStringUiComponentSerializer<PlayerAttributeValueUiComponent> {
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
