package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.client.stamina.ClientStaminaState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.threetag.palladium.client.gui.ui.component.*;
import net.threetag.palladium.client.gui.ui.screen.UiScreen;
import net.threetag.palladium.client.util.RenderUtil;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.util.PalladiumCodecs;

public class UpgradePointUiComponent extends AbstractStringUiComponent {

    public static final MapCodec<UpgradePointUiComponent> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
            PalladiumCodecs.COLOR_INT_CODEC.optionalFieldOf("color", RenderUtil.DEFAULT_GRAY).forGetter(AbstractStringUiComponent::getColor),
            Codec.BOOL.optionalFieldOf("shadow", false).forGetter(AbstractStringUiComponent::hasShadow),
            TEXT_ALIGNMENT_CODEC.optionalFieldOf("alignment", TextAlignment.LEFT).forGetter(AbstractStringUiComponent::getTextAlignment),
            TEXT_OVERFLOW_CODEC.optionalFieldOf("overflow", StringWidget.TextOverflow.CLAMPED).forGetter(AbstractStringUiComponent::getTextOverflow),
            propertiesCodec()
    ).apply(instance, UpgradePointUiComponent::new));

    public UpgradePointUiComponent(int color, boolean shadow, TextAlignment alignment, StringWidget.TextOverflow textOverflow, UiComponentProperties properties) {
        super(color, shadow, alignment, textOverflow, properties);
    }

    @Override
    public Component getText(UiScreen uiScreen) {
        int upgradePoints = ClientStaminaState.getUpgradePoints();
        return Component.literal(String.valueOf(upgradePoints));
    }

    @Override
    public UiComponentSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.UPGRADE_POINTS;
    }

    public static class Serializer extends AbstractStringUiComponent.AbstractStringUiComponentSerializer<UpgradePointUiComponent> {
        public MapCodec<UpgradePointUiComponent> codec() {
            return UpgradePointUiComponent.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<UiComponent, UpgradePointUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("Upgrade Points").setDescription("Renders the number of upgrade points the player has.");
        }
    }
}
