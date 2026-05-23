package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.client.dna_analyzer.ClientDNAAnalyzerState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.HolderLookup;
import net.threetag.palladium.client.gui.ui.UiAlignment;
import net.threetag.palladium.client.gui.ui.component.RenderableUiComponent;
import net.threetag.palladium.client.gui.ui.component.UiComponent;
import net.threetag.palladium.client.gui.ui.component.UiComponentProperties;
import net.threetag.palladium.client.gui.ui.component.UiComponentSerializer;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;

public class DnaAnalyzerInfoUiComponent extends RenderableUiComponent {
    public static final MapCodec<DnaAnalyzerInfoUiComponent> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
            Codec.INT.optionalFieldOf("label_color", 0xFF9FC9EE).forGetter(DnaAnalyzerInfoUiComponent::getLabelColor),
            Codec.INT.optionalFieldOf("value_color", 0xFFE6F2FF).forGetter(DnaAnalyzerInfoUiComponent::getValueColor),
            propertiesCodec(120, 128)
    ).apply(instance, DnaAnalyzerInfoUiComponent::new));

    private final int labelColor;
    private final int valueColor;

    public DnaAnalyzerInfoUiComponent(int labelColor, int valueColor, UiComponentProperties properties) {
        super(properties);
        this.labelColor = withOpaqueAlpha(labelColor);
        this.valueColor = withOpaqueAlpha(valueColor);
    }

    @Override
    public UiComponentSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.DNA_ANALYZER_INFO;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor gui, DataContext context, int x, int y, int width, int height, int mouseX, int mouseY, UiAlignment alignment) {
        ClientDNAAnalyzerState.ClientData state = ClientDNAAnalyzerState.getLatest();
        String sourceName = state == null ? "No Sample Loaded" : safeText(state.sourceName(), "Unknown Source");
        String sourceUuid = state == null ? "" : safeText(state.sourceUuid(), "");
        int genes = state == null ? 0 : countFilled(state.geneSlots());
        boolean processing = state != null && state.processing();
        boolean awaitingVialCollection = state != null && state.awaitingVialCollection();
        String analyzed;
        if (processing) {
            int progressPercent = state.processingTotalTicks() <= 0
                    ? 0
                    : (int) ((state.processingProgress() * 100.0F) / state.processingTotalTicks());
            analyzed = "Processing " + progressPercent + "%";
        } else if (awaitingVialCollection) {
            analyzed = "Complete";
        } else {
            analyzed = state != null && state.analyzed() ? "Ready" : "Pending";
        }

        int lineY = y + 2;
        gui.text(minecraft.font, "Specimen", x, lineY, this.labelColor, false);
        lineY += 12;
        gui.text(minecraft.font, trimToWidth(minecraft, sourceName, width), x, lineY, this.valueColor, false);
        lineY += 14;

        gui.text(minecraft.font, "UUID", x, lineY, this.labelColor, false);
        lineY += 12;
        gui.text(minecraft.font, trimToWidth(minecraft, trimMiddle(sourceUuid, 18), width), x, lineY, 0xFFB4C8DF, false);
        lineY += 14;

        gui.text(minecraft.font, "Gene Slots", x, lineY, this.labelColor, false);
        lineY += 12;
        gui.text(minecraft.font, genes + "/6", x, lineY, this.valueColor, false);
        lineY += 14;

        gui.text(minecraft.font, "Analyzer", x, lineY, this.labelColor, false);
        lineY += 12;
        gui.text(minecraft.font, analyzed, x, lineY, this.valueColor, false);
    }

    private static int countFilled(String[] slots) {
        int count = 0;
        if (slots == null) {
            return 0;
        }
        for (String slot : slots) {
            if (slot != null && !slot.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static String trimMiddle(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value == null ? "" : value;
        }
        int left = (maxLen - 3) / 2;
        int right = maxLen - 3 - left;
        return value.substring(0, left) + "..." + value.substring(value.length() - right);
    }

    private static String trimToWidth(Minecraft minecraft, String value, int maxWidth) {
        if (minecraft.font.width(value) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        int suffixWidth = minecraft.font.width(suffix);
        int target = Math.max(0, maxWidth - suffixWidth);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char next = value.charAt(i);
            if (minecraft.font.width(builder.toString() + next) > target) {
                break;
            }
            builder.append(next);
        }
        return builder + suffix;
    }

    private static int withOpaqueAlpha(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    public int getLabelColor() {
        return this.labelColor;
    }

    public int getValueColor() {
        return this.valueColor;
    }

    public static class Serializer extends UiComponentSerializer<DnaAnalyzerInfoUiComponent> {
        @Override
        public MapCodec<DnaAnalyzerInfoUiComponent> codec() {
            return DnaAnalyzerInfoUiComponent.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiComponent, DnaAnalyzerInfoUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("DNA Analyzer Info")
                    .setDescription("Renders specimen details from the active DNA analyzer state.");
        }
    }
}
