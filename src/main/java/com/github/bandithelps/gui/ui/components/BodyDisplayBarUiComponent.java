package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.capabilities.body.BodyDisplayBar;
import com.github.bandithelps.capabilities.body.BodyDisplayBarType;
import com.github.bandithelps.client.body.ClientBodyState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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

public class BodyDisplayBarUiComponent extends RenderableUiComponent {
    private static final int DEFAULT_BAR_WIDTH = 72;
    private static final int DEFAULT_BAR_HEIGHT = 6;
    private static final int DEFAULT_ICON_SIZE = 6;
    private static final int DEFAULT_ICON_GAP = 0;
    private static final String BODY_ICON_TEXTURE_FOLDER = "textures/gui/body_bars/";

    public static final MapCodec<BodyDisplayBarUiComponent> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(BodyDisplayBarUiComponent::getDisplayBarId),
            Codec.INT.optionalFieldOf("width", DEFAULT_BAR_WIDTH).forGetter(BodyDisplayBarUiComponent::getBarWidth),
            Codec.INT.optionalFieldOf("height", DEFAULT_BAR_HEIGHT).forGetter(BodyDisplayBarUiComponent::getBarHeight),
            Codec.BOOL.optionalFieldOf("show_icon", true).forGetter(BodyDisplayBarUiComponent::isShowIcon),
            Codec.BOOL.optionalFieldOf("show_label", false).forGetter(BodyDisplayBarUiComponent::isShowLabel),
            Codec.INT.optionalFieldOf("value_decimals", 1).forGetter(BodyDisplayBarUiComponent::getValueDecimals),
            Codec.STRING.optionalFieldOf("description", "").forGetter(BodyDisplayBarUiComponent::getDescription),
            propertiesCodec(DEFAULT_BAR_WIDTH, DEFAULT_BAR_HEIGHT)
    ).apply(instance, BodyDisplayBarUiComponent::new));

    private final String displayBarId;
    private final int barWidth;
    private final int barHeight;
    private final boolean showIcon;
    private final boolean showLabel;
    private final int valueDecimals;
    private final String description;

    public BodyDisplayBarUiComponent(
            String displayBarId,
            int barWidth,
            int barHeight,
            boolean showIcon,
            boolean showLabel,
            int valueDecimals,
            String description,
            UiComponentProperties properties
    ) {
        super(properties);
        this.displayBarId = displayBarId;
        this.barWidth = Math.max(3, barWidth);
        this.barHeight = Math.max(3, barHeight);
        this.showIcon = showIcon;
        this.showLabel = showLabel;
        this.valueDecimals = Math.max(0, Math.min(3, valueDecimals));
        this.description = description == null ? "" : description.trim();
    }

    @Override
    public UiComponentSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.BODY_DISPLAY_BAR;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor gui, DataContext context, int x, int y, int width, int height, int mouseX, int mouseY, UiAlignment alignment) {
        BodyDisplayBar displayBar = ClientBodyState.getDisplayBars().get(this.displayBarId);
        if (displayBar == null) {
            return;
        }

        float currentValue = ClientBodyState.getCustomFloat(displayBar.part(), displayBar.key(), displayBar.minValue());
        float ratio = getRatio(displayBar, currentValue);
        int renderWidth = Math.max(3, this.barWidth);
        int renderHeight = Math.max(3, this.barHeight);

        drawBarFrame(gui, x, y, renderWidth, renderHeight);
        if (displayBar.type() == BodyDisplayBarType.SLIDER) {
            drawSlider(gui, x, y, renderWidth, renderHeight, ratio, displayBar);
        } else {
            drawFillBar(gui, x, y, renderWidth, renderHeight, ratio, displayBar);
        }

        if (this.showIcon) {
            renderBarIconIfPresent(gui, minecraft, displayBar.id(), x, y, renderHeight);
        }

        if (this.showLabel) {
            int titleY = y - minecraft.font.lineHeight - 1;
            gui.text(minecraft.font, displayBar.label(), x + 1, titleY, 0xFFFFFFFF, true);
        }

        if (isHovered(mouseX, mouseY, x, y, renderWidth, renderHeight)) {
            List<FormattedCharSequence> tooltipLines = buildTooltipLines(displayBar, currentValue, ratio).stream()
                    .flatMap(line -> minecraft.font.split(line, 240).stream())
                    .toList();
            gui.setTooltipForNextFrame(
                    minecraft.font,
                    tooltipLines,
                    mouseX,
                    mouseY
            );
        }
    }

    public String getDisplayBarId() {
        return displayBarId;
    }

    public int getBarWidth() {
        return barWidth;
    }

    public int getBarHeight() {
        return barHeight;
    }

    public boolean isShowIcon() {
        return showIcon;
    }

    public boolean isShowLabel() {
        return showLabel;
    }

    public int getValueDecimals() {
        return valueDecimals;
    }

    public String getDescription() {
        return description;
    }

    private List<Component> buildTooltipLines(BodyDisplayBar displayBar, float currentValue, float ratio) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(displayBar.label() + " (" + displayBar.id() + ")"));
        if (!this.description.isBlank()) {
            for (String line : normalizeLineBreaks(this.description).split("\n")) {
                if (!line.isBlank()) {
                    lines.add(Component.literal(line));
                }
            }
        }
        lines.add(Component.literal("Current: " + formatValue(currentValue, this.valueDecimals)));
        lines.add(Component.literal("Min: " + formatValue(displayBar.minValue(), this.valueDecimals)));
        lines.add(Component.literal("Max: " + formatValue(displayBar.maxValue(), this.valueDecimals)));
        lines.add(Component.literal("Percent: " + formatPercent(ratio)));
        return lines;
    }

    private static boolean isHovered(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static String normalizeLineBreaks(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\\n", "\n");
    }

    private static float getRatio(BodyDisplayBar displayBar, float value) {
        float range = Math.max(0.0001F, displayBar.maxValue() - displayBar.minValue());
        return Mth.clamp((value - displayBar.minValue()) / range, 0.0F, 1.0F);
    }

    private static void drawFillBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float ratio, BodyDisplayBar displayBar) {
        int innerWidth = width - 2;
        int fillWidth = Math.round(innerWidth * ratio);
        if (fillWidth <= 0) {
            return;
        }
        drawHorizontalGradient(
                graphics,
                x + 1,
                y + 1,
                fillWidth,
                height - 2,
                displayBar.gradientLeftColorRgb(),
                displayBar.gradientRightColorRgb()
        );
    }

    private static void drawSlider(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float ratio, BodyDisplayBar displayBar) {
        int innerWidth = width - 2;
        int innerX = x + 1;

        int gradientTop = y + 1;
        int gradientBottom = y + (height > 2 ? height - 1 : height);
        if (gradientBottom > gradientTop) {
            drawHorizontalGradient(
                    graphics,
                    innerX,
                    gradientTop,
                    innerWidth,
                    gradientBottom - gradientTop,
                    displayBar.gradientLeftColorRgb(),
                    displayBar.gradientRightColorRgb()
            );
        }

        int markerHalfWidth = 1;
        int markerCenter = innerX + Math.round(innerWidth * ratio);
        int markerColor = 0xFF000000 | displayBar.sliderColorRgb();
        graphics.fill(
                markerCenter - markerHalfWidth,
                y + 1,
                markerCenter + markerHalfWidth + 1,
                y + height - 1,
                markerColor
        );
    }

    private static void drawHorizontalGradient(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int leftRgb,
            int rightRgb
    ) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (width == 1) {
            graphics.fill(x, y, x + 1, y + height, 0xFF000000 | leftRgb);
            return;
        }

        for (int i = 0; i < width; i++) {
            float t = (float) i / (float) (width - 1);
            int gradientColor = 0xFF000000 | lerpRgb(leftRgb, rightRgb, t);
            graphics.fill(x + i, y, x + i + 1, y + height, gradientColor);
        }
    }

    private static int lerpRgb(int leftRgb, int rightRgb, float t) {
        int lr = (leftRgb >> 16) & 0xFF;
        int lg = (leftRgb >> 8) & 0xFF;
        int lb = leftRgb & 0xFF;
        int rr = (rightRgb >> 16) & 0xFF;
        int rg = (rightRgb >> 8) & 0xFF;
        int rb = rightRgb & 0xFF;

        int r = Mth.floor(Mth.lerp(t, lr, rr));
        int g = Mth.floor(Mth.lerp(t, lg, rg));
        int b = Mth.floor(Mth.lerp(t, lb, rb));
        return (r << 16) | (g << 8) | b;
    }

    private static void drawBarFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xCC101018);
        graphics.fill(x, y, x + width, y + 1, 0xFF304050);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF304050);
        graphics.fill(x, y, x + 1, y + height, 0xFF304050);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF304050);
    }

    private static void renderBarIconIfPresent(GuiGraphicsExtractor graphics, Minecraft minecraft, String displayBarId, int barX, int barY, int barHeight) {
        Identifier iconTexture = getIconTexture(displayBarId);
        if (minecraft.getResourceManager().getResource(iconTexture).isEmpty()) {
            return;
        }

        int iconX = barX - DEFAULT_ICON_SIZE - DEFAULT_ICON_GAP;
        int iconY = barY + (barHeight - DEFAULT_ICON_SIZE) / 2;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                iconTexture,
                iconX,
                iconY,
                0.0F,
                0.0F,
                DEFAULT_ICON_SIZE,
                DEFAULT_ICON_SIZE,
                DEFAULT_ICON_SIZE,
                DEFAULT_ICON_SIZE
        );
    }

    private static Identifier getIconTexture(String displayBarId) {
        return Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, BODY_ICON_TEXTURE_FOLDER + displayBarId + ".png");
    }

    private static String formatValue(float value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static String formatPercent(float ratio) {
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0F);
    }

    public static class Serializer extends UiComponentSerializer<BodyDisplayBarUiComponent> {
        @Override
        public MapCodec<BodyDisplayBarUiComponent> codec() {
            return BodyDisplayBarUiComponent.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiComponent, BodyDisplayBarUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("Body Display Bar")
                    .setDescription("Renders a registered body display bar by id.")
                    .add("id", TYPE_STRING, "The id of the body display bar to render.")
                    .add("width", TYPE_INT, "Optional width override for the bar.")
                    .add("height", TYPE_INT, "Optional height override for the bar.")
                    .add("show_icon", TYPE_BOOLEAN, "Whether the body bar icon should be rendered.")
                    .add("show_label", TYPE_BOOLEAN, "Whether the bar title should be rendered above the bar.")
                    .add("value_decimals", TYPE_INT, "Decimal places used in tooltip numeric values.")
                    .add("description", TYPE_STRING, "Optional text shown in the bar tooltip.");
        }
    }
}
