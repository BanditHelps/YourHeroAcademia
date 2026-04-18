package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.client.stamina.ClientStaminaState;
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

import java.util.List;

public class StaminaBarUiComponent extends RenderableUiComponent {
    private static final int DEFAULT_BAR_WIDTH = 84;
    private static final int DEFAULT_BAR_HEIGHT = 10;
    private static final int BAR_BACKGROUND_COLOR = 0xCC101018;
    private static final int BAR_FRAME_COLOR = 0xFF304050;
    private static final int BAR_FILL_COLOR = 0xFF2ECC71;

    public static final MapCodec<StaminaBarUiComponent> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
            Codec.INT.optionalFieldOf("width", DEFAULT_BAR_WIDTH).forGetter(StaminaBarUiComponent::getBarWidth),
            Codec.INT.optionalFieldOf("height", DEFAULT_BAR_HEIGHT).forGetter(StaminaBarUiComponent::getBarHeight),
            Codec.BOOL.optionalFieldOf("show_label", true).forGetter(StaminaBarUiComponent::isShowLabel),
            propertiesCodec(DEFAULT_BAR_WIDTH, DEFAULT_BAR_HEIGHT)
    ).apply(instance, StaminaBarUiComponent::new));

    private final int barWidth;
    private final int barHeight;
    private final boolean showLabel;

    public StaminaBarUiComponent(int barWidth, int barHeight, boolean showLabel, UiComponentProperties properties) {
        super(properties);
        this.barWidth = Math.max(3, barWidth);
        this.barHeight = Math.max(3, barHeight);
        this.showLabel = showLabel;
    }

    @Override
    public UiComponentSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.STAMINA_BAR;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor gui, DataContext context, int x, int y, int width, int height, int mouseX, int mouseY, UiAlignment alignment) {
        if (minecraft.player == null) {
            return;
        }

        int current = ClientStaminaState.getCurrentStamina();
        int max = Math.max(1, ClientStaminaState.getMaxStamina());
        float ratio = Mth.clamp((float) current / (float) max, 0.0F, 1.0F);
        int innerWidth = this.barWidth - 2;
        int fillWidth = Math.round(innerWidth * ratio);

        drawBarFrame(gui, x, y, this.barWidth, this.barHeight);
        if (fillWidth > 0) {
            gui.fill(x + 1, y + 1, x + 1 + fillWidth, y + this.barHeight - 1, BAR_FILL_COLOR);
        }

        if (this.showLabel) {
            gui.text(minecraft.font, "Stamina", x + 1, y - minecraft.font.lineHeight - 1, 0xFFFFFFFF, true);
        }

        if (isHovered(mouseX, mouseY, x, y, this.barWidth, this.barHeight)) {
            List<FormattedCharSequence> tooltipLines = List.of(
                    Component.literal("Stamina: " + current + "/" + max).getVisualOrderText(),
                    Component.literal("Current: " + current).getVisualOrderText(),
                    Component.literal("Max: " + max).getVisualOrderText()
            );
            gui.setTooltipForNextFrame(minecraft.font, tooltipLines, mouseX, mouseY);
        }
    }

    public int getBarWidth() {
        return barWidth;
    }

    public int getBarHeight() {
        return barHeight;
    }

    public boolean isShowLabel() {
        return showLabel;
    }

    private static boolean isHovered(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void drawBarFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, BAR_BACKGROUND_COLOR);
        graphics.fill(x, y, x + width, y + 1, BAR_FRAME_COLOR);
        graphics.fill(x, y + height - 1, x + width, y + height, BAR_FRAME_COLOR);
        graphics.fill(x, y, x + 1, y + height, BAR_FRAME_COLOR);
        graphics.fill(x + width - 1, y, x + width, y + height, BAR_FRAME_COLOR);
    }

    public static class Serializer extends UiComponentSerializer<StaminaBarUiComponent> {
        @Override
        public MapCodec<StaminaBarUiComponent> codec() {
            return StaminaBarUiComponent.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiComponent, StaminaBarUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("Stamina Bar")
                    .setDescription("Renders a stamina bar with hover tooltip for current and max values.")
                    .add("width", TYPE_INT, "Optional width override for the bar.")
                    .add("height", TYPE_INT, "Optional height override for the bar.")
                    .add("show_label", TYPE_BOOLEAN, "Whether to show the stamina label above the bar.");
        }
    }
}
