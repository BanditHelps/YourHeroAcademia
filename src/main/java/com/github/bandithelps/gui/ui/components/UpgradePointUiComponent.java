package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.client.stamina.ClientStaminaState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.core.HolderLookup;
import net.threetag.palladium.client.gui.ui.UiAlignment;
import net.threetag.palladium.client.gui.ui.screen.UiScreen;
import net.threetag.palladium.client.gui.ui.widget.*;
import net.threetag.palladium.client.util.RenderUtil;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.util.PalladiumCodecs;

public class UpgradePointUiComponent extends RenderableUiWidget {

    public static final MapCodec<UpgradePointUiComponent> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
            PalladiumCodecs.COLOR_INT_CODEC.optionalFieldOf("color", RenderUtil.DEFAULT_GRAY).forGetter(UpgradePointUiComponent::getColor),
            Codec.BOOL.optionalFieldOf("shadow", false).forGetter(UpgradePointUiComponent::hasShadow),
            AbstractStringUiWidget.TEXT_ALIGNMENT_CODEC.optionalFieldOf("alignment", TextAlignment.LEFT).forGetter(UpgradePointUiComponent::getTextAlignment),
            propertiesCodec()
    ).apply(instance, UpgradePointUiComponent::new));

    private final int color;
    private final boolean shadow;
    private final TextAlignment alignment;

    public UpgradePointUiComponent(int color, boolean shadow, TextAlignment alignment, UiWidgetProperties properties) {
        super(properties);
        this.color = color;
        this.shadow = shadow;
        this.alignment = alignment;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor gui, DataContext context, int x, int y, int width, int height, int mouseX, int mouseY, UiAlignment alignment) {
        int upgradePoints = ClientStaminaState.getUpgradePoints();
        String text = String.valueOf(upgradePoints);
        int textWidth = minecraft.font.width(text);
        int textX = switch (this.alignment) {
            case CENTER -> x + (width - textWidth) / 2;
            case RIGHT -> x + width - textWidth;
            default -> x;
        };
        gui.text(minecraft.font, text, textX, y, this.color, this.shadow);
    }

    @Override
    public UiWidgetSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.UPGRADE_POINTS;
    }

    public int getColor() {
        return color;
    }

    public boolean hasShadow() {
        return shadow;
    }

    public TextAlignment getTextAlignment() {
        return alignment;
    }

    public static class Serializer extends UiWidgetSerializer<UpgradePointUiComponent> {
        @Override
        public MapCodec<UpgradePointUiComponent> codec() {
            return UpgradePointUiComponent.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiWidget, UpgradePointUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("Upgrade Points").setDescription("Renders the number of upgrade points the player has.");
        }
    }
}
