package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.client.body.ClientBodyState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.threetag.palladium.client.gui.ui.UiAlignment;
import net.threetag.palladium.client.gui.ui.component.RenderableUiComponent;
import net.threetag.palladium.client.gui.ui.component.UiComponent;
import net.threetag.palladium.client.gui.ui.component.UiComponentProperties;
import net.threetag.palladium.client.gui.ui.component.UiComponentSerializer;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VerticalSegmentBarUiComponent extends RenderableUiComponent {
    private static final int DEFAULT_BAR_WIDTH = 16;
    private static final int DEFAULT_BAR_HEIGHT = 92;
    private static final int DEFAULT_SEGMENT_GAP = 1;
    private static final int DEFAULT_SEGMENT_MAX = 5;
    private static final int BACKGROUND_COLOR = 0xCC101018;
    private static final int FRAME_COLOR = 0xFF304050;

    public static final MapCodec<VerticalSegmentBarUiComponent> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
            Codec.STRING.fieldOf("label").forGetter(VerticalSegmentBarUiComponent::getLabel),
            BodyPart.CODEC.optionalFieldOf("part", BodyPart.CHEST).forGetter(VerticalSegmentBarUiComponent::getPart),
            Codec.STRING.fieldOf("current_key").forGetter(VerticalSegmentBarUiComponent::getCurrentKey),
            Codec.STRING.fieldOf("max_key").forGetter(VerticalSegmentBarUiComponent::getMaxKey),
            Codec.INT.optionalFieldOf("width", DEFAULT_BAR_WIDTH).forGetter(VerticalSegmentBarUiComponent::getBarWidth),
            Codec.INT.optionalFieldOf("height", DEFAULT_BAR_HEIGHT).forGetter(VerticalSegmentBarUiComponent::getBarHeight),
            Codec.INT.optionalFieldOf("segment_gap", DEFAULT_SEGMENT_GAP).forGetter(VerticalSegmentBarUiComponent::getSegmentGap),
            Codec.INT.optionalFieldOf("max_segments", 64).forGetter(VerticalSegmentBarUiComponent::getMaxSegments),
            Codec.FLOAT.optionalFieldOf("default_current", 0.0F).forGetter(VerticalSegmentBarUiComponent::getDefaultCurrent),
            Codec.FLOAT.optionalFieldOf("default_max", (float) DEFAULT_SEGMENT_MAX).forGetter(VerticalSegmentBarUiComponent::getDefaultMax),
            Codec.BOOL.optionalFieldOf("show_label", true).forGetter(VerticalSegmentBarUiComponent::isShowLabel),
            Codec.BOOL.optionalFieldOf("show_value", true).forGetter(VerticalSegmentBarUiComponent::isShowValue),
            Codec.INT.optionalFieldOf("empty_color", 0xFF1F2933).forGetter(VerticalSegmentBarUiComponent::getEmptyColor),
            Codec.INT.optionalFieldOf("fill_color", 0xFF55FF55).forGetter(VerticalSegmentBarUiComponent::getFillColor),
            propertiesCodec(DEFAULT_BAR_WIDTH, DEFAULT_BAR_HEIGHT)
    ).apply(instance, VerticalSegmentBarUiComponent::new));

    private final String label;
    private final BodyPart part;
    private final String currentKey;
    private final String maxKey;
    private final int barWidth;
    private final int barHeight;
    private final int segmentGap;
    private final int maxSegments;
    private final float defaultCurrent;
    private final float defaultMax;
    private final boolean showLabel;
    private final boolean showValue;
    private final int emptyColor;
    private final int fillColor;

    public VerticalSegmentBarUiComponent(
            String label,
            BodyPart part,
            String currentKey,
            String maxKey,
            int barWidth,
            int barHeight,
            int segmentGap,
            int maxSegments,
            float defaultCurrent,
            float defaultMax,
            boolean showLabel,
            boolean showValue,
            int emptyColor,
            int fillColor,
            UiComponentProperties properties
    ) {
        super(properties);
        this.label = label == null ? "" : label.trim();
        this.part = part == null ? BodyPart.CHEST : part;
        this.currentKey = currentKey;
        this.maxKey = maxKey;
        this.barWidth = Math.max(3, barWidth);
        this.barHeight = Math.max(8, barHeight);
        this.segmentGap = Math.max(0, segmentGap);
        this.maxSegments = Math.max(1, maxSegments);
        this.defaultCurrent = defaultCurrent;
        this.defaultMax = Math.max(1.0F, defaultMax);
        this.showLabel = showLabel;
        this.showValue = showValue;
        this.emptyColor = withOpaqueAlpha(emptyColor);
        this.fillColor = withOpaqueAlpha(fillColor);
    }

    @Override
    public UiComponentSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.VERTICAL_SEGMENT_BAR;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor gui, DataContext context, int x, int y, int width, int height, int mouseX, int mouseY, UiAlignment alignment) {
        float rawMax = ClientBodyState.getCustomFloat(this.part, this.maxKey, this.defaultMax);
        float maxValue = Math.max(1.0F, rawMax);
        float rawCurrent = ClientBodyState.getCustomFloat(this.part, this.currentKey, this.defaultCurrent);
        float currentValue = Mth.clamp(rawCurrent, 0.0F, maxValue);
        int segmentCount = Mth.clamp(Mth.ceil(maxValue), 1, this.maxSegments);

        drawBarFrame(gui, x, y, this.barWidth, this.barHeight);
        drawSegments(gui, x, y, this.barWidth, this.barHeight, segmentCount, currentValue, maxValue);

        if (this.showLabel && !this.label.isBlank()) {
            int labelY = y - minecraft.font.lineHeight - 1;
            int labelX = x + (this.barWidth - minecraft.font.width(this.label)) / 2;
            gui.text(minecraft.font, this.label, labelX, labelY, 0xFFFFFFFF, true);
        }

        if (this.showValue) {
            String valueText = Mth.floor(currentValue) + "/" + Mth.floor(maxValue);
            int textWidth = minecraft.font.width(valueText);
            int textX = x + (this.barWidth - textWidth) / 2;
            gui.text(minecraft.font, valueText, textX, y + this.barHeight + 2, 0xFFB0B0B0, true);
        }

        if (isHovered(mouseX, mouseY, x, y, this.barWidth, this.barHeight)) {
            List<FormattedCharSequence> lines = buildTooltipLines(minecraft, currentValue, maxValue, segmentCount);
            gui.setTooltipForNextFrame(minecraft.font, lines, mouseX, mouseY);
        }
    }

    private void drawSegments(
            GuiGraphicsExtractor gui,
            int x,
            int y,
            int width,
            int height,
            int segmentCount,
            float currentValue,
            float maxValue
    ) {
        int innerX = x + 1;
        int innerY = y + 1;
        int innerWidth = Math.max(1, width - 2);
        int innerHeight = Math.max(1, height - 2);
        int totalGap = this.segmentGap * Math.max(0, segmentCount - 1);
        int drawableHeight = Math.max(1, innerHeight - totalGap);
        int baseSegmentHeight = Math.max(1, drawableHeight / segmentCount);
        int leftoverPixels = Math.max(0, drawableHeight - (baseSegmentHeight * segmentCount));

        float progress = maxValue <= 0.0F ? 0.0F : Mth.clamp(currentValue / maxValue, 0.0F, 1.0F);
        float filledSegments = progress * segmentCount;

        int cursorY = innerY + innerHeight;
        for (int segmentIndex = 0; segmentIndex < segmentCount; segmentIndex++) {
            int segmentHeight = baseSegmentHeight + (segmentIndex < leftoverPixels ? 1 : 0);
            int segmentBottom = cursorY;
            int segmentTop = segmentBottom - segmentHeight;
            float segmentFill = Mth.clamp(filledSegments - segmentIndex, 0.0F, 1.0F);

            gui.fill(innerX, segmentTop, innerX + innerWidth, segmentBottom, this.emptyColor);
            if (segmentFill > 0.0F) {
                int fillTop = segmentBottom - Math.max(1, Math.round(segmentHeight * segmentFill));
                gui.fill(innerX, fillTop, innerX + innerWidth, segmentBottom, this.fillColor);
            }

            cursorY = segmentTop - this.segmentGap;
        }
    }

    private static void drawBarFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, BACKGROUND_COLOR);
        graphics.fill(x, y, x + width, y + 1, FRAME_COLOR);
        graphics.fill(x, y + height - 1, x + width, y + height, FRAME_COLOR);
        graphics.fill(x, y, x + 1, y + height, FRAME_COLOR);
        graphics.fill(x + width - 1, y, x + width, y + height, FRAME_COLOR);
    }

    private static List<FormattedCharSequence> buildTooltipLines(Minecraft minecraft, float currentValue, float maxValue, int segments) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Current: " + formatValue(currentValue)));
        lines.add(Component.literal("Max: " + formatValue(maxValue)));
        lines.add(Component.literal("Segments: " + segments));
        return lines.stream().map(Component::getVisualOrderText).toList();
    }

    private static boolean isHovered(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int withOpaqueAlpha(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    private static String formatValue(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    public String getLabel() {
        return label;
    }

    public BodyPart getPart() {
        return part;
    }

    public String getCurrentKey() {
        return currentKey;
    }

    public String getMaxKey() {
        return maxKey;
    }

    public int getBarWidth() {
        return barWidth;
    }

    public int getBarHeight() {
        return barHeight;
    }

    public int getSegmentGap() {
        return segmentGap;
    }

    public int getMaxSegments() {
        return maxSegments;
    }

    public float getDefaultCurrent() {
        return defaultCurrent;
    }

    public float getDefaultMax() {
        return defaultMax;
    }

    public boolean isShowLabel() {
        return showLabel;
    }

    public boolean isShowValue() {
        return showValue;
    }

    public int getEmptyColor() {
        return emptyColor;
    }

    public int getFillColor() {
        return fillColor;
    }

    public static class Serializer extends UiComponentSerializer<VerticalSegmentBarUiComponent> {
        @Override
        public MapCodec<VerticalSegmentBarUiComponent> codec() {
            return VerticalSegmentBarUiComponent.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiComponent, VerticalSegmentBarUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("Vertical Segment Bar")
                    .setDescription("Renders a vertical segmented upgrade bar from body float current and max keys.")
                    .add("label", TYPE_STRING, "Label shown above the bar.")
                    .add("part", TYPE_STRING, "Body part key used for reading body float values.")
                    .add("current_key", TYPE_STRING, "Body float key used for current value.")
                    .add("max_key", TYPE_STRING, "Body float key used for max value.")
                    .add("width", TYPE_INT, "Optional width override.")
                    .add("height", TYPE_INT, "Optional height override.")
                    .add("segment_gap", TYPE_INT, "Pixels between each segment.")
                    .add("max_segments", TYPE_INT, "Safety clamp for maximum rendered segments.")
                    .add("default_current", TYPE_FLOAT, "Fallback current value if key is missing.")
                    .add("default_max", TYPE_FLOAT, "Fallback max value if key is missing.")
                    .add("show_label", TYPE_BOOLEAN, "Show label above bar.")
                    .add("show_value", TYPE_BOOLEAN, "Show current/max text below bar.")
                    .add("empty_color", TYPE_INT, "Unfilled segment color.")
                    .add("fill_color", TYPE_INT, "Filled segment color.");
        }
    }
}
