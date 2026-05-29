package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.attributes.IntelligenceAttributes;
import com.github.bandithelps.client.dna_analyzer.ClientDNAAnalyzerState;
import com.github.bandithelps.client.dna_analyzer.ClientDNAAnalyzerToolState;
import com.github.bandithelps.gui.screens.DnaAnalyzerRenamePopupScreen;
import com.github.bandithelps.gene.Gene;
import com.github.bandithelps.network.DNAAnalyzerExtractPayload;
import com.github.bandithelps.utils.gene.GeneUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.threetag.palladium.client.gui.ui.component.UiComponent;
import net.threetag.palladium.client.gui.ui.component.UiComponentProperties;
import net.threetag.palladium.client.gui.ui.component.UiComponentSerializer;
import net.threetag.palladium.client.gui.ui.screen.UiScreen;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DnaAnalyzerPanelUiComponent extends UiComponent {
    private static final int SLOT_WIDTH = 72;
    private static final int SLOT_HEIGHT = 24;
    private static final int SLOT_ROW_GAP = 10;
    private static final int HELIX_DRAW_WIDTH = 40;
    private static final int HELIX_HALF_WIDTH = HELIX_DRAW_WIDTH / 2;
    private static final int HELIX_TO_SLOT_GAP = 10;
    private static final int EXTRACT_COUNT_LOW_INTELLIGENCE = 3;
    private static final int EXTRACT_COUNT_MID_INTELLIGENCE = 2;
    private static final int EXTRACT_COUNT_HIGH_INTELLIGENCE = 1;
    private static final double MID_INTELLIGENCE_THRESHOLD = 25.0D;
    private static final double HIGH_INTELLIGENCE_THRESHOLD = 60.0D;

    public static final MapCodec<DnaAnalyzerPanelUiComponent> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
            Codec.STRING.optionalFieldOf("title", "").forGetter(DnaAnalyzerPanelUiComponent::getTitle),
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
    private int selectedSlot = -1;
    private int descriptionScrollOffset = 0;
    private int[] selectedIsolationSlots = new int[0];
    private int selectedExtractionCount = EXTRACT_COUNT_LOW_INTELLIGENCE;

    public DnaAnalyzerPanelUiComponent(
            String title,
            int frameColor,
            int slotColor,
            int slotActiveColor,
            int textColor,
            UiComponentProperties properties
    ) {
        super(properties);
        this.title = title == null || title.isBlank() ? "" : title;
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
    public AbstractWidget buildWidget(UiScreen screen, ScreenRectangle rectangle) {
        return new DnaAnalyzerPanelWidget(this, this.getX(rectangle), this.getY(rectangle), this.getWidth(), this.getHeight());
    }

    private static void drawPanelBackground(GuiGraphicsExtractor gui, int x, int y, int width, int height, int frameColor) {
        gui.fill(x, y, x + width, y + height, 0xCC0E131B);
        gui.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xAA172231);
        gui.fill(x, y, x + width, y + 1, frameColor);
        gui.fill(x, y + height - 1, x + width, y + height, frameColor);
        gui.fill(x, y, x + 1, y + height, frameColor);
        gui.fill(x + width - 1, y, x + width, y + height, frameColor);
    }

    private static void drawHelix(
            GuiGraphicsExtractor gui,
            int x,
            int y,
            int height,
            float animationTime,
            boolean activeMode,
            String sampleUuid,
            float processingProgress
    ) {
        float speed = activeMode ? 0.16F : 0.03F;
        float phase = animationTime * speed;
        float frequency = activeMode ? 0.20F : 0.13F;
        int uuidSeed = sampleUuid == null ? 0 : sampleUuid.hashCode();
        float uuidPhaseOffset = activeMode ? (((uuidSeed >>> 8) & 0xFF) / 255.0F) * 6.2831855F : 0.0F;
        float uuidFrequencyScale = activeMode ? (0.85F + ((((uuidSeed >>> 16) & 0xFF) / 255.0F) * 0.40F)) : 1.0F;
        float uuidAmplitudeScale = activeMode ? (0.75F + (((uuidSeed & 0xFF) / 255.0F) * 0.90F)) : 1.0F;
        int centerX = x + (HELIX_DRAW_WIDTH / 2);
        for (int i = 0; i < height; i += 2) {
            float angle = (i * frequency * uuidFrequencyScale) + phase + uuidPhaseOffset;
            float spin = (float) Math.sin(angle);
            float depth = (float) Math.cos(angle);
            float baseSpread = activeMode ? 5.0F : 7.0F;
            float animatedSpread = activeMode
                    ? (13.0F * Math.abs(spin) * uuidAmplitudeScale)
                    : (5.0F * (0.5F + 0.5F * Math.abs(spin)));
            float uuidJitter = activeMode ? uuidSpreadJitter(uuidSeed, i) : 0.0F;
            int spread = Math.max(3, Math.round(baseSpread + animatedSpread + uuidJitter));

            int leftX = centerX - spread;
            int rightX = centerX + spread;
            int defaultFrontColor = depth >= 0 ? 0xFF8CE0FF : 0xFF5EA5C8;
            int defaultBackColor = depth >= 0 ? 0xFF5EA5C8 : 0xFF8CE0FF;
            int progressedFrontColor = depth >= 0 ? 0xFF8EF5A8 : 0xFF5BBE7B;
            int progressedBackColor = depth >= 0 ? 0xFF5BBE7B : 0xFF8EF5A8;
            float completedHeight = processingProgress * height;
            float distanceFromBottom = height - i;
            float segmentProgress = clamp01((completedHeight - distanceFromBottom + 2.0F) / 4.0F);
            int frontColor = lerpColor(defaultFrontColor, progressedFrontColor, segmentProgress);
            int backColor = lerpColor(defaultBackColor, progressedBackColor, segmentProgress);

            gui.fill(leftX, y + i, leftX + 2, y + i + 2, backColor);
            gui.fill(rightX, y + i, rightX + 2, y + i + 2, frontColor);
            if ((i / 2) % 2 == 0) {
                int defaultRungColor = depth >= 0 ? 0xAA9EE4FF : 0x665C8AA8;
                int progressedRungColor = depth >= 0 ? 0xAA9EF2B5 : 0x6666B580;
                int rungColor = lerpColor(defaultRungColor, progressedRungColor, segmentProgress);
                gui.fill(Math.min(leftX, rightX), y + i, Math.max(leftX, rightX) + 2, y + i + 1, rungColor);
            }
        }
    }

    private static float uuidSpreadJitter(int seed, int row) {
        int value = seed ^ (row * 0x9E3779B9);
        value ^= (value >>> 16);
        value *= 0x7FEB352D;
        value ^= (value >>> 15);
        value *= 0x846CA68B;
        value ^= (value >>> 16);
        float normalized = ((value & 0xFF) / 255.0F) - 0.5F;
        return normalized * 4.0F;
    }

    private static int drawGeneSlots(
            DnaAnalyzerPanelUiComponent owner,
            Minecraft minecraft,
            GuiGraphicsExtractor gui,
            String[] slots,
            Gene[] genes,
            int leftX,
            int rightX,
            int startY,
            int mouseX,
            int mouseY
    ) {
        int hoveredSlot = -1;
        for (int i = 0; i < 6; i++) {
            int row = i % 3;
            int slotX = i < 3 ? leftX : rightX;
            int slotY = startY + row * (SLOT_HEIGHT + SLOT_ROW_GAP);
            boolean filled = i < slots.length && !slots[i].isBlank();
            boolean selected = owner.selectedSlot == i;
            int border = selected ? 0xFFFFC857 : filled ? owner.slotActiveColor : 0xFF3A4A5E;

            gui.fill(slotX, slotY, slotX + SLOT_WIDTH, slotY + SLOT_HEIGHT, owner.slotColor);
            gui.fill(slotX, slotY, slotX + SLOT_WIDTH, slotY + 1, border);
            gui.fill(slotX, slotY + SLOT_HEIGHT - 1, slotX + SLOT_WIDTH, slotY + SLOT_HEIGHT, border);
            gui.fill(slotX, slotY, slotX + 1, slotY + SLOT_HEIGHT, border);
            gui.fill(slotX + SLOT_WIDTH - 1, slotY, slotX + SLOT_WIDTH, slotY + SLOT_HEIGHT, border);

            String text = filled ? trimToWidth(minecraft, safeText(genes[i] == null ? "" : genes[i].getName(), "Unknown"), SLOT_WIDTH - 8) : "Empty";
            int textColor = filled ? 0xFFB3F0C4 : 0xFF7E8BA0;
            int textX = slotX + (SLOT_WIDTH - minecraft.font.width(text)) / 2;
            gui.text(minecraft.font, text, textX, slotY + 8, textColor, false);

            if (mouseX >= slotX && mouseX < slotX + SLOT_WIDTH && mouseY >= slotY && mouseY < slotY + SLOT_HEIGHT) {
                hoveredSlot = i;
            }

            if (hoveredSlot == i && filled) {
                addGeneTooltip(gui, minecraft, slots[i], mouseX, mouseY, i + 1);
            }
        }
        return hoveredSlot;
    }

    private static void drawSelectedGeneInfo(DnaAnalyzerPanelUiComponent owner, Minecraft minecraft, GuiGraphicsExtractor gui, Gene[] genes, int x, int y, int width, int bottomY) {
        gui.fill(x, y, x + width, bottomY, 0x88101824);
        gui.fill(x, y, x + width, y + 1, 0xAA476485);
        gui.fill(x, bottomY - 1, x + width, bottomY, 0xAA476485);
        gui.fill(x, y, x + 1, bottomY, 0xAA476485);
        gui.fill(x + width - 1, y, x + width, bottomY, 0xAA476485);

        if (owner.selectedSlot < 0 || owner.selectedSlot >= genes.length || genes[owner.selectedSlot] == null) {
            gui.text(minecraft.font, "No gene selected", x + 6, y + 4, 0xFF98C8FF, false);
            gui.text(minecraft.font, "Click a gene slot to inspect details.", x + 6, y + 16, 0xFF9AA9BC, false);
            return;
        }

        Gene gene = genes[owner.selectedSlot];
        gui.text(minecraft.font, trimToWidth(minecraft, gene.getName(), width - 12), x + 6, y + 4, owner.textColor, false);
        String qualityLine = "Q " + gene.getQuality() + "/100";
        String categoryLine = "Cat " + gene.getCategory().name();
        gui.text(minecraft.font, qualityLine + "  " + categoryLine, x + 6, y + 16, 0xFFB7D9FF, false);

        int descX = x + 5;
        int descY = y + 28;
        int descW = width - 10;
        int descH = Math.max(16, bottomY - descY - 4);
        drawDescriptionBox(owner, minecraft, gui, gene, descX, descY, descW, descH);
    }

    private static void drawDescriptionBox(DnaAnalyzerPanelUiComponent owner, Minecraft minecraft, GuiGraphicsExtractor gui, Gene gene, int x, int y, int width, int height) {
        gui.fill(x, y, x + width, y + height, 0xAA0B1220);
        gui.fill(x, y, x + width, y + 1, 0xAA3E5F84);
        gui.fill(x, y + height - 1, x + width, y + height, 0xAA3E5F84);
        gui.fill(x, y, x + 1, y + height, 0xAA3E5F84);
        gui.fill(x + width - 1, y, x + width, y + height, 0xAA3E5F84);

        String description = resolveDescriptionText(gene);
        List<String> wrapped = wrapText(minecraft, description, width - 10);
        int visibleLines = Math.max(1, (height - 6) / 9);
        int maxScroll = Math.max(0, wrapped.size() - visibleLines);
        owner.descriptionScrollOffset = clamp(owner.descriptionScrollOffset, 0, maxScroll);

        int start = owner.descriptionScrollOffset;
        int end = Math.min(wrapped.size(), start + visibleLines);
        int lineY = y + 3;
        for (int i = start; i < end; i++) {
            gui.text(minecraft.font, wrapped.get(i), x + 4, lineY, 0xFFBFD7EE, false);
            lineY += 9;
        }

        if (maxScroll > 0) {
            int barX = x + width - 3;
            gui.fill(barX, y + 2, barX + 1, y + height - 2, 0xFF1E334A);
            int thumbHeight = Math.max(8, (height - 6) * visibleLines / wrapped.size());
            int trackHeight = (height - 4) - thumbHeight;
            int thumbY = y + 2 + (trackHeight <= 0 ? 0 : (trackHeight * owner.descriptionScrollOffset / maxScroll));
            gui.fill(barX, thumbY, barX + 1, thumbY + thumbHeight, 0xFF88B7E3);
        }
    }

    private static void addGeneTooltip(GuiGraphicsExtractor gui, Minecraft minecraft, String rawGene, int mouseX, int mouseY, int slotNumber) {
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

    private static Gene[] parseGenes(String[] slots) {
        Gene[] genes = new Gene[6];
        for (int i = 0; i < genes.length; i++) {
            if (i < slots.length && !slots[i].isBlank()) {
                genes[i] = GeneUtil.parseGene(slots[i]);
            }
        }
        return genes;
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

    private static String resolveDescriptionText(Gene gene) {
        String raw = gene.getDescription();
        if (raw == null || raw.isBlank()) {
            return "No description available.";
        }
        if (isLikelyTranslationKey(raw)) {
            return Component.translatable(raw).getString();
        }
        return raw;
    }

    private static boolean isLikelyTranslationKey(String value) {
        if (value == null || value.isBlank() || value.contains(" ")) {
            return false;
        }
        return value.contains(".");
    }

    private static List<String> wrapText(Minecraft minecraft, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            lines.add("");
            return lines;
        }
        String[] words = text.replace('\n', ' ').trim().split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (minecraft.font.width(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                if (minecraft.font.width(word) <= maxWidth) {
                    current.append(word);
                } else {
                    lines.add(trimToWidth(minecraft, word, maxWidth));
                }
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int lerpColor(int from, int to, float progress) {
        float t = clamp01(progress);
        int a1 = (from >>> 24) & 0xFF;
        int r1 = (from >>> 16) & 0xFF;
        int g1 = (from >>> 8) & 0xFF;
        int b1 = from & 0xFF;
        int a2 = (to >>> 24) & 0xFF;
        int r2 = (to >>> 16) & 0xFF;
        int g2 = (to >>> 8) & 0xFF;
        int b2 = to & 0xFF;
        int a = (int) (a1 + ((a2 - a1) * t));
        int r = (int) (r1 + ((r2 - r1) * t));
        int g = (int) (g1 + ((g2 - g1) * t));
        int b = (int) (b1 + ((b2 - b1) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
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

    private static final class DnaAnalyzerPanelWidget extends AbstractWidget {
        private final DnaAnalyzerPanelUiComponent owner;

        private DnaAnalyzerPanelWidget(DnaAnalyzerPanelUiComponent owner, int x, int y, int width, int height) {
            super(x, y, width, height, Component.literal(owner.title));
            this.owner = owner;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();
            ClientDNAAnalyzerState.ClientData state = ClientDNAAnalyzerState.getLatest();
            String sampleUuid = state == null ? "" : safeText(state.sourceUuid(), "");
            boolean activeMode = !sampleUuid.isBlank();
            boolean processing = state != null && state.processing();
            boolean awaitingVialCollection = state != null && state.awaitingVialCollection();
            float processingProgress = state == null
                    ? 0.0F
                    : (awaitingVialCollection
                    ? 1.0F
                    : state.processingTotalTicks() <= 0
                    ? 0.0F
                    : clamp01((float) state.processingProgress() / (float) state.processingTotalTicks()));

            drawPanelBackground(gui, x, y, width, height, this.owner.frameColor);
            if (awaitingVialCollection) {
                drawCompletionOverlay(gui, minecraft, x, y, width, height);
                this.setMessage(Component.literal("Awaiting vial extraction"));
                return;
            }

            int centerX = x + (width / 2);
            int helixX = centerX - HELIX_HALF_WIDTH;
            int helixY = y + 6;
            int helixHeight = 90;
            float animationTime = minecraft.level != null
                    ? minecraft.level.getGameTime() + partialTick
                    : ((float) System.currentTimeMillis() / 50L);
            drawHelix(gui, helixX, helixY, helixHeight, animationTime, activeMode, sampleUuid, processingProgress);

            String[] slots = state == null ? emptySlots() : normalizeSlots(state.geneSlots());
            Gene[] genes = parseGenes(slots);
            if (this.owner.selectedSlot >= genes.length || this.owner.selectedSlot < 0 || genes[this.owner.selectedSlot] == null) {
                this.owner.selectedSlot = -1;
            }

            int leftSlotsX = centerX - HELIX_HALF_WIDTH - HELIX_TO_SLOT_GAP - SLOT_WIDTH;
            int rightSlotsX = centerX + HELIX_HALF_WIDTH + HELIX_TO_SLOT_GAP;
            int slotsStartY = y + 6;
            boolean isolateMode = ClientDNAAnalyzerToolState.isActive(ClientDNAAnalyzerToolState.TOOL_ISOLATE);
            if (!isolateMode) {
                this.owner.selectedIsolationSlots = new int[0];
            }

            int hoveredSlot = drawGeneSlots(this.owner, minecraft, gui, slots, genes, leftSlotsX, rightSlotsX, slotsStartY, mouseX, mouseY);
            if (isolateMode) {
                int[] unlockedModes = getUnlockedExtractionModesForPlayer(minecraft);
                this.owner.selectedExtractionCount = normalizeSelectedExtractionMode(this.owner.selectedExtractionCount, unlockedModes);
                List<IsolationOption> options = buildIsolationOptions(leftSlotsX, rightSlotsX, slotsStartY, this.owner.selectedExtractionCount);
                if (!isSelectionPresent(this.owner.selectedIsolationSlots, options)) {
                    this.owner.selectedIsolationSlots = new int[0];
                }
                IsolationOption hoveredOption = getHoveredOption(options, mouseX, mouseY);
                drawIsolationOptionHighlights(gui, options, hoveredOption, this.owner.selectedIsolationSlots);
                drawIsolateInfo(gui, minecraft, x + 12, y + 102, width - 24, y + height - 8, options, unlockedModes, this.owner.selectedExtractionCount);
                boolean canSplice = state != null
                        && state.analyzed()
                        && !state.processing()
                        && !state.awaitingVialCollection()
                        && selectionHasAnyFilledGenes(this.owner.selectedIsolationSlots, slots);
                drawModeButton(gui, minecraft, x, y, width, height, unlockedModes.length > 1, processing, awaitingVialCollection);
                drawSpliceButton(gui, minecraft, x, y, width, height, canSplice, processing, awaitingVialCollection);
            } else {
                drawSelectedGeneInfo(this.owner, minecraft, gui, genes, x + 12, y + 102, width - 24, y + height - 8);
            }

            if (hoveredSlot >= 0 && hoveredSlot < genes.length && genes[hoveredSlot] != null) {
                this.setMessage(Component.literal("Gene Slot " + (hoveredSlot + 1) + ": " + genes[hoveredSlot].getName()));
            } else if (isolateMode) {
                this.setMessage(Component.literal("Isolation mode"));
            } else {
                this.setMessage(Component.literal(this.owner.title));
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            ClientDNAAnalyzerState.ClientData state = ClientDNAAnalyzerState.getLatest();
            if (state != null && (state.processing() || state.awaitingVialCollection())) {
                return true;
            }
            String[] slots = state == null ? emptySlots() : normalizeSlots(state.geneSlots());
            Gene[] genes = parseGenes(slots);

            int centerX = this.getX() + (this.getWidth() / 2);
            int leftSlotsX = centerX - HELIX_HALF_WIDTH - HELIX_TO_SLOT_GAP - SLOT_WIDTH;
            int rightSlotsX = centerX + HELIX_HALF_WIDTH + HELIX_TO_SLOT_GAP;
            int slotsStartY = this.getY() + 6;
            boolean isolateMode = ClientDNAAnalyzerToolState.isActive(ClientDNAAnalyzerToolState.TOOL_ISOLATE);

            if (isolateMode) {
                int[] unlockedModes = getUnlockedExtractionModesForPlayer(Minecraft.getInstance());
                this.owner.selectedExtractionCount = normalizeSelectedExtractionMode(this.owner.selectedExtractionCount, unlockedModes);
                if (isPointInsideModeButton((int) event.x(), (int) event.y())) {
                    if (unlockedModes.length > 1) {
                        this.owner.selectedExtractionCount = cycleExtractionMode(this.owner.selectedExtractionCount, unlockedModes);
                        this.owner.selectedIsolationSlots = new int[0];
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    return true;
                }

                boolean canSplice = state != null && state.analyzed() && selectionHasAnyFilledGenes(this.owner.selectedIsolationSlots, slots);
                if (isPointInsideSpliceButton((int) event.x(), (int) event.y())) {
                    if (canSplice) {
                        this.sendSpliceRequest(this.owner.selectedIsolationSlots);
                    }
                    return true;
                }

                List<IsolationOption> options = buildIsolationOptions(leftSlotsX, rightSlotsX, slotsStartY, this.owner.selectedExtractionCount);
                IsolationOption clickedOption = getHoveredOption(options, (int) event.x(), (int) event.y());
                if (clickedOption != null) {
                    if (Arrays.equals(this.owner.selectedIsolationSlots, clickedOption.slots())) {
                        this.owner.selectedIsolationSlots = new int[0];
                    } else {
                        this.owner.selectedIsolationSlots = Arrays.copyOf(clickedOption.slots(), clickedOption.slots().length);
                    }
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
                return false;
            }

            int clickedSlot = getSlotAt((int) event.x(), (int) event.y(), leftSlotsX, rightSlotsX, slotsStartY);
            if (clickedSlot >= 0 && clickedSlot < genes.length && genes[clickedSlot] != null) {
                if (ClientDNAAnalyzerToolState.isRenameEnabled()) {
                    this.openRenamePopup(clickedSlot, genes[clickedSlot]);
                    return true;
                }
                if (this.owner.selectedSlot != clickedSlot) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                this.owner.selectedSlot = clickedSlot;
                this.owner.descriptionScrollOffset = 0;
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            ClientDNAAnalyzerState.ClientData state = ClientDNAAnalyzerState.getLatest();
            if ((state != null && (state.processing() || state.awaitingVialCollection()))
                    || ClientDNAAnalyzerToolState.isActive(ClientDNAAnalyzerToolState.TOOL_ISOLATE)) {
                return false;
            }
            String[] slots = state == null ? emptySlots() : normalizeSlots(state.geneSlots());
            Gene[] genes = parseGenes(slots);
            if (this.owner.selectedSlot < 0 || this.owner.selectedSlot >= genes.length || genes[this.owner.selectedSlot] == null) {
                return false;
            }

            int infoX = this.getX() + 12;
            int infoY = this.getY() + 102;
            int infoWidth = this.getWidth() - 24;
            int infoBottom = this.getY() + this.getHeight() - 8;
            int descX = infoX + 5;
            int descY = infoY + 28;
            int descW = infoWidth - 10;
            int descH = Math.max(16, infoBottom - descY - 4);

            if (mouseX < descX || mouseX >= descX + descW || mouseY < descY || mouseY >= descY + descH) {
                return false;
            }

            String description = resolveDescriptionText(genes[this.owner.selectedSlot]);
            List<String> wrapped = wrapText(Minecraft.getInstance(), description, descW - 10);
            int visibleLines = Math.max(1, (descH - 6) / 9);
            int maxScroll = Math.max(0, wrapped.size() - visibleLines);
            if (maxScroll <= 0) {
                return false;
            }

            if (scrollY > 0) {
                this.owner.descriptionScrollOffset = Math.max(0, this.owner.descriptionScrollOffset - 1);
            } else if (scrollY < 0) {
                this.owner.descriptionScrollOffset = Math.min(maxScroll, this.owner.descriptionScrollOffset + 1);
            }
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            narrationElementOutput.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, this.getMessage());
        }

        private void openRenamePopup(int slot, Gene gene) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen == null || gene == null) {
                return;
            }
            BlockPos analyzerPos = ClientDNAAnalyzerState.getLatestPos();
            if (analyzerPos == null) {
                return;
            }
            minecraft.setScreen(new DnaAnalyzerRenamePopupScreen(minecraft.screen, analyzerPos, slot, safeText(gene.getName(), "")));
        }

        private int[] getUnlockedExtractionModesForPlayer(Minecraft minecraft) {
            if (minecraft.player == null) {
                return new int[]{EXTRACT_COUNT_LOW_INTELLIGENCE};
            }
            double intelligence = minecraft.player.getAttributeValue(IntelligenceAttributes.INTELLIGENCE);
            if (intelligence >= HIGH_INTELLIGENCE_THRESHOLD) {
                return new int[]{EXTRACT_COUNT_LOW_INTELLIGENCE, EXTRACT_COUNT_MID_INTELLIGENCE, EXTRACT_COUNT_HIGH_INTELLIGENCE};
            }
            if (intelligence >= MID_INTELLIGENCE_THRESHOLD) {
                return new int[]{EXTRACT_COUNT_LOW_INTELLIGENCE, EXTRACT_COUNT_MID_INTELLIGENCE};
            }
            return new int[]{EXTRACT_COUNT_LOW_INTELLIGENCE};
        }

        private int normalizeSelectedExtractionMode(int selectedMode, int[] unlockedModes) {
            if (unlockedModes == null || unlockedModes.length == 0) {
                return EXTRACT_COUNT_LOW_INTELLIGENCE;
            }
            for (int mode : unlockedModes) {
                if (mode == selectedMode) {
                    return selectedMode;
                }
            }
            return unlockedModes[unlockedModes.length - 1];
        }

        private int cycleExtractionMode(int selectedMode, int[] unlockedModes) {
            if (unlockedModes == null || unlockedModes.length == 0) {
                return EXTRACT_COUNT_LOW_INTELLIGENCE;
            }
            for (int i = 0; i < unlockedModes.length; i++) {
                if (unlockedModes[i] == selectedMode) {
                    return unlockedModes[(i + 1) % unlockedModes.length];
                }
            }
            return unlockedModes[0];
        }

        private List<IsolationOption> buildIsolationOptions(int leftX, int rightX, int startY, int extractCount) {
            List<IsolationOption> options = new ArrayList<>();
            if (extractCount == 1) {
                for (int i = 0; i < 6; i++) {
                    options.add(optionForSlots(new int[]{i}, leftX, rightX, startY, "Slot " + (i + 1)));
                }
                return options;
            }
            if (extractCount == 2) {
                options.add(optionForSlots(new int[]{0, 1}, leftX, rightX, startY, "Left A"));
                options.add(optionForSlots(new int[]{1, 2}, leftX, rightX, startY, "Left B"));
                options.add(optionForSlots(new int[]{3, 4}, leftX, rightX, startY, "Right A"));
                options.add(optionForSlots(new int[]{4, 5}, leftX, rightX, startY, "Right B"));
                return options;
            }
            options.add(optionForSlots(new int[]{0, 1, 2}, leftX, rightX, startY, "Left"));
            options.add(optionForSlots(new int[]{3, 4, 5}, leftX, rightX, startY, "Right"));
            return options;
        }

        private IsolationOption optionForSlots(int[] slots, int leftX, int rightX, int startY, String label) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (int slot : slots) {
                int slotX = slot < 3 ? leftX : rightX;
                int row = slot % 3;
                int slotY = startY + row * (SLOT_HEIGHT + SLOT_ROW_GAP);
                minX = Math.min(minX, slotX);
                minY = Math.min(minY, slotY);
                maxX = Math.max(maxX, slotX + SLOT_WIDTH);
                maxY = Math.max(maxY, slotY + SLOT_HEIGHT);
            }
            return new IsolationOption(Arrays.copyOf(slots, slots.length), minX, minY, maxX - minX, maxY - minY, label);
        }

        private IsolationOption getHoveredOption(List<IsolationOption> options, int mouseX, int mouseY) {
            for (IsolationOption option : options) {
                if (mouseX >= option.x() && mouseX < option.x() + option.width()
                        && mouseY >= option.y() && mouseY < option.y() + option.height()) {
                    return option;
                }
            }
            return null;
        }

        private boolean isSelectionPresent(int[] selectedSlots, List<IsolationOption> options) {
            if (selectedSlots == null || selectedSlots.length == 0) {
                return true;
            }
            for (IsolationOption option : options) {
                if (Arrays.equals(selectedSlots, option.slots())) {
                    return true;
                }
            }
            return false;
        }

        private void drawIsolationOptionHighlights(GuiGraphicsExtractor gui, List<IsolationOption> options, IsolationOption hoveredOption, int[] selectedSlots) {
            for (IsolationOption option : options) {
                boolean selected = Arrays.equals(selectedSlots, option.slots());
                boolean hovered = hoveredOption != null && Arrays.equals(hoveredOption.slots(), option.slots());
                int fillColor = selected ? 0x663AA6FF : hovered ? 0x444DCBFF : 0x221E3F57;
                int border = selected ? 0xFFFFC857 : hovered ? 0xFF8AD0FF : 0xAA557391;
                gui.fill(option.x(), option.y(), option.x() + option.width(), option.y() + option.height(), fillColor);
                gui.fill(option.x(), option.y(), option.x() + option.width(), option.y() + 1, border);
                gui.fill(option.x(), option.y() + option.height() - 1, option.x() + option.width(), option.y() + option.height(), border);
                gui.fill(option.x(), option.y(), option.x() + 1, option.y() + option.height(), border);
                gui.fill(option.x() + option.width() - 1, option.y(), option.x() + option.width(), option.y() + option.height(), border);
            }
        }

        private void drawIsolateInfo(
                GuiGraphicsExtractor gui,
                Minecraft minecraft,
                int x,
                int y,
                int width,
                int bottomY,
                List<IsolationOption> options,
                int[] unlockedModes,
                int selectedMode
        ) {
            gui.fill(x, y, x + width, bottomY, 0x88101824);
            gui.fill(x, y, x + width, y + 1, 0xAA476485);
            gui.fill(x, bottomY - 1, x + width, bottomY, 0xAA476485);
            gui.fill(x, y, x + 1, bottomY, 0xAA476485);
            gui.fill(x + width - 1, y, x + width, bottomY, 0xAA476485);
            gui.text(minecraft.font, "Isolation Mode", x + 6, y + 4, 0xFFE6F2FF, false);
            String modeLine = "Splice Mode: " + selectedMode;
            gui.text(minecraft.font, modeLine, x + 6, y + 16, 0xFFB7D9FF, false);
            if (unlockedModes.length > 1) {
                gui.text(minecraft.font, "Use Mode to toggle control level.", x + 6, y + 27, 0xFF9AA9BC, false);
            } else {
                gui.text(minecraft.font, "Raise INT to unlock extra modes.", x + 6, y + 27, 0xFF9AA9BC, false);
            }
            String selected = "None";
            for (IsolationOption option : options) {
                if (Arrays.equals(this.owner.selectedIsolationSlots, option.slots())) {
                    selected = option.label();
                    break;
                }
            }
            gui.text(minecraft.font, "Selection: " + selected, x + 6, y + 40, 0xFFB7D9FF, false);
        }

        private void drawModeButton(
                GuiGraphicsExtractor gui,
                Minecraft minecraft,
                int x,
                int y,
                int width,
                int height,
                boolean canToggleMode,
                boolean processing,
                boolean awaitingVialCollection
        ) {
            int buttonX = x + width - 138;
            int buttonY = y + height - 26;
            int buttonW = 58;
            int buttonH = 16;
            boolean disabled = !canToggleMode || processing || awaitingVialCollection;
            int border = disabled ? 0xFF3A4A5E : 0xFF79B8FF;
            int fill = disabled ? 0xAA1C222B : 0xAA1C3D54;
            gui.fill(buttonX, buttonY, buttonX + buttonW, buttonY + buttonH, fill);
            gui.fill(buttonX, buttonY, buttonX + buttonW, buttonY + 1, border);
            gui.fill(buttonX, buttonY + buttonH - 1, buttonX + buttonW, buttonY + buttonH, border);
            gui.fill(buttonX, buttonY, buttonX + 1, buttonY + buttonH, border);
            gui.fill(buttonX + buttonW - 1, buttonY, buttonX + buttonW, buttonY + buttonH, border);
            String text = "Mode " + this.owner.selectedExtractionCount;
            int color = disabled ? 0xFF738398 : 0xFFE6F2FF;
            gui.text(minecraft.font, text, buttonX + (buttonW - minecraft.font.width(text)) / 2, buttonY + 4, color, false);
        }

        private void drawSpliceButton(
                GuiGraphicsExtractor gui,
                Minecraft minecraft,
                int x,
                int y,
                int width,
                int height,
                boolean canSplice,
                boolean processing,
                boolean awaitingVialCollection
        ) {
            int buttonX = x + width - 72;
            int buttonY = y + height - 26;
            int buttonW = 58;
            int buttonH = 16;
            boolean disabled = !canSplice || processing || awaitingVialCollection;
            int border = disabled ? 0xFF3A4A5E : 0xFF79B8FF;
            int fill = disabled ? 0xAA1C222B : 0xAA1C3D54;
            gui.fill(buttonX, buttonY, buttonX + buttonW, buttonY + buttonH, fill);
            gui.fill(buttonX, buttonY, buttonX + buttonW, buttonY + 1, border);
            gui.fill(buttonX, buttonY + buttonH - 1, buttonX + buttonW, buttonY + buttonH, border);
            gui.fill(buttonX, buttonY, buttonX + 1, buttonY + buttonH, border);
            gui.fill(buttonX + buttonW - 1, buttonY, buttonX + buttonW, buttonY + buttonH, border);
            String text = processing ? "..." : awaitingVialCollection ? "Done" : "Splice";
            int color = disabled ? 0xFF738398 : 0xFFE6F2FF;
            gui.text(minecraft.font, text, buttonX + (buttonW - minecraft.font.width(text)) / 2, buttonY + 4, color, false);
        }

        private void drawCompletionOverlay(GuiGraphicsExtractor gui, Minecraft minecraft, int x, int y, int width, int height) {
            gui.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xDD05090F);

            int popupWidth = Math.min(220, width - 24);
            int popupHeight = 74;
            int popupX = x + (width - popupWidth) / 2;
            int popupY = y + (height - popupHeight) / 2;

            gui.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, 0xEE111A25);
            gui.fill(popupX, popupY, popupX + popupWidth, popupY + 1, 0xFF79B8FF);
            gui.fill(popupX, popupY + popupHeight - 1, popupX + popupWidth, popupY + popupHeight, 0xFF79B8FF);
            gui.fill(popupX, popupY, popupX + 1, popupY + popupHeight, 0xFF79B8FF);
            gui.fill(popupX + popupWidth - 1, popupY, popupX + popupWidth, popupY + popupHeight, 0xFF79B8FF);

            String title = "Extraction Complete";
            gui.text(minecraft.font, title, popupX + (popupWidth - minecraft.font.width(title)) / 2, popupY + 10, 0xFFAEEFBF, false);

            String line1 = "Right-click analyzer with";
            String line2 = "an Empty Gene Vial to continue.";
            gui.text(minecraft.font, line1, popupX + (popupWidth - minecraft.font.width(line1)) / 2, popupY + 30, 0xFFE6F2FF, false);
            gui.text(minecraft.font, line2, popupX + (popupWidth - minecraft.font.width(line2)) / 2, popupY + 42, 0xFFE6F2FF, false);
        }

        private boolean isPointInsideSpliceButton(int mouseX, int mouseY) {
            int buttonX = this.getX() + this.getWidth() - 72;
            int buttonY = this.getY() + this.getHeight() - 26;
            return mouseX >= buttonX && mouseX < buttonX + 58 && mouseY >= buttonY && mouseY < buttonY + 16;
        }

        private boolean isPointInsideModeButton(int mouseX, int mouseY) {
            int buttonX = this.getX() + this.getWidth() - 138;
            int buttonY = this.getY() + this.getHeight() - 26;
            return mouseX >= buttonX && mouseX < buttonX + 58 && mouseY >= buttonY && mouseY < buttonY + 16;
        }

        private boolean selectionHasAnyFilledGenes(int[] selection, String[] slots) {
            if (selection == null || selection.length == 0) {
                return false;
            }
            for (int index : selection) {
                if (index >= 0 && index < slots.length && slots[index] != null && !slots[index].isBlank()) {
                    return true;
                }
            }
            return false;
        }

        private void sendSpliceRequest(int[] selectedSlots) {
            BlockPos analyzerPos = ClientDNAAnalyzerState.getLatestPos();
            if (analyzerPos == null || selectedSlots == null || selectedSlots.length == 0) {
                return;
            }
            ClientPacketDistributor.sendToServer(new DNAAnalyzerExtractPayload(analyzerPos, Arrays.copyOf(selectedSlots, selectedSlots.length)));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }

        private record IsolationOption(int[] slots, int x, int y, int width, int height, String label) {
        }
    }

    private static int getSlotAt(int mouseX, int mouseY, int leftX, int rightX, int startY) {
        for (int i = 0; i < 6; i++) {
            int row = i % 3;
            int slotX = i < 3 ? leftX : rightX;
            int slotY = startY + row * (SLOT_HEIGHT + SLOT_ROW_GAP);
            if (mouseX >= slotX && mouseX < slotX + SLOT_WIDTH && mouseY >= slotY && mouseY < slotY + SLOT_HEIGHT) {
                return i;
            }
        }
        return -1;
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
