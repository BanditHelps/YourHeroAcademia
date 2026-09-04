package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.Config;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.client.body.ClientBodyState;
import com.github.bandithelps.creation.CreationUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.core.HolderLookup;
import net.threetag.palladium.client.gui.ui.UiAlignment;
import net.threetag.palladium.client.gui.ui.widget.AbstractStringUiWidget;
import net.threetag.palladium.client.gui.ui.widget.RenderableUiWidget;
import net.threetag.palladium.client.gui.ui.widget.UiWidget;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetProperties;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetSerializer;
import net.threetag.palladium.client.util.RenderUtil;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.util.PalladiumCodecs;

public class MaxLipidsUiComponent extends RenderableUiWidget {

    public static final MapCodec<MaxLipidsUiComponent> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
            PalladiumCodecs.COLOR_INT_CODEC.optionalFieldOf("color", RenderUtil.DEFAULT_GRAY).forGetter(MaxLipidsUiComponent::getColor),
            Codec.BOOL.optionalFieldOf("shadow", false).forGetter(MaxLipidsUiComponent::hasShadow),
            AbstractStringUiWidget.TEXT_ALIGNMENT_CODEC.optionalFieldOf("alignment", TextAlignment.LEFT).forGetter(MaxLipidsUiComponent::getTextAlignment),
            propertiesCodec()
    ).apply(instance, MaxLipidsUiComponent::new));

    private final int color;
    private final boolean shadow;
    private final TextAlignment alignment;

    public MaxLipidsUiComponent(int color, boolean shadow, TextAlignment alignment, UiWidgetProperties properties) {
        super(properties);
        this.color = color;
        this.shadow = shadow;
        this.alignment = alignment;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor gui, DataContext context, int x, int y, int width, int height, int mouseX, int mouseY, UiAlignment alignment) {
        float maxLipids = ClientBodyState.getCustomFloat(
                BodyPart.CHEST,
                CreationUtil.MAX_LIPIDS_KEY,
                Config.CREATION_MAX_LIPIDS.get()
        );
        String text = String.valueOf(Math.round(maxLipids));
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
        return YhaUiComponentSerializers.MAX_LIPIDS;
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

    public static class Serializer extends UiWidgetSerializer<MaxLipidsUiComponent> {
        @Override
        public MapCodec<MaxLipidsUiComponent> codec() {
            return MaxLipidsUiComponent.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiWidget, MaxLipidsUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("Max Lipids").setDescription("Renders the Creation user's current lipid capacity.");
        }
    }
}
