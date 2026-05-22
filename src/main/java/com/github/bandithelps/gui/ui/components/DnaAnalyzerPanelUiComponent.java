package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.client.dna_analyzer.ClientDNAAnalyzerState;
import com.github.bandithelps.gene.Gene;
import com.github.bandithelps.utils.gene.GeneUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.threetag.palladium.client.gui.ui.UiAlignment;
import net.threetag.palladium.client.gui.ui.component.RenderableUiComponent;
import net.threetag.palladium.client.gui.ui.component.UiComponent;
import net.threetag.palladium.client.gui.ui.component.UiComponentProperties;
import net.threetag.palladium.client.gui.ui.component.UiComponentSerializer;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;

import java.util.ArrayList;
import java.util.List;

public class DnaAnalyzerPanelUiComponent extends RenderableUiComponent {
    public static final MapCodec<DnaAnalyzerPanelUiComponent> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
            Codec.STRING.optionalFieldOf("title", "DNA ANALYZER").forGetter(DnaAnalyzerPanelUiComponent::getTitle),
            Codec.INT.optionalFieldOf("frame_color", 0xFF79B8FF).forGetter(DnaAnalyzerPanelUiComponent::getFrameColor),
            Codec.INT.optionalFieldOf("slot_color", 0xFF1A2532).forGetter(DnaAnalyzerPanelUiComponent::getSlotColor),
            Codec.INT.optionalFieldOf("slot_active_color", 0xFF9AD1FF).forGetter(DnaAnalyzerPanelUiComponent::getSlotActiveColor),
            Codec.INT.optionalFieldOf("text_color", 0xFFE6F2FF).forGetter(DnaAnalyzerPanelUiComponent::getTextColor),
            propertiesCodec(236, 176)
    ).apply(instance, DnaAnalyzerPanelUiComponent::new));

    private final String title;
    private final int frameColor;
    private final int slotColor;
    private final int slotActiveColor;
    private final int textColor;

    public DnaAnalyzerPanelUiComponent(
            String title,
            int frameColor,
            int slotColor,
            int slotActiveColor,
            int textColor,
            UiComponentProperties properties
    ) {
        super(properties);
        this.title = title == null || title.isBlank() ? "DNA ANALYZER" : title;
        this.frameColor = withOpaqueAlpha(frameColor);
        this.slotColor = withOpaqueAlpha(slotColor);
        this.slotActiveColor = withOpaqueAlpha(slotActiveColor);
        this.textColor = withOpaqueAlpha(textColor);
    }

    @Override
    public UiComponentSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.DNA_ANALYZER_PANEL;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor gui, DataContext context, int x, int y, int width, int height, int mouseX, int mouseY, UiAlignment alignment) {
        drawPanelBackground(gui, x, y, width, height);

        int titleX = x + (width - minecraft.font.width(this.title)) / 2;
        gui.text(minecraft.font, this.title, titleX, y + 6, this.textColor, true);

        int helixX = x + 14;
        int helixY = y + 24;
        int helixHeight = 96;
        drawHelix(gui, helixX, helixY, helixHeight);

        ClientDNAAnalyzerState.ClientData state = ClientDNAAnalyzerState.getLatest();
        String sourceName = state == null ? "No Sample Loaded" : safeText(state.sourceName(), "Unknown Source");
        String sourceUuid = state == null ? "" : safeText(state.sourceUuid(), "");
        String[] slots = state == null ? emptySlots() : normalizeSlots(state.geneSlots());

        int slotsX = x + 74;
        int slotsY = y + 24;
        drawGeneSlots(minecraft, gui, slots, slotsX, slotsY, mouseX, mouseY);

        int filled = 0;
        for (String slot : slots) {
            if (!slot.isBlank()) {
                filled++;
            }
        }

        gui.text(minecraft.font, "Source:", x + 14, y + 128, 0xFF98C8FF, false);
        gui.text(minecraft.font, sourceName, x + 56, y + 128, this.textColor, false);
        gui.text(minecraft.font, "UUID:", x + 14, y + 140, 0xFF98C8FF, false);
        gui.text(minecraft.font, trimMiddle(sourceUuid, 28), x + 56, y + 140, 0xFFB4C8DF, false);
        gui.text(minecraft.font, "Genes:", x + 14, y + 152, 0xFF98C8FF, false);
        gui.text(minecraft.font, String.valueOf(filled) + "/6", x + 56, y + 152, this.textColor, false);
    }

    private void drawPanelBackground(GuiGraphicsExtractor gui, int x, int y, int width, int height) {
        gui.fill(x, y, x + width, y + height, 0xCC0E131B);
        gui.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xAA172231);
        gui.fill(x, y, x + width, y + 1, this.frameColor);
        gui.fill(x, y + height - 1, x + width, y + height, this.frameColor);
        gui.fill(x, y, x + 1, y + height, this.frameColor);
        gui.fill(x + width - 1, y, x + width, y + height, this.frameColor);
    }

    private void drawHelix(GuiGraphicsExtractor gui, int x, int y, int height) {
        for (int i = 0; i < height; i += 2) {
            int wave = (int) Math.round(Math.sin((i + 4) * 0.26D) * 7.0D);
            int leftX = x + 8 + wave;
            int rightX = x + 30 - wave;
            gui.fill(leftX, y + i, leftX + 2, y + i + 2, 0xFF7CD2FF);
            gui.fill(rightX, y + i, rightX + 2, y + i + 2, 0xFF7CD2FF);
            if ((i / 2) % 2 == 0) {
                gui.fill(Math.min(leftX, rightX), y + i, Math.max(leftX, rightX) + 2, y + i + 1, 0xAA9EE4FF);
            }
        }
    }

    private void drawGeneSlots(Minecraft minecraft, GuiGraphicsExtractor gui, String[] slots, int startX, int startY, int mouseX, int mouseY) {
        for (int i = 0; i < 6; i++) {
            int slotX = startX + (i % 3) * 52;
            int slotY = startY + (i / 3) * 52;
            boolean filled = i < slots.length && !slots[i].isBlank();
            int border = filled ? this.slotActiveColor : 0xFF3A4A5E;

            gui.fill(slotX, slotY, slotX + 44, slotY + 44, this.slotColor);
            gui.fill(slotX, slotY, slotX + 44, slotY + 1, border);
            gui.fill(slotX, slotY + 43, slotX + 44, slotY + 44, border);
            gui.fill(slotX, slotY, slotX + 1, slotY + 44, border);
            gui.fill(slotX + 43, slotY, slotX + 44, slotY + 44, border);

            String text = filled ? "GENE" : "EMPTY";
            int textColor = filled ? 0xFFB3F0C4 : 0xFF7E8BA0;
            int textX = slotX + (44 - minecraft.font.width(text)) / 2;
            gui.text(minecraft.font, text, textX, slotY + 16, textColor, false);

            if (mouseX >= slotX && mouseX < slotX + 44 && mouseY >= slotY && mouseY < slotY + 44 && filled) {
                addGeneTooltip(gui, minecraft, slots[i], mouseX, mouseY, i + 1);
            }
        }
    }

    private void addGeneTooltip(GuiGraphicsExtractor gui, Minecraft minecraft, String rawGene, int mouseX, int mouseY, int slotNumber) {
        Gene gene = GeneUtil.parseGene(rawGene);
        if (gene == null) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Gene Slot " + slotNumber));
        lines.add(Component.literal("Name: " + gene.getName()));
        lines.add(Component.literal("Quality: " + gene.getQuality() + "/100"));
        lines.add(Component.literal("Category: " + gene.getCategory().name()));
        lines.add(Component.literal("Type: " + gene.getType().getId()));
        List<FormattedCharSequence> tooltipLines = lines.stream()
                .map(Component::getVisualOrderText)
                .toList();
        gui.setTooltipForNextFrame(minecraft.font, tooltipLines, mouseX, mouseY);
    }

    private static String[] normalizeSlots(String[] slots) {
        String[] normalized = emptySlots();
        for (int i = 0; i < normalized.length; i++) {
            if (slots != null && i < slots.length && slots[i] != null) {
                normalized[i] = slots[i];
            }
        }
        return normalized;
    }

    private static String[] emptySlots() {
        String[] slots = new String[6];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = "";
        }
        return slots;
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

    private static int withOpaqueAlpha(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    public String getTitle() {
        return this.title;
    }

    public int getFrameColor() {
        return this.frameColor;
    }

    public int getSlotColor() {
        return this.slotColor;
    }

    public int getSlotActiveColor() {
        return this.slotActiveColor;
    }

    public int getTextColor() {
        return this.textColor;
    }

    public static class Serializer extends UiComponentSerializer<DnaAnalyzerPanelUiComponent> {
        @Override
        public MapCodec<DnaAnalyzerPanelUiComponent> codec() {
            return DnaAnalyzerPanelUiComponent.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiComponent, DnaAnalyzerPanelUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("DNA Analyzer Panel")
                    .setDescription("Renders a modern DNA panel with a helix, six gene slots, and slot tooltips.");
        }
    }
}
