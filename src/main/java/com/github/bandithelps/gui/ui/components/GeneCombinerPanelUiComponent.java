package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.gene_combiner.ClientGeneCombinerState;
import com.github.bandithelps.gene.Gene;
import com.github.bandithelps.items.GeneVialItem;
import com.github.bandithelps.network.GeneCombinerStartPayload;
import com.github.bandithelps.network.GeneCombinerTransferPayload;
import com.github.bandithelps.utils.gene.GeneUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.threetag.palladium.client.gui.ui.component.UiComponent;
import net.threetag.palladium.client.gui.ui.component.UiComponentProperties;
import net.threetag.palladium.client.gui.ui.component.UiComponentSerializer;
import net.threetag.palladium.client.gui.ui.screen.UiScreen;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;

public class GeneCombinerPanelUiComponent extends UiComponent {
    private static final int INPUT_SLOTS = 4;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 7;
    private static final int LIST_WIDTH = 120;
    private static final int LIST_HEADER_HEIGHT = 14;
    private static final int SLOT_SIZE = 34;

    public static final MapCodec<GeneCombinerPanelUiComponent> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
            Codec.INT.optionalFieldOf("frame_color", 0xFF79B8FF).forGetter(GeneCombinerPanelUiComponent::getFrameColor),
            Codec.INT.optionalFieldOf("panel_color", 0xCC172231).forGetter(GeneCombinerPanelUiComponent::getPanelColor),
            Codec.INT.optionalFieldOf("text_color", 0xFFE6F2FF).forGetter(GeneCombinerPanelUiComponent::getTextColor),
            propertiesCodec(340, 188)
    ).apply(instance, GeneCombinerPanelUiComponent::new));

    private final int frameColor;
    private final int panelColor;
    private final int textColor;

    public GeneCombinerPanelUiComponent(int frameColor, int panelColor, int textColor, UiComponentProperties properties) {
        super(properties);
        this.frameColor = withOpaqueAlpha(frameColor);
        this.panelColor = withOpaqueAlpha(panelColor);
        this.textColor = withOpaqueAlpha(textColor);
    }

    @Override
    public UiComponentSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.GENE_COMBINER_PANEL;
    }

    @Override
    public AbstractWidget buildWidget(UiScreen screen, ScreenRectangle rectangle) {
        return new GeneCombinerWidget(this, this.getX(rectangle), this.getY(rectangle), this.getWidth(), this.getHeight());
    }

    public int getFrameColor() {
        return this.frameColor;
    }

    public int getPanelColor() {
        return this.panelColor;
    }

    public int getTextColor() {
        return this.textColor;
    }

    private static int withOpaqueAlpha(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    private static final class GeneCombinerWidget extends AbstractWidget {
        private final GeneCombinerPanelUiComponent owner;
        private int scrollOffset = 0;
        private InventoryEntry pressedEntry;
        private DraggingEntry dragging;
        private String lastSeenResultKind = "empty";
        private long resultFlashStartMs = 0L;
        private int resultFlashColor = 0;

        private GeneCombinerWidget(GeneCombinerPanelUiComponent owner, int x, int y, int width, int height) {
            super(x, y, width, height, Component.literal("Gene Combiner"));
            this.owner = owner;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();
            drawFrame(gui, x, y, width, height, this.owner.panelColor, this.owner.frameColor);

            List<InventoryEntry> entries = getInventoryEntries(minecraft);
            int maxScroll = Math.max(0, entries.size() - VISIBLE_ROWS);
            this.scrollOffset = clamp(this.scrollOffset, 0, maxScroll);

            int listX = x + 6;
            int listY = y + 8;
            int listHeight = LIST_HEADER_HEIGHT + (VISIBLE_ROWS * ROW_HEIGHT) + 6;
            drawInventoryList(gui, minecraft, entries, listX, listY, LIST_WIDTH, listHeight, mouseX, mouseY);

            ClientGeneCombinerState.ClientData state = ClientGeneCombinerState.getLatest();
            int machineX = listX + LIST_WIDTH + 10;
            int machineY = y + 8;
            int machineWidth = width - (machineX - x) - 6;
            int machineHeight = height - 16;
            drawMachinePanel(gui, minecraft, state, machineX, machineY, machineWidth, machineHeight, mouseX, mouseY);

            if (this.dragging != null) {
                gui.item(this.dragging.stack(), mouseX - 8, mouseY - 8);
                gui.text(minecraft.font, trimToWidth(minecraft, this.dragging.label(), 96), mouseX + 10, mouseY - 2, 0xFFD5E8FF, false);
            }

            drawResultFlashOverlay(gui, x, y, width, height);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            int mouseX = (int) event.x();
            int mouseY = (int) event.y();

            if (isInsideCombineButton(mouseX, mouseY)) {
                BlockPos pos = ClientGeneCombinerState.getLatestPos();
                if (pos != null) {
                    ClientPacketDistributor.sendToServer(new GeneCombinerStartPayload(pos));
                    clickSound();
                }
                return true;
            }

            List<InventoryEntry> entries = getInventoryEntries(Minecraft.getInstance());
            InventoryEntry clickedEntry = getInventoryEntryAt(entries, mouseX, mouseY);
            if (clickedEntry != null && event.button() == 0) {
                this.pressedEntry = clickedEntry;
                this.dragging = null;
                return true;
            }

            int slot = getMachineSlotAt(mouseX, mouseY);
            if (slot >= 0) {
                BlockPos pos = ClientGeneCombinerState.getLatestPos();
                if (pos == null) {
                    return true;
                }
                ClientPacketDistributor.sendToServer(new GeneCombinerTransferPayload(pos, -1, slot));
                clickSound();
                return true;
            }

            this.pressedEntry = null;
            this.dragging = null;
            return false;
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
            if (event.button() != 0) {
                return false;
            }
            if (this.pressedEntry == null) {
                return false;
            }
            if (this.dragging == null) {
                this.dragging = new DraggingEntry(
                        this.pressedEntry.primaryInventorySlot(),
                        this.pressedEntry.stack().copy(),
                        this.pressedEntry.label()
                );
                clickSound();
            }
            return true;
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            if (event.button() != 0) {
                return false;
            }
            if (this.dragging == null) {
                this.pressedEntry = null;
                return false;
            }
            int slot = getMachineSlotAt((int) event.x(), (int) event.y());
            if (slot >= 0) {
                BlockPos pos = ClientGeneCombinerState.getLatestPos();
                if (pos != null) {
                    ClientPacketDistributor.sendToServer(new GeneCombinerTransferPayload(pos, this.dragging.inventorySlot(), slot));
                    clickSound();
                }
            }
            this.dragging = null;
            this.pressedEntry = null;
            return true;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            int listX = this.getX() + 6;
            int listY = this.getY() + 8;
            int listHeight = LIST_HEADER_HEIGHT + (VISIBLE_ROWS * ROW_HEIGHT) + 6;
            if (mouseX < listX || mouseX >= listX + LIST_WIDTH || mouseY < listY || mouseY >= listY + listHeight) {
                return false;
            }
            List<InventoryEntry> entries = getInventoryEntries(Minecraft.getInstance());
            int maxScroll = Math.max(0, entries.size() - VISIBLE_ROWS);
            if (scrollY > 0) {
                this.scrollOffset = Math.max(0, this.scrollOffset - 1);
            } else if (scrollY < 0) {
                this.scrollOffset = Math.min(maxScroll, this.scrollOffset + 1);
            }
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            narrationElementOutput.add(NarratedElementType.TITLE, this.getMessage());
        }

        private void drawInventoryList(
                GuiGraphicsExtractor gui,
                Minecraft minecraft,
                List<InventoryEntry> entries,
                int x,
                int y,
                int width,
                int height,
                int mouseX,
                int mouseY
        ) {
            drawFrame(gui, x, y, width, height, 0xAA111A26, this.owner.frameColor);
            gui.text(minecraft.font, "Vials In Inventory", x + 4, y + 3, this.owner.textColor, false);
            int rowsY = y + LIST_HEADER_HEIGHT;
            int visible = Math.min(VISIBLE_ROWS, Math.max(0, entries.size() - this.scrollOffset));
            for (int i = 0; i < visible; i++) {
                InventoryEntry entry = entries.get(this.scrollOffset + i);
                int rowY = rowsY + (i * ROW_HEIGHT);
                boolean hovered = mouseX >= x + 2 && mouseX < x + width - 2 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
                int fill = hovered ? 0xAA2A3F54 : 0xAA182533;
                int border = hovered ? 0xFF9AD1FF : 0xFF3B5A78;
                drawFrame(gui, x + 2, rowY, width - 4, ROW_HEIGHT - 1, fill, border);
                gui.item(entry.stack(), x + 6, rowY + 2);
                String copiesSuffix = entry.copies() > 1 ? " (" + entry.copies() + ")" : "";
                int suffixWidth = minecraft.font.width(copiesSuffix);
                String label = trimToWidth(minecraft, entry.label(), Math.max(20, width - 30 - suffixWidth));
                gui.text(minecraft.font, label, x + 24, rowY + 4, 0xFFDCEFFF, false);
                if (!copiesSuffix.isBlank()) {
                    gui.text(minecraft.font, copiesSuffix, x + width - 8 - suffixWidth, rowY + 4, 0xFFAED6F3, false);
                }

                if (hovered) {
                    List<Component> tooltip = buildVialTooltip(entry.stack(), entry.label(), entry.geneCount(), entry.copies());
                    gui.setTooltipForNextFrame(
                            minecraft.font,
                            tooltip.stream().map(Component::getVisualOrderText).toList(),
                            mouseX,
                            mouseY
                    );
                }
            }

            if (entries.isEmpty()) {
                gui.text(minecraft.font, "No gene vials found", x + 6, rowsY + 6, 0xFF879AB1, false);
            }

            int maxScroll = Math.max(0, entries.size() - VISIBLE_ROWS);
            if (maxScroll > 0) {
                int barX = x + width - 4;
                int barY = rowsY + 2;
                int barH = (VISIBLE_ROWS * ROW_HEIGHT) - 4;
                gui.fill(barX, barY, barX + 2, barY + barH, 0xFF22394F);
                int thumbHeight = Math.max(12, (barH * VISIBLE_ROWS) / entries.size());
                int track = barH - thumbHeight;
                int thumbY = barY + (track <= 0 ? 0 : (track * this.scrollOffset / maxScroll));
                gui.fill(barX, thumbY, barX + 2, thumbY + thumbHeight, 0xFF89BCE8);
            }
        }

        private void drawMachinePanel(
                GuiGraphicsExtractor gui,
                Minecraft minecraft,
                ClientGeneCombinerState.ClientData state,
                int x,
                int y,
                int width,
                int height,
                int mouseX,
                int mouseY
        ) {
            drawFrame(gui, x, y, width, height, 0xAA111A26, this.owner.frameColor);
            gui.text(minecraft.font, "Combiner", x + 4, y + 3, this.owner.textColor, false);

            int slotsX = x + 10;
            int slotsY = y + 18;
            int[] counts = state == null ? new int[0] : state.inputGeneCounts();
            String[] labels = state == null ? new String[0] : state.inputSlotLabels();
            String[] tooltips = state == null ? new String[0] : state.inputSlotTooltips();
            for (int i = 0; i < INPUT_SLOTS; i++) {
                int slotX = slotsX + (i % 2) * (SLOT_SIZE + 10);
                int slotY = slotsY + (i / 2) * (SLOT_SIZE + 10);
                boolean filled = i < counts.length && counts[i] > 0;
                boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
                int border = hovered ? 0xFF9AD1FF : filled ? 0xFF87C47B : 0xFF49637D;
                drawFrame(gui, slotX, slotY, SLOT_SIZE, SLOT_SIZE, 0xAA1B2A3B, border);
                if (filled) {
                    gui.item(new ItemStack(YourHeroAcademia.GENE_VIAL.get()), slotX + 9, slotY + 9);
                } else {
                    gui.text(minecraft.font, "-", slotX + (SLOT_SIZE - minecraft.font.width("-")) / 2, slotY + 13, 0xFF7E8EA3, false);
                }
                if (hovered) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.literal("Input Slot " + (i + 1)));
                    if (filled) {
                        String label = i < labels.length ? labels[i] : "";
                        if (!label.isBlank()) {
                            tooltip.add(Component.literal(label).withStyle(ChatFormatting.AQUA));
                        }
                        if (i < tooltips.length && tooltips[i] != null && !tooltips[i].isBlank()) {
                            for (String line : tooltips[i].split("\\n")) {
                                if (!line.isBlank()) {
                                    tooltip.add(styleInputTooltipLine(line));
                                }
                            }
                        } else {
                            tooltip.add(Component.literal(counts[i] + " genes").withStyle(ChatFormatting.GRAY));
                        }
                        tooltip.add(Component.literal("Click to clear slot").withStyle(ChatFormatting.DARK_GRAY));
                    } else {
                        tooltip.add(Component.literal("Drop a vial here").withStyle(ChatFormatting.GRAY));
                    }
                    gui.setTooltipForNextFrame(
                            minecraft.font,
                            tooltip.stream().map(Component::getVisualOrderText).toList(),
                            mouseX,
                            mouseY
                    );
                }
            }

            boolean processing = state != null && state.processing();
            float progress = state == null || state.processingTotalTicks() <= 0
                    ? 0.0F
                    : clamp01((float) state.processingProgress() / (float) state.processingTotalTicks());
            String outputKind = state == null ? "empty" : state.outputKind();
            updateResultFlash(outputKind, processing);
            boolean failedLastAttempt = !processing && "slop".equals(outputKind);
            boolean successfulLastAttempt = !processing && "vial".equals(outputKind);

            int machineCenterX = x + width - 78;
            int machineCenterY = y + 52;
            drawMixerAnimation(gui, machineCenterX, machineCenterY, processing, progress, minecraft);

            int progressX = x + 10;
            int progressY = y + height - 44;
            int progressW = width - 20;
            int progressBorder = failedLastAttempt ? 0xFFAA5A5A : successfulLastAttempt ? 0xFF57A968 : 0xFF43617F;
            int progressFill = failedLastAttempt ? 0xFFCC6B6B : successfulLastAttempt ? 0xFF6FD88A : 0xFF7BD6FF;
            drawFrame(gui, progressX, progressY, progressW, 10, 0xAA1B2A3B, progressBorder);
            gui.fill(progressX + 1, progressY + 1, progressX + 1 + Math.round((progressW - 2) * progress), progressY + 9, progressFill);

            int loadedInputs = countLoadedInputs(counts);
            String status = processing
                    ? "Combining..."
                    : failedLastAttempt
                    ? "Failure"
                    : successfulLastAttempt
                    ? "Success"
                    : "Load at least 2 vials";
            int statusColor = failedLastAttempt ? 0xFFFF9A9A : successfulLastAttempt ? 0xFF9CF5AE : 0xFFB7D9FF;
            gui.text(minecraft.font, status, progressX, progressY - 10, statusColor, false);

            int buttonX = x + width - 80;
            int buttonY = y + height - 66;
            boolean canCombine = !processing && loadedInputs >= 2;
            drawFrame(gui, buttonX, buttonY, 70, 18, canCombine ? 0xAA1C3D54 : 0xAA1C222B, canCombine ? 0xFF79B8FF : 0xFF41546B);
            String buttonLabel = processing ? "Working" : "Combine";
            int buttonTextColor = canCombine ? 0xFFE6F2FF : 0xFF7A8A9D;
            gui.text(minecraft.font, buttonLabel, buttonX + (70 - minecraft.font.width(buttonLabel)) / 2, buttonY + 5, buttonTextColor, false);

            if (!"empty".equals(outputKind)) {
                String result = state.outputLabel();
                if (result == null || result.isBlank()) {
                    result = "slop".equals(outputKind) ? "Genetic Slop" : "Gene Vial";
                }
                if (failedLastAttempt) {
                    gui.text(minecraft.font, "Combination failed: unstable genome.", x + 10, y + height - 27, 0xFFFF8D8D, false);
                } else if (successfulLastAttempt) {
                    gui.text(minecraft.font, "Combination successful!", x + 10, y + height - 27, 0xFF98F5B1, false);
                }
                int resultColor = failedLastAttempt ? 0xFFFFB3B3 : 0xFFAEEFBF;
                gui.text(minecraft.font, "Result: " + trimToWidth(minecraft, result, width - 20), x + 10, y + height - 16, resultColor, false);
            }
        }

        private void drawMixerAnimation(GuiGraphicsExtractor gui, int centerX, int centerY, boolean active, float progress, Minecraft minecraft) {
            float time = minecraft.level != null ? minecraft.level.getGameTime() : ((float) System.currentTimeMillis() / 50L);
            float speed = active ? 0.4F : 0.08F;
            for (int i = 0; i < 20; i++) {
                float angle = (time * speed) + (i * 0.4F);
                int radius = active ? 16 : 12;
                int px = centerX + Math.round((float) Math.cos(angle) * radius);
                int py = centerY + Math.round((float) Math.sin(angle) * (radius - 5));
                int color = active ? 0xFF7BD6FF : 0xFF4E7592;
                if (active && i < Math.round(progress * 20.0F)) {
                    color = 0xFF96F5A8;
                }
                gui.fill(px, py, px + 2, py + 2, color);
            }
        }

        private boolean isInsideCombineButton(int mouseX, int mouseY) {
            int x = this.getX() + this.getWidth() - 80;
            int y = this.getY() + this.getHeight() - 66;
            return mouseX >= x && mouseX < x + 70 && mouseY >= y && mouseY < y + 18;
        }

        private void updateResultFlash(String outputKind, boolean processing) {
            String normalized = outputKind == null ? "empty" : outputKind;
            if (processing) {
                this.lastSeenResultKind = normalized;
                return;
            }
            boolean changedToResult = !normalized.equals(this.lastSeenResultKind)
                    && ("slop".equals(normalized) || "vial".equals(normalized));
            if (changedToResult) {
                this.resultFlashStartMs = System.currentTimeMillis();
                this.resultFlashColor = "slop".equals(normalized) ? 0xAAFF4B4B : 0xAA52FF88;
            }
            this.lastSeenResultKind = normalized;
        }

        private void drawResultFlashOverlay(GuiGraphicsExtractor gui, int x, int y, int width, int height) {
            if (this.resultFlashStartMs <= 0L) {
                return;
            }
            long elapsed = System.currentTimeMillis() - this.resultFlashStartMs;
            long durationMs = 1400L;
            if (elapsed >= durationMs) {
                this.resultFlashStartMs = 0L;
                return;
            }
            float life = 1.0F - ((float) elapsed / (float) durationMs);
            int alpha = Math.max(0, Math.min(255, Math.round(((this.resultFlashColor >>> 24) & 0xFF) * life)));
            int rgb = this.resultFlashColor & 0x00FFFFFF;
            int color = (alpha << 24) | rgb;
            gui.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
        }

        private int getMachineSlotAt(int mouseX, int mouseY) {
            int startX = this.getX() + 6 + LIST_WIDTH + 10 + 10;
            int startY = this.getY() + 8 + 18;
            for (int i = 0; i < INPUT_SLOTS; i++) {
                int slotX = startX + (i % 2) * (SLOT_SIZE + 10);
                int slotY = startY + (i / 2) * (SLOT_SIZE + 10);
                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    return i;
                }
            }
            return -1;
        }

        private InventoryEntry getInventoryEntryAt(List<InventoryEntry> entries, int mouseX, int mouseY) {
            int x = this.getX() + 6;
            int y = this.getY() + 8 + LIST_HEADER_HEIGHT;
            int width = LIST_WIDTH;
            int visible = Math.min(VISIBLE_ROWS, Math.max(0, entries.size() - this.scrollOffset));
            for (int i = 0; i < visible; i++) {
                int rowY = y + (i * ROW_HEIGHT);
                if (mouseX >= x + 2 && mouseX < x + width - 2 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                    return entries.get(this.scrollOffset + i);
                }
            }
            return null;
        }

        private List<InventoryEntry> getInventoryEntries(Minecraft minecraft) {
            List<InventoryEntry> entries = new ArrayList<>();
            if (minecraft.player == null) {
                return entries;
            }
            Inventory inventory = minecraft.player.getInventory();
            Map<String, GroupAccumulator> grouped = new LinkedHashMap<>();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.isEmpty() || stack.getItem() != YourHeroAcademia.GENE_VIAL.get()) {
                    continue;
                }
                int geneCount = GeneVialItem.getGeneCount(stack);
                String label = resolveVialLabel(stack, geneCount);
                String key = buildGroupingKey(stack);
                GroupAccumulator existing = grouped.get(key);
                if (existing == null) {
                    grouped.put(key, new GroupAccumulator(slot, stack.copy(), geneCount, label, 1));
                } else {
                    existing.copies++;
                }
            }
            for (GroupAccumulator group : grouped.values()) {
                entries.add(new InventoryEntry(
                        group.primaryInventorySlot,
                        group.stack,
                        group.geneCount,
                        group.label,
                        group.copies
                ));
            }
            return entries;
        }

        private String buildGroupingKey(ItemStack stack) {
            List<String> genes = GeneVialItem.getStoredGeneList(stack);
            String sourceName = GeneVialItem.getSourceName(stack);
            String sourceUuid = GeneVialItem.getSourceUuid(stack);
            return String.join("|", genes) + "#" + (sourceName == null ? "" : sourceName) + "#" + (sourceUuid == null ? "" : sourceUuid);
        }

        private String resolveVialLabel(ItemStack stack, int geneCount) {
            String multiSuffix = geneCount > 1 ? "+" : "";
            List<String> genes = GeneVialItem.getStoredGeneList(stack);
            for (String raw : genes) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                Gene parsed = GeneUtil.parseGene(raw);
                if (parsed != null) {
                    return parsed.getName() + multiSuffix;
                }
            }
            String source = GeneVialItem.getSourceName(stack);
            if (source != null && !source.isBlank()) {
                return source + multiSuffix;
            }
            return geneCount + " gene" + (geneCount == 1 ? "" : "s") + multiSuffix;
        }

        private List<Component> buildVialTooltip(ItemStack stack, String fallbackLabel, int geneCount, int copies) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(fallbackLabel).withStyle(ChatFormatting.AQUA));
            if (copies > 1) {
                tooltip.add(Component.literal("Copies: " + copies).withStyle(ChatFormatting.DARK_AQUA));
            }
            List<String> genes = GeneVialItem.getStoredGeneList(stack);
            int index = 1;
            for (String raw : genes) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                Gene parsed = GeneUtil.parseGene(raw);
                if (parsed == null) {
                    continue;
                }
                tooltip.add(Component.literal(index + ". " + parsed.getName()
                                + " [" + parsed.getCategory().name() + "]"
                                + " (" + parsed.getType().getId() + ", q:" + parsed.getQuality() + ")")
                        .withStyle(getCategoryColor(parsed.getCategory().name())));
                if (parsed.hasSideEffects()) {
                    parsed.getSideEffects().forEach(sideEffect ->
                            tooltip.add(Component.literal("   - Side effect: " + sideEffect.getDisplayName())
                                    .withStyle(ChatFormatting.RED)));
                }
                index++;
            }
            if (index == 1) {
                tooltip.add(Component.literal("No genes stored").withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.literal("Total genes: " + geneCount).withStyle(ChatFormatting.GOLD));
            }
            return tooltip;
        }

        private Component styleInputTooltipLine(String line) {
            if (line == null || line.isBlank()) {
                return Component.literal("");
            }
            if (line.startsWith("Gene Vial")) {
                return Component.literal(line).withStyle(ChatFormatting.AQUA);
            }
            if (line.contains("Side effect:")) {
                return Component.literal(line).withStyle(ChatFormatting.RED);
            }
            if (Character.isDigit(line.charAt(0))) {
                String upper = line.toUpperCase();
                if (upper.contains("[BUILDER]")) {
                    return Component.literal(line).withStyle(getCategoryColor("BUILDER"));
                }
                if (upper.contains("[ATTRIBUTE]")) {
                    return Component.literal(line).withStyle(getCategoryColor("ATTRIBUTE"));
                }
                if (upper.contains("[RESISTANCE]")) {
                    return Component.literal(line).withStyle(getCategoryColor("RESISTANCE"));
                }
                if (upper.contains("[COSMETIC]")) {
                    return Component.literal(line).withStyle(getCategoryColor("COSMETIC"));
                }
                if (upper.contains("[ABILITY]")) {
                    return Component.literal(line).withStyle(getCategoryColor("ABILITY"));
                }
                if (upper.contains("[QUIRK]")) {
                    return Component.literal(line).withStyle(getCategoryColor("QUIRK"));
                }
            }
            return Component.literal(line).withStyle(ChatFormatting.GRAY);
        }

        private ChatFormatting getCategoryColor(String categoryName) {
            if (categoryName == null) {
                return ChatFormatting.WHITE;
            }
            return switch (categoryName.toUpperCase()) {
                case "BUILDER" -> ChatFormatting.BLUE;
                case "ATTRIBUTE" -> ChatFormatting.GREEN;
                case "RESISTANCE" -> ChatFormatting.DARK_GREEN;
                case "COSMETIC" -> ChatFormatting.LIGHT_PURPLE;
                case "ABILITY" -> ChatFormatting.YELLOW;
                case "QUIRK" -> ChatFormatting.GOLD;
                default -> ChatFormatting.WHITE;
            };
        }

        private void clickSound() {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }

        private static int countLoadedInputs(int[] counts) {
            int loaded = 0;
            if (counts == null) {
                return 0;
            }
            for (int count : counts) {
                if (count > 0) {
                    loaded++;
                }
            }
            return loaded;
        }

        private static String trimToWidth(Minecraft minecraft, String value, int maxWidth) {
            if (value == null || value.isBlank()) {
                return "";
            }
            if (minecraft.font.width(value) <= maxWidth) {
                return value;
            }
            String suffix = "...";
            int target = Math.max(0, maxWidth - minecraft.font.width(suffix));
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

        private static float clamp01(float value) {
            return Math.max(0.0F, Math.min(1.0F, value));
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static void drawFrame(GuiGraphicsExtractor gui, int x, int y, int width, int height, int fill, int border) {
            gui.fill(x, y, x + width, y + height, fill);
            gui.fill(x, y, x + width, y + 1, border);
            gui.fill(x, y + height - 1, x + width, y + height, border);
            gui.fill(x, y, x + 1, y + height, border);
            gui.fill(x + width - 1, y, x + width, y + height, border);
        }

        private record InventoryEntry(int primaryInventorySlot, ItemStack stack, int geneCount, String label, int copies) {
        }

        private record DraggingEntry(int inventorySlot, ItemStack stack, String label) {
        }

        private static final class GroupAccumulator {
            private final int primaryInventorySlot;
            private final ItemStack stack;
            private final int geneCount;
            private final String label;
            private int copies;

            private GroupAccumulator(int primaryInventorySlot, ItemStack stack, int geneCount, String label, int copies) {
                this.primaryInventorySlot = primaryInventorySlot;
                this.stack = stack;
                this.geneCount = geneCount;
                this.label = label;
                this.copies = copies;
            }
        }
    }

    public static class Serializer extends UiComponentSerializer<GeneCombinerPanelUiComponent> {
        @Override
        public MapCodec<GeneCombinerPanelUiComponent> codec() {
            return GeneCombinerPanelUiComponent.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiComponent, GeneCombinerPanelUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("Gene Combiner Panel")
                    .setDescription("Container-free gene combiner panel with inventory vial list and drag/drop input slots.");
        }
    }
}
