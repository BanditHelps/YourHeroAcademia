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
import java.util.List;
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
        private DraggingEntry dragging;

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
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            int mouseX = (int) event.x();
            int mouseY = (int) event.y();
            List<InventoryEntry> entries = getInventoryEntries(Minecraft.getInstance());
            InventoryEntry clickedEntry = getInventoryEntryAt(entries, mouseX, mouseY);
            if (clickedEntry != null) {
                this.dragging = new DraggingEntry(clickedEntry.inventorySlot(), clickedEntry.stack().copy(), clickedEntry.label());
                clickSound();
                return true;
            }

            int slot = getMachineSlotAt(mouseX, mouseY);
            if (slot >= 0) {
                BlockPos pos = ClientGeneCombinerState.getLatestPos();
                if (pos == null) {
                    this.dragging = null;
                    return true;
                }
                if (this.dragging != null) {
                    ClientPacketDistributor.sendToServer(new GeneCombinerTransferPayload(pos, this.dragging.inventorySlot(), slot));
                    this.dragging = null;
                    clickSound();
                    return true;
                }
                ClientPacketDistributor.sendToServer(new GeneCombinerTransferPayload(pos, -1, slot));
                clickSound();
                return true;
            }

            if (isInsideCombineButton(mouseX, mouseY)) {
                BlockPos pos = ClientGeneCombinerState.getLatestPos();
                if (pos != null) {
                    ClientPacketDistributor.sendToServer(new GeneCombinerStartPayload(pos));
                    clickSound();
                }
                return true;
            }

            this.dragging = null;
            return false;
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
                String label = trimToWidth(minecraft, entry.label(), width - 44);
                gui.text(minecraft.font, label, x + 24, rowY + 4, 0xFFDCEFFF, false);
                gui.text(minecraft.font, "x" + entry.geneCount(), x + width - 22, rowY + 4, 0xFF9FD1A8, false);

                if (hovered) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.literal(entry.label()));
                    tooltip.add(Component.literal("Genes: " + entry.geneCount()));
                    tooltip.add(Component.literal("Inv slot: " + entry.inventorySlot()));
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
            for (int i = 0; i < INPUT_SLOTS; i++) {
                int slotX = slotsX + (i % 2) * (SLOT_SIZE + 10);
                int slotY = slotsY + (i / 2) * (SLOT_SIZE + 10);
                boolean filled = i < counts.length && counts[i] > 0;
                boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
                int border = hovered ? 0xFF9AD1FF : filled ? 0xFF87C47B : 0xFF49637D;
                drawFrame(gui, slotX, slotY, SLOT_SIZE, SLOT_SIZE, 0xAA1B2A3B, border);
                String text = filled ? Integer.toString(counts[i]) : "-";
                int textColor = filled ? 0xFFAEEFBF : 0xFF7E8EA3;
                gui.text(minecraft.font, text, slotX + (SLOT_SIZE - minecraft.font.width(text)) / 2, slotY + 13, textColor, false);
                if (hovered) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.literal("Input Slot " + (i + 1)));
                    if (filled) {
                        String label = i < labels.length ? labels[i] : "";
                        tooltip.add(Component.literal(label.isBlank() ? counts[i] + " genes" : label));
                        tooltip.add(Component.literal("Click to clear slot"));
                    } else {
                        tooltip.add(Component.literal("Drop a vial here"));
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

            int machineCenterX = x + width - 78;
            int machineCenterY = y + 52;
            drawMixerAnimation(gui, machineCenterX, machineCenterY, processing, progress, minecraft);

            int progressX = x + 10;
            int progressY = y + height - 44;
            int progressW = width - 20;
            drawFrame(gui, progressX, progressY, progressW, 10, 0xAA1B2A3B, 0xFF43617F);
            gui.fill(progressX + 1, progressY + 1, progressX + 1 + Math.round((progressW - 2) * progress), progressY + 9, 0xFF7BD6FF);

            String status = processing ? "Combining..." : "Idle";
            gui.text(minecraft.font, status, progressX, progressY - 10, 0xFFB7D9FF, false);

            int buttonX = x + width - 80;
            int buttonY = y + height - 28;
            boolean canCombine = !processing && hasAnyInput(counts);
            drawFrame(gui, buttonX, buttonY, 70, 18, canCombine ? 0xAA1C3D54 : 0xAA1C222B, canCombine ? 0xFF79B8FF : 0xFF41546B);
            String buttonLabel = processing ? "Working" : "Combine";
            int buttonTextColor = canCombine ? 0xFFE6F2FF : 0xFF7A8A9D;
            gui.text(minecraft.font, buttonLabel, buttonX + (70 - minecraft.font.width(buttonLabel)) / 2, buttonY + 5, buttonTextColor, false);

            String outputKind = state == null ? "empty" : state.outputKind();
            if (!"empty".equals(outputKind)) {
                String result = state.outputLabel();
                if (result == null || result.isBlank()) {
                    result = "slop".equals(outputKind) ? "Genetic Slop" : "Gene Vial";
                }
                gui.text(minecraft.font, "Result: " + trimToWidth(minecraft, result, width - 20), x + 10, y + height - 16, 0xFFAEEFBF, false);
            }
        }

        private void drawMixerAnimation(GuiGraphicsExtractor gui, int centerX, int centerY, boolean active, float progress, Minecraft minecraft) {
            float time = minecraft.level != null ? minecraft.level.getGameTime() : (System.currentTimeMillis() / 50L);
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
            int y = this.getY() + this.getHeight() - 28;
            return mouseX >= x && mouseX < x + 70 && mouseY >= y && mouseY < y + 18;
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
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.isEmpty() || stack.getItem() != YourHeroAcademia.GENE_VIAL.get()) {
                    continue;
                }
                int geneCount = GeneVialItem.getGeneCount(stack);
                String label = resolveVialLabel(stack, geneCount);
                entries.add(new InventoryEntry(slot, stack, geneCount, label));
            }
            return entries;
        }

        private String resolveVialLabel(ItemStack stack, int geneCount) {
            List<String> genes = GeneVialItem.getStoredGeneList(stack);
            for (String raw : genes) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                Gene parsed = GeneUtil.parseGene(raw);
                if (parsed != null) {
                    return parsed.getName();
                }
            }
            String source = GeneVialItem.getSourceName(stack);
            if (source != null && !source.isBlank()) {
                return source;
            }
            return geneCount + " gene" + (geneCount == 1 ? "" : "s");
        }

        private void clickSound() {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }

        private static boolean hasAnyInput(int[] counts) {
            if (counts == null) {
                return false;
            }
            for (int count : counts) {
                if (count > 0) {
                    return true;
                }
            }
            return false;
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

        private record InventoryEntry(int inventorySlot, ItemStack stack, int geneCount, String label) {
        }

        private record DraggingEntry(int inventorySlot, ItemStack stack, String label) {
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
