package com.github.bandithelps.gui.ui.layouts;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.Codec;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.threetag.palladium.client.gui.ui.component.UiComponent;
import net.threetag.palladium.client.gui.ui.layout.UiLayout;
import net.threetag.palladium.client.gui.ui.layout.UiLayoutSerializer;
import net.threetag.palladium.client.gui.ui.screen.UiScreen;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class AnchoredMultiColumnLayout extends UiLayout {

    public static final MapCodec<AnchoredMultiColumnLayout> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codecs.CODEC.listOf(1, 10).fieldOf("layouts").forGetter(l -> l.layouts),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("gap", 0).forGetter(l -> l.gap),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("anchor_index", 0).forGetter(l -> l.anchorIndex),
            Codec.INT.optionalFieldOf("x_offset", 0).forGetter(l -> l.xOffset)
    ).apply(instance, AnchoredMultiColumnLayout::new));

    private final List<UiLayout> layouts;
    private final int gap;
    private final int anchorIndex;
    private final int xOffset;
    private final List<Integer> columnStarts;
    private final int anchorShift;
    private final int width;
    private final int height;

    public AnchoredMultiColumnLayout(List<UiLayout> layouts, int gap, int anchorIndex, int xOffset) {
        this.layouts = layouts;
        this.gap = gap;
        this.anchorIndex = Mth.clamp(anchorIndex, 0, layouts.size() - 1);
        this.xOffset = xOffset;
        this.columnStarts = new ArrayList<>(layouts.size());

        int runningX = 0;
        int maxHeight = 0;
        for (UiLayout layout : layouts) {
            this.columnStarts.add(runningX);
            runningX += layout.getWidth() + this.gap;
            maxHeight = Math.max(maxHeight, layout.getHeight());
        }

        this.width = Math.max(1, runningX - this.gap);
        this.height = maxHeight;

        int anchorStart = this.columnStarts.get(this.anchorIndex);
        int anchorCenter = anchorStart + (layouts.get(this.anchorIndex).getWidth() / 2);
        int layoutCenter = this.width / 2;
        this.anchorShift = layoutCenter - anchorCenter;
    }

    @Override
    public UiLayoutSerializer<?> getSerializer() {
        return YhaUiLayoutSerializers.ANCHORED_MULTI_COLUMN;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public void renderBackground(GuiGraphicsExtractor guiGraphics, int x, int y) {
        for (int i = 0; i < this.layouts.size(); i++) {
            this.layouts.get(i).renderBackground(guiGraphics, x + this.columnStarts.get(i) + this.anchorShift + this.xOffset, y);
        }
    }

    @Override
    public void addComponents(UiScreen screen, int x, int y, BiConsumer<UiComponent, AbstractWidget> consumer) {
        for (int i = 0; i < this.layouts.size(); i++) {
            this.layouts.get(i).addComponents(screen, x + this.columnStarts.get(i) + this.anchorShift + this.xOffset, y, consumer);
        }
    }

    public static class Serializer extends UiLayoutSerializer<AnchoredMultiColumnLayout> {

        @Override
        public MapCodec<AnchoredMultiColumnLayout> codec() {
            return CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiLayout, AnchoredMultiColumnLayout> builder, HolderLookup.Provider provider) {
            builder.setName("Anchored Multi Column Layout")
                    .setDescription("Places layouts to both sides of an anchor point so an anchor column can remain fixed.")
                    .add("layouts", TYPE_UI_LAYOUTS, "List of UI layouts.")
                    .addOptional("gap", TYPE_NON_NEGATIVE_INT, "Gap between each layout.", 0)
                    .addOptional("anchor_index", TYPE_NON_NEGATIVE_INT, "Index of the anchor layout in the list.", 0)
                    .addOptional("x_offset", TYPE_INT, "Additional horizontal offset for the entire layout. Negative moves left.", 0);
        }
    }
}
