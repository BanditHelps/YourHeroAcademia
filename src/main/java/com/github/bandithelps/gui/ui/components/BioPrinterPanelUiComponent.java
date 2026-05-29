package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.bio_printer.ClientBioPrinterState;
import com.github.bandithelps.gene.Gene;
import com.github.bandithelps.items.GeneVialItem;
import com.github.bandithelps.network.BioPrinterImportPayload;
import com.github.bandithelps.network.BioPrinterPrintPayload;
import com.github.bandithelps.network.BioPrinterTransferPayload;
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

public class BioPrinterPanelUiComponent extends UiComponent {
    private static final int INPUT_SLOTS = 6;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 7;
    private static final int LIST_WIDTH = 126;
    private static final int LIST_HEADER_HEIGHT = 14;
    private static final int SLOT_SIZE = 26;
    private static final int SLOT_GAP_Y = 6;
    private static final int DNA_BRIDGE_WIDTH = 26;

    public static final MapCodec<BioPrinterPanelUiComponent> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
            Codec.INT.optionalFieldOf("frame_color", 0xFF79B8FF).forGetter(BioPrinterPanelUiComponent::getFrameColor),
            Codec.INT.optionalFieldOf("panel_color", 0xCC172231).forGetter(BioPrinterPanelUiComponent::getPanelColor),
            Codec.INT.optionalFieldOf("text_color", 0xFFE6F2FF).forGetter(BioPrinterPanelUiComponent::getTextColor),
            propertiesCodec(356, 192)
    ).apply(instance, BioPrinterPanelUiComponent::new));

    private final int frameColor;
    private final int panelColor;
    private final int textColor;

    public BioPrinterPanelUiComponent(int frameColor, int panelColor, int textColor, UiComponentProperties properties) {
        super(properties);
        this.frameColor = withOpaqueAlpha(frameColor);
        this.panelColor = withOpaqueAlpha(panelColor);
        this.textColor = withOpaqueAlpha(textColor);
    }

    @Override
    public UiComponentSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.BIO_PRINTER_PANEL;
    }

    @Override
    public AbstractWidget buildWidget(UiScreen screen, ScreenRectangle rectangle) {
        return new BioPrinterWidget(this, this.getX(rectangle), this.getY(rectangle), this.getWidth(), this.getHeight());
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

    private static final class BioPrinterWidget extends AbstractWidget {
        private final BioPrinterPanelUiComponent owner;
        private int scrollOffset = 0;
        private InventoryEntry pressedEntry;
        private DraggingEntry dragging;

        private BioPrinterWidget(BioPrinterPanelUiComponent owner, int x, int y, int width, int height) {
            super(x, y, width, height, Component.literal("Bio Printer"));
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

            ClientBioPrinterState.ClientData state = ClientBioPrinterState.getLatest();
            int machineX = listX + LIST_WIDTH + 10;
            int machineY = y + 8;
            int machineWidth = width - (machineX - x) - 6;
            int machineHeight = height - 16;
            drawMachinePanel(gui, minecraft, state, machineX, machineY, machineWidth, machineHeight, mouseX, mouseY);
            drawImportButton(gui, minecraft, state);

            if (this.dragging != null) {
                gui.item(this.dragging.stack(), mouseX - 8, mouseY - 8);
                gui.text(minecraft.font, trimToWidth(minecraft, this.dragging.label(), 110), mouseX + 10, mouseY - 2, 0xFFD5E8FF, false);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            int mouseX = (int) event.x();
            int mouseY = (int) event.y();
            BlockPos pos = ClientBioPrinterState.getLatestPos();
            if (pos == null) {
                return false;
            }
            ClientBioPrinterState.ClientData state = ClientBioPrinterState.getLatest();
            boolean awaitingExtraction = state != null && state.awaitingInjectorExtraction();
            boolean processing = state != null && state.processing();

            if (isInsideImportButton(mouseX, mouseY)) {
                if (!processing && !awaitingExtraction) {
                    ClientPacketDistributor.sendToServer(new BioPrinterImportPayload(pos));
                    clickSound();
                }
                return true;
            }
            if (isInsidePrintButton(mouseX, mouseY)) {
                if (!processing && !awaitingExtraction) {
                    ClientPacketDistributor.sendToServer(new BioPrinterPrintPayload(pos));
                    clickSound();
                }
                return true;
            }

            List<InventoryEntry> entries = getInventoryEntries(Minecraft.getInstance());
            InventoryEntry clickedEntry = getInventoryEntryAt(entries, mouseX, mouseY);
            if (clickedEntry != null && event.button() == 0 && !awaitingExtraction) {
                this.pressedEntry = clickedEntry;
                this.dragging = null;
                return true;
            }

            int slot = getMachineSlotAt(mouseX, mouseY);
            if (slot >= 0) {
                boolean[] clearable = state == null ? new boolean[0] : state.clearableSlots();
                if (!awaitingExtraction && slot < clearable.length && clearable[slot]) {
                    ClientPacketDistributor.sendToServer(new BioPrinterTransferPayload(pos, -1, slot));
                    clickSound();
                }
                return true;
            }

            this.pressedEntry = null;
            this.dragging = null;
            return false;
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
            if (event.button() != 0 || this.pressedEntry == null) {
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
            if (event.button() != 0 || this.dragging == null) {
                this.pressedEntry = null;
                return false;
            }
            int slot = getMachineSlotAt((int) event.x(), (int) event.y());
            BlockPos pos = ClientBioPrinterState.getLatestPos();
            ClientBioPrinterState.ClientData state = ClientBioPrinterState.getLatest();
            boolean hasImportedBase = state != null && state.sourceUuid() != null && !state.sourceUuid().isBlank();
            boolean awaitingExtraction = state != null && state.awaitingInjectorExtraction();
            if (slot >= 0 && pos != null && hasImportedBase && !awaitingExtraction) {
                ClientPacketDistributor.sendToServer(new BioPrinterTransferPayload(pos, this.dragging.inventorySlot(), slot));
                clickSound();
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
            narrationElementOutput.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, this.getMessage());
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
            gui.text(minecraft.font, "Gene Vials", x + 4, y + 3, this.owner.textColor, false);
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
        }

        private void drawMachinePanel(
                GuiGraphicsExtractor gui,
                Minecraft minecraft,
                ClientBioPrinterState.ClientData state,
                int x,
                int y,
                int width,
                int height,
                int mouseX,
                int mouseY
        ) {
            drawFrame(gui, x, y, width, height, 0xAA111A26, this.owner.frameColor);
            gui.text(minecraft.font, "Bio Printer", x + 4, y + 3, this.owner.textColor, false);

            String[] labels = state == null ? new String[0] : state.genomeSlotLabels();
            String[] tooltips = state == null ? new String[0] : state.genomeSlotTooltips();
            boolean[] clearable = state == null ? new boolean[0] : state.clearableSlots();
            boolean hasImportedBase = state != null && state.sourceUuid() != null && !state.sourceUuid().isBlank();

            SlotPreview preview = resolvePreview(mouseX, mouseY);
            drawDnaBridge(gui, minecraft, x, y, width);
            for (int i = 0; i < INPUT_SLOTS; i++) {
                int slotX = getMachineSlotX(i, x, width);
                int slotY = getMachineSlotY(i, y);
                boolean filled = i < labels.length && labels[i] != null && !labels[i].isBlank();
                boolean canUndo = i < clearable.length && clearable[i];
                boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
                boolean previewTarget = preview != null && preview.targets()[i];
                boolean previewOverwrite = preview != null && preview.overwrites()[i];
                int border = hovered ? 0xFF9AD1FF : filled ? (canUndo ? 0xFFFFC670 : 0xFF74D2A8) : 0xFF49637D;
                int fill = 0xAA1B2A3B;
                if (previewTarget) {
                    fill = previewOverwrite ? 0xAA8A3C3C : 0xAA245A7A;
                    border = previewOverwrite ? 0xFFFF9A66 : 0xFF8CD7FF;
                }
                drawFrame(gui, slotX, slotY, SLOT_SIZE, SLOT_SIZE, fill, border);
                if (filled) {
                    gui.item(new ItemStack(YourHeroAcademia.GENE_VIAL.get()), slotX + 5, slotY + 5);
                } else {
                    gui.text(minecraft.font, "-", slotX + (SLOT_SIZE - minecraft.font.width("-")) / 2, slotY + 9, 0xFF7E8EA3, false);
                }
                gui.text(minecraft.font, Integer.toString(i + 1), slotX + SLOT_SIZE - 6, slotY + 2, 0x99CFE8FF, false);
                if (hovered) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.literal("Genome Slot " + (i + 1)));
                    if (filled) {
                        if (i < labels.length && labels[i] != null && !labels[i].isBlank()) {
                            tooltip.add(Component.literal(labels[i]).withStyle(ChatFormatting.AQUA));
                        }
                        if (i < tooltips.length && tooltips[i] != null && !tooltips[i].isBlank()) {
                            for (String line : tooltips[i].split("\\n")) {
                                if (!line.isBlank()) {
                                    tooltip.add(Component.literal(line).withStyle(line.startsWith("Side effect") ? ChatFormatting.RED : ChatFormatting.GRAY));
                                }
                            }
                        }
                        if (canUndo) {
                            tooltip.add(Component.literal("Click to undo vial change").withStyle(ChatFormatting.DARK_GRAY));
                        } else {
                            tooltip.add(Component.literal("Imported gene (locked)").withStyle(ChatFormatting.DARK_GRAY));
                        }
                    } else {
                        tooltip.add(Component.literal("Drop vial segment here").withStyle(ChatFormatting.GRAY));
                    }
                    if (previewOverwrite) {
                        tooltip.add(Component.literal("Will be overwritten").withStyle(ChatFormatting.GOLD));
                    }
                    gui.setTooltipForNextFrame(minecraft.font, tooltip.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
                }
            }

            boolean processing = state != null && state.processing();
            boolean awaitingExtraction = state != null && state.awaitingInjectorExtraction();
            float progress = state == null || state.processingTotalTicks() <= 0
                    ? 0.0F
                    : clamp01((float) state.processingProgress() / (float) state.processingTotalTicks());
            if (awaitingExtraction) {
                progress = 1.0F;
            }

            int progressX = x + 10;
            int progressY = y + height - 42;
            int progressW = width - 20;
            drawFrame(gui, progressX, progressY, progressW, 10, 0xAA1B2A3B, 0xFF43617F);
            gui.fill(progressX + 1, progressY + 1, progressX + 1 + Math.round((progressW - 2) * progress), progressY + 9, 0xFF7BD6FF);

            String status = awaitingExtraction
                    ? "Ready - shift+right click with DNA Injector"
                    : processing
                    ? "Printing DNA..."
                    : !hasImportedBase
                    ? "Import DNA to start editing"
                    : "Import DNA, then print";
            gui.text(minecraft.font, trimToWidth(minecraft, status, width - 20), progressX, progressY - 10, 0xFFB7D9FF, false);
            String etaText = "ETA: --";
            if (awaitingExtraction) {
                etaText = "";
            } else if (processing && state != null) {
                int remainingTicks = Math.max(0, state.processingTotalTicks() - state.processingProgress());
                int remainingSeconds = (remainingTicks + 19) / 20;
                etaText = "ETA: " + remainingSeconds + "s";
            }
            int etaX = progressX + progressW - minecraft.font.width(etaText);
            gui.text(minecraft.font, etaText, etaX, progressY - 10, 0xFF9DD4FF, false);

            boolean canPrint = !processing && !awaitingExtraction;
            int printX = getPrintButtonX();
            int printY = getPrintButtonY();
            int printW = getPrintButtonWidth();
            int printH = getPrintButtonHeight();
            drawFrame(gui, printX, printY, printW, printH, canPrint ? 0xAA1C3D54 : 0xAA1C222B, canPrint ? 0xFF79B8FF : 0xFF41546B);
            String printText = processing ? "Working" : "Print";
            gui.text(minecraft.font, printText, printX + (printW - minecraft.font.width(printText)) / 2, printY + 5, canPrint ? 0xFFE6F2FF : 0xFF7A8A9D, false);

            String src = state == null || state.sourceName() == null || state.sourceName().isBlank() ? "None" : state.sourceName();
            gui.text(minecraft.font, trimToWidth(minecraft, "Source: " + src, width - 20), x + 10, progressY - 22, 0xFFAED6F3, false);
        }

        private void drawDnaBridge(GuiGraphicsExtractor gui, Minecraft minecraft, int panelX, int panelY, int panelWidth) {
            int centerX = panelX + (panelWidth / 2);
            int startY = panelY + 22;
            int totalHeight = (SLOT_SIZE * 3) + (SLOT_GAP_Y * 2);
            float time = minecraft.level != null ? minecraft.level.getGameTime() : ((float) System.currentTimeMillis() / 50L);
            for (int i = 0; i < totalHeight; i += 2) {
                float angle = (i * 0.22F) + (time * 0.12F);
                int spread = 4 + Math.round((float) Math.sin(angle) * 5.0F);
                int leftX = centerX - spread;
                int rightX = centerX + spread;
                int rowY = startY + i;
                gui.fill(leftX, rowY, leftX + 2, rowY + 2, 0xFF5EA5C8);
                gui.fill(rightX, rowY, rightX + 2, rowY + 2, 0xFF8CE0FF);
                if ((i / 2) % 2 == 0) {
                    gui.fill(leftX + 2, rowY, rightX, rowY + 1, 0xAA9EE4FF);
                }
            }
        }

        private boolean isInsideImportButton(int mouseX, int mouseY) {
            int x = getImportButtonX();
            int y = getImportButtonY();
            return mouseX >= x && mouseX < x + getImportButtonWidth() && mouseY >= y && mouseY < y + getImportButtonHeight();
        }

        private boolean isInsidePrintButton(int mouseX, int mouseY) {
            int x = getPrintButtonX();
            int y = getPrintButtonY();
            return mouseX >= x && mouseX < x + getPrintButtonWidth() && mouseY >= y && mouseY < y + getPrintButtonHeight();
        }

        private int getMachineSlotAt(int mouseX, int mouseY) {
            for (int i = 0; i < INPUT_SLOTS; i++) {
                int panelX = this.getX() + 6 + LIST_WIDTH + 10;
                int panelY = this.getY() + 8;
                int panelW = this.getWidth() - (panelX - this.getX()) - 6;
                int slotX = getMachineSlotX(i, panelX, panelW);
                int slotY = getMachineSlotY(i, panelY);
                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    return i;
                }
            }
            return -1;
        }

        private SlotPreview resolvePreview(int mouseX, int mouseY) {
            ClientBioPrinterState.ClientData state = ClientBioPrinterState.getLatest();
            boolean hasImportedBase = state != null && state.sourceUuid() != null && !state.sourceUuid().isBlank();
            if (!hasImportedBase) {
                return null;
            }
            ItemStack previewStack = ItemStack.EMPTY;
            if (this.dragging != null) {
                previewStack = this.dragging.stack();
            } else if (this.pressedEntry != null) {
                previewStack = this.pressedEntry.stack();
            }
            if (previewStack.isEmpty() || previewStack.getItem() != YourHeroAcademia.GENE_VIAL.get()) {
                return null;
            }
            List<String> genes = GeneVialItem.getStoredGeneList(previewStack);
            int slotLength = Math.max(1, Math.min(3, genes.size()));
            int hoveredSlot = getMachineSlotAt(mouseX, mouseY);
            if (hoveredSlot < 0) {
                return null;
            }
            int startSlot = normalizePlacementStart(slotLength, hoveredSlot);
            if (startSlot < 0 || !isValidPlacementStart(slotLength, startSlot) || startSlot + slotLength > INPUT_SLOTS) {
                return null;
            }
            String[] labels = ClientBioPrinterState.getLatest() == null ? new String[0] : ClientBioPrinterState.getLatest().genomeSlotLabels();
            boolean[] targets = new boolean[INPUT_SLOTS];
            boolean[] overwrites = new boolean[INPUT_SLOTS];
            for (int i = 0; i < slotLength; i++) {
                int slot = startSlot + i;
                targets[slot] = true;
                boolean incomingFilled = i < genes.size() && genes.get(i) != null && !genes.get(i).isBlank();
                boolean existingFilled = slot < labels.length && labels[slot] != null && !labels[slot].isBlank();
                overwrites[slot] = incomingFilled && existingFilled;
            }
            return new SlotPreview(targets, overwrites);
        }

        private static boolean isValidPlacementStart(int slotLength, int startSlot) {
            if (slotLength == 3) {
                return startSlot == 0 || startSlot == 3;
            }
            if (slotLength == 2) {
                return startSlot == 0 || startSlot == 1 || startSlot == 3 || startSlot == 4;
            }
            return slotLength == 1;
        }

        private static int normalizePlacementStart(int slotLength, int hoveredSlot) {
            if (hoveredSlot < 0 || hoveredSlot >= INPUT_SLOTS) {
                return -1;
            }
            if (slotLength == 3) {
                return hoveredSlot < 3 ? 0 : 3;
            }
            if (slotLength == 2) {
                return switch (hoveredSlot) {
                    case 0 -> 0;
                    case 1, 2 -> 1;
                    case 3 -> 3;
                    case 4, 5 -> 4;
                    default -> -1;
                };
            }
            return hoveredSlot;
        }

        private static int getMachineSlotX(int slotIndex, int panelX, int panelWidth) {
            int centerX = panelX + (panelWidth / 2);
            int leftX = centerX - (DNA_BRIDGE_WIDTH / 2) - SLOT_SIZE - 8;
            int rightX = centerX + (DNA_BRIDGE_WIDTH / 2) + 8;
            return slotIndex < 3 ? leftX : rightX;
        }

        private static int getMachineSlotY(int slotIndex, int panelY) {
            return panelY + 22 + ((slotIndex % 3) * (SLOT_SIZE + SLOT_GAP_Y));
        }

        private void drawImportButton(GuiGraphicsExtractor gui, Minecraft minecraft, ClientBioPrinterState.ClientData state) {
            boolean processing = state != null && state.processing();
            boolean awaitingExtraction = state != null && state.awaitingInjectorExtraction();
            boolean disabled = processing || awaitingExtraction;
            int x = getImportButtonX();
            int y = getImportButtonY();
            int w = getImportButtonWidth();
            int h = getImportButtonHeight();
            drawFrame(gui, x, y, w, h, disabled ? 0xAA1C222B : 0xAA1C3D54, disabled ? 0xFF41546B : 0xFF79B8FF);
            String text = "Import DNA";
            gui.text(minecraft.font, text, x + (w - minecraft.font.width(text)) / 2, y + 5, disabled ? 0xFF7A8A9D : 0xFFE6F2FF, false);
        }

        private int getImportButtonX() {
            return this.getX() + 6 + 16;
        }

        private int getImportButtonY() {
            int listY = this.getY() + 8;
            int listHeight = LIST_HEADER_HEIGHT + (VISIBLE_ROWS * ROW_HEIGHT) + 6;
            return listY + listHeight + 4;
        }

        private int getImportButtonWidth() {
            return LIST_WIDTH - 32;
        }

        private int getImportButtonHeight() {
            return 18;
        }

        private int getPrintButtonX() {
            int panelX = this.getX() + 6 + LIST_WIDTH + 10;
            int panelW = this.getWidth() - (panelX - this.getX()) - 6;
            return panelX + panelW - 82;
        }

        private int getPrintButtonY() {
            int panelY = this.getY() + 8;
            int panelH = this.getHeight() - 16;
            int progressY = panelY + panelH - 42;
            return progressY + 14;
        }

        private int getPrintButtonWidth() {
            return 70;
        }

        private int getPrintButtonHeight() {
            return 18;
        }

        private InventoryEntry getInventoryEntryAt(List<InventoryEntry> entries, int mouseX, int mouseY) {
            int x = this.getX() + 6;
            int y = this.getY() + 8 + LIST_HEADER_HEIGHT;
            int visible = Math.min(VISIBLE_ROWS, Math.max(0, entries.size() - this.scrollOffset));
            for (int i = 0; i < visible; i++) {
                int rowY = y + (i * ROW_HEIGHT);
                if (mouseX >= x + 2 && mouseX < x + LIST_WIDTH - 2 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
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
                entries.add(new InventoryEntry(group.primaryInventorySlot, group.stack, group.geneCount, group.label, group.copies));
            }
            return entries;
        }

        private String buildGroupingKey(ItemStack stack) {
            List<String> genes = GeneVialItem.getStoredGeneList(stack);
            String sourceName = GeneVialItem.getSourceName(stack);
            String sourceUuid = GeneVialItem.getSourceUuid(stack);
            return String.join("|", genes) + "#" + sourceName + "#" + sourceUuid;
        }

        private String resolveVialLabel(ItemStack stack, int geneCount) {
            List<String> genes = GeneVialItem.getStoredGeneList(stack);
            for (String raw : genes) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                Gene parsed = GeneUtil.parseGene(raw);
                if (parsed != null) {
                    return parsed.getName() + " +" + Math.max(0, geneCount - 1);
                }
            }
            return geneCount + " slot vial";
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
                    tooltip.add(Component.literal("[" + index + "] Empty").withStyle(ChatFormatting.DARK_GRAY));
                    index++;
                    continue;
                }
                Gene parsed = GeneUtil.parseGene(raw);
                if (parsed == null) {
                    continue;
                }
                tooltip.add(Component.literal("[" + index + "] " + parsed.getName()
                                + " (" + parsed.getType().getId() + ", q:" + parsed.getQuality() + ")")
                        .withStyle(ChatFormatting.YELLOW));
                if (parsed.hasSideEffects()) {
                    parsed.getSideEffects().forEach(sideEffect ->
                            tooltip.add(Component.literal("   - Side effect: " + sideEffect.getDisplayName()).withStyle(ChatFormatting.RED)));
                }
                index++;
            }
            tooltip.add(Component.literal("Filled slots: " + geneCount).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal("Drop by first slot alignment").withStyle(ChatFormatting.GRAY));
            return tooltip;
        }

        private void clickSound() {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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

        private record SlotPreview(boolean[] targets, boolean[] overwrites) {
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

    public static class Serializer extends UiComponentSerializer<BioPrinterPanelUiComponent> {
        @Override
        public MapCodec<BioPrinterPanelUiComponent> codec() {
            return BioPrinterPanelUiComponent.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiComponent, BioPrinterPanelUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("Bio Printer Panel")
                    .setDescription("Bio printer panel with DNA import, genome editing, and print controls.");
        }
    }
}
