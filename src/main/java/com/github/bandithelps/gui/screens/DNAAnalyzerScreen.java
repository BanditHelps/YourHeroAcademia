package com.github.bandithelps.gui.screens;

import com.github.bandithelps.attributes.IntelligenceAttributes;
import com.github.bandithelps.client.dna_analyzer.ClientDNAAnalyzerState;
import com.github.bandithelps.gene.Gene;
import com.github.bandithelps.network.DNAAnalyzerExtractPayload;
import com.github.bandithelps.network.DNAAnalyzerRenamePayload;
import com.github.bandithelps.utils.gene.GeneUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class DNAAnalyzerScreen extends Screen {
    private static final int GENE_SLOT_SIZE = 36;

    private final BlockPos analyzerPos;
    private EditBox renameBox;
    private Button renameButton;
    private Button modeButton;
    private Button extractLeftButton;
    private Button extractRightButton;
    private Button extractSelectedButton;
    private int selectedSlot = -1;
    private boolean extractionMode = false;

    public DNAAnalyzerScreen(BlockPos analyzerPos) {
        super(Component.literal("DNA Analyzer"));
        this.analyzerPos = analyzerPos;
    }

    @Override
    protected void init() {
        super.init();
        int panelX = this.width / 2 - 120;
        int panelY = this.height / 2 - 90;

        this.renameBox = new EditBox(this.font, panelX + 12, panelY + 142, 150, 20, Component.literal("Gene name"));
        this.renameBox.setMaxLength(32);
        this.addRenderableWidget(this.renameBox);

        this.renameButton = this.addRenderableWidget(Button.builder(Component.literal("Rename"), btn -> renameSelectedGene())
                .bounds(panelX + 168, panelY + 142, 60, 20)
                .build());

        this.modeButton = this.addRenderableWidget(Button.builder(Component.literal("Extraction: OFF"), btn -> {
                    extractionMode = !extractionMode;
                    updateButtonState();
                }).bounds(panelX + 12, panelY + 168, 100, 20)
                .build());

        this.extractLeftButton = this.addRenderableWidget(Button.builder(Component.literal("Extract Left"), btn -> extractLeft())
                .bounds(panelX + 118, panelY + 168, 66, 20)
                .build());
        this.extractRightButton = this.addRenderableWidget(Button.builder(Component.literal("Extract Right"), btn -> extractRight())
                .bounds(panelX + 190, panelY + 168, 66, 20)
                .build());
        this.extractSelectedButton = this.addRenderableWidget(Button.builder(Component.literal("Extract Gene"), btn -> extractSelected())
                .bounds(panelX + 118, panelY + 192, 138, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(panelX + 190, panelY + 8, 66, 20)
                .build());

        updateButtonState();
    }

    @Override
    public void tick() {
        super.tick();
        updateButtonState();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int panelX = this.width / 2 - 120;
        int panelY = this.height / 2 - 90;
        graphics.fill(panelX, panelY, panelX + 260, panelY + 216, 0xCC121212);
        graphics.fill(panelX + 1, panelY + 1, panelX + 259, panelY + 215, 0xCC1E1E1E);
        graphics.centeredText(this.font, Component.literal("DNA Analyzer"), this.width / 2, panelY + 12, 0xFFFFFF);

        ClientDNAAnalyzerState.ClientData data = ClientDNAAnalyzerState.get(this.analyzerPos);
        if (data == null) {
            graphics.text(this.font, Component.literal("Waiting for analyzer data..."), panelX + 12, panelY + 30, 0xBBBBBB, false);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }

        drawDNAHelix(graphics, panelX + 12, panelY + 36);
        drawSlots(graphics, data, panelX + 98, panelY + 34, mouseX, mouseY);

        graphics.text(this.font, Component.literal("Source: " + data.sourceName()), panelX + 12, panelY + 124, 0x9FDFFF, false);
        graphics.text(this.font, Component.literal("Genes: " + countFilled(data.geneSlots())), panelX + 12, panelY + 134, 0xFFE28A, false);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int panelX = this.width / 2 - 120;
        int panelY = this.height / 2 - 90;
        ClientDNAAnalyzerState.ClientData data = ClientDNAAnalyzerState.get(this.analyzerPos);
        if (data != null) {
            for (int slot = 0; slot < 6; slot++) {
                int x = panelX + 98 + (slot % 2) * 50;
                int y = panelY + 34 + (slot / 2) * 38;
                if (event.x() >= x && event.x() < x + GENE_SLOT_SIZE && event.y() >= y && event.y() < y + GENE_SLOT_SIZE) {
                    if (!data.geneSlots()[slot].isEmpty()) {
                        selectedSlot = slot;
                        Gene gene = GeneUtil.parseGene(data.geneSlots()[slot]);
                        if (gene != null) {
                            renameBox.setValue(gene.getName());
                        }
                        updateButtonState();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawDNAHelix(GuiGraphicsExtractor graphics, int startX, int startY) {
        for (int i = 0; i < 40; i++) {
            int y = startY + i * 2;
            int leftX = startX + (int) (8.0D * Math.sin(i * 0.5D));
            int rightX = startX + 34 - (int) (8.0D * Math.sin(i * 0.5D));
            graphics.fill(leftX, y, leftX + 2, y + 2, 0xFF79B8FF);
            graphics.fill(rightX, y, rightX + 2, y + 2, 0xFF79B8FF);
            if ((i & 1) == 0) {
                graphics.fill(leftX + 2, y, rightX, y + 1, 0xAA90D6FF);
            }
        }
    }

    private void drawSlots(GuiGraphicsExtractor graphics, ClientDNAAnalyzerState.ClientData data, int startX, int startY, int mouseX, int mouseY) {
        for (int i = 0; i < 6; i++) {
            int x = startX + (i % 2) * 50;
            int y = startY + (i / 2) * 38;
            boolean filled = i < data.geneSlots().length && data.geneSlots()[i] != null && !data.geneSlots()[i].isEmpty();
            int borderColor = selectedSlot == i ? 0xFFFFC857 : 0xFF555555;
            graphics.fill(x, y, x + GENE_SLOT_SIZE, y + GENE_SLOT_SIZE, 0xFF1B1B1B);
            graphics.fill(x, y, x + GENE_SLOT_SIZE, y + 1, borderColor);
            graphics.fill(x, y + GENE_SLOT_SIZE - 1, x + GENE_SLOT_SIZE, y + GENE_SLOT_SIZE, borderColor);
            graphics.fill(x, y, x + 1, y + GENE_SLOT_SIZE, borderColor);
            graphics.fill(x + GENE_SLOT_SIZE - 1, y, x + GENE_SLOT_SIZE, y + GENE_SLOT_SIZE, borderColor);
            if (filled) {
                graphics.centeredText(this.font, Component.literal("Gene"), x + GENE_SLOT_SIZE / 2, y + 12, 0xA6E3A1);
            } else {
                graphics.centeredText(this.font, Component.literal("Empty"), x + GENE_SLOT_SIZE / 2, y + 12, 0x777777);
            }

            boolean hovered = mouseX >= x && mouseX < x + GENE_SLOT_SIZE && mouseY >= y && mouseY < y + GENE_SLOT_SIZE;
            if (hovered && filled) {
                drawGeneTooltip(graphics, mouseX, mouseY, data.geneSlots()[i], i);
            }
        }
    }

    private void drawGeneTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, String rawGene, int slotIndex) {
        Gene gene = GeneUtil.parseGene(rawGene);
        if (gene == null) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Slot " + (slotIndex + 1)));
        lines.add(Component.literal("Name: " + gene.getName()));
        lines.add(Component.literal("Category: " + gene.getCategory()));
        lines.add(Component.literal("Quality: " + gene.getQuality() + "/100"));
        lines.add(Component.literal("Type: " + gene.getType().getId()));
        List<ClientTooltipComponent> tooltipLines = lines.stream()
                .flatMap(line -> this.font.split(line, 260).stream())
                .map(ClientTooltipComponent::create)
                .toList();
        graphics.tooltip(this.font, tooltipLines, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, (Identifier) null);
    }

    private void renameSelectedGene() {
        if (selectedSlot < 0) {
            return;
        }
        String newName = this.renameBox.getValue();
        if (newName == null || newName.isBlank()) {
            return;
        }
        ClientPacketDistributor.sendToServer(new DNAAnalyzerRenamePayload(analyzerPos, selectedSlot, newName.trim()));
    }

    private void extractLeft() {
        ClientPacketDistributor.sendToServer(new DNAAnalyzerExtractPayload(analyzerPos, new int[]{0, 1, 2}));
        onClose();
    }

    private void extractRight() {
        ClientPacketDistributor.sendToServer(new DNAAnalyzerExtractPayload(analyzerPos, new int[]{3, 4, 5}));
        onClose();
    }

    private void extractSelected() {
        if (selectedSlot < 0) {
            return;
        }
        ClientPacketDistributor.sendToServer(new DNAAnalyzerExtractPayload(analyzerPos, new int[]{selectedSlot}));
        onClose();
    }

    private void updateButtonState() {
        modeButton.setMessage(Component.literal("Extraction: " + (extractionMode ? "ON" : "OFF")));
        ClientDNAAnalyzerState.ClientData data = ClientDNAAnalyzerState.get(this.analyzerPos);
        boolean hasData = data != null && data.analyzed();
        int extractCount = getAllowedExtractCount();

        renameButton.active = hasData && selectedSlot >= 0;
        renameBox.setEditable(hasData && selectedSlot >= 0);

        boolean extractionEnabled = extractionMode && hasData;
        extractLeftButton.visible = extractionEnabled && extractCount > 1;
        extractRightButton.visible = extractionEnabled && extractCount > 1;
        extractSelectedButton.visible = extractionEnabled && extractCount == 1;
        extractSelectedButton.active = extractionEnabled && extractCount == 1 && selectedSlot >= 0;
    }

    private int getAllowedExtractCount() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return 3;
        }
        double intelligence = this.minecraft.player.getAttributeValue(IntelligenceAttributes.INTELLIGENCE);
        if (intelligence >= 60.0D) {
            return 1;
        }
        if (intelligence >= 25.0D) {
            return 2;
        }
        return 3;
    }

    private int countFilled(String[] slots) {
        int count = 0;
        for (String slot : slots) {
            if (slot != null && !slot.isEmpty()) {
                count++;
            }
        }
        return count;
    }
}
