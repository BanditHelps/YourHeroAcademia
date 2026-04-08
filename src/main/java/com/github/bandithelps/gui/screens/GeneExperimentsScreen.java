package com.github.bandithelps.gui.screens;

import com.github.bandithelps.gui.GeneUiStyle;
import com.github.bandithelps.gui.gene.data.FakeGeneCatalog;
import com.github.bandithelps.gui.gene.data.FakeGraphFactory;
import com.github.bandithelps.gui.gene.data.FakeMachineCatalog;
import com.github.bandithelps.gui.gene.data.NodeTemplate;
import com.github.bandithelps.gui.gene.interaction.ConnectionDraft;
import com.github.bandithelps.gui.gene.interaction.GraphInputController;
import com.github.bandithelps.gui.gene.interaction.GraphViewport;
import com.github.bandithelps.gui.gene.interaction.SelectionState;
import com.github.bandithelps.gui.gene.model.GeneGraphDocument;
import com.github.bandithelps.gui.gene.model.GeneNode;
import com.github.bandithelps.gui.gene.model.GenePort;
import com.github.bandithelps.gui.gene.model.NodeKind;
import com.github.bandithelps.gui.gene.model.PortDirection;
import com.github.bandithelps.gui.gene.render.GraphCanvasRenderer;
import com.github.bandithelps.gui.gene.render.GraphHitTest;
import com.github.bandithelps.gui.ui.UiDropdown;
import com.github.bandithelps.gui.ui.UiIconButton;
import com.github.bandithelps.gui.ui.UiListItem;
import com.github.bandithelps.gui.ui.UiRect;
import com.github.bandithelps.gui.ui.UiRoot;
import com.github.bandithelps.gui.ui.UiTextButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class GeneExperimentsScreen extends Screen {
    private static final boolean HIDE_USED_GENES = false;
    private static final int FRAME_WIDTH = 364;
    private static final int FRAME_HEIGHT = 224;
    private static final int HEADER_HEIGHT = 18;
    private static final int OUTER_PADDING = 8;
    private static final int LEFT_PANEL_WIDTH = 76;
    private static final int PANEL_GAP = 6;
    private static final int ENTRY_HEIGHT = 16;
    private static final int TAB_HEIGHT = 14;
    private static final int DROPDOWN_HEIGHT = 14;
    private static final int LIST_ROW_GAP = 3;

    private final List<NodeTemplate> machineTemplates = FakeMachineCatalog.entries();
    private final Map<String, List<NodeTemplate>> genesByCategory = FakeGeneCatalog.groupedByCategory();
    private final Map<String, List<GeneInventoryEntry>> geneInventoryByCategory = new LinkedHashMap<>();
    private final Set<String> consumedGeneInstanceIds = new HashSet<>();
    private final Map<String, String> geneInstanceByNodeId = new HashMap<>();
    private final Set<String> expandedGeneTemplateIds = new HashSet<>();
    private final List<String> geneCategories = new ArrayList<>();
    private final UiRoot uiRoot = new UiRoot();
    private final List<UiIconButton> topActionButtons = new ArrayList<>();
    private final List<UiListItem<NodeTemplate>> machineItems = new ArrayList<>();
    private final List<UiListItem<GeneListRow>> geneItems = new ArrayList<>();

    private final GeneGraphDocument graph = FakeGraphFactory.createEmpty();
    private final GraphViewport viewport = new GraphViewport();
    private final SelectionState selection = new SelectionState();
    private final ConnectionDraft connectionDraft = new ConnectionDraft();
    private final GraphInputController inputController = new GraphInputController(this.graph, this.viewport, this.selection, this.connectionDraft);
    private final GraphCanvasRenderer canvasRenderer = new GraphCanvasRenderer();

    private int leftPanelX;
    private int leftPanelY;
    private int leftPanelW;
    private int leftPanelH;
    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;
    private int footerY;
    private int canvasX;
    private int canvasY;
    private int canvasW;
    private int canvasH;
    private int geneScrollOffset;
    private int geneVisibleRows;
    private int geneTotalRows;
    private int geneListStartY;
    private int geneListEndY;

    private LeftTab activeTab = LeftTab.MACHINES;
    private NodeTemplate draggedTemplate;
    private String draggedGeneInstanceId;
    private double dragMouseX;
    private double dragMouseY;
    private boolean shiftHeld;
    private boolean categoryDropdownExpanded;
    private String selectedGeneCategory;
    private UiDropdown categoryDropdown;

    public GeneExperimentsScreen() {
        super(Component.literal("Genetic Experimentation Table"));
    }

    @Override
    public void init() {
        super.init();
        layoutPanels();
        this.geneCategories.clear();
        this.geneCategories.addAll(this.genesByCategory.keySet());
        if (this.selectedGeneCategory == null && !this.geneCategories.isEmpty()) {
            this.selectedGeneCategory = this.geneCategories.get(0);
        }
        buildGeneInventory();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        layoutPanels();
        this.dragMouseX = mouseX;
        this.dragMouseY = mouseY;

        this.extractBackground(graphics, mouseX, mouseY, partialTick);
        GeneUiStyle.drawTiledTexture(graphics, GeneUiStyle.FRAME_TEXTURE, this.frameX, this.frameY, this.frameW, this.frameH);
        GeneUiStyle.drawBevelPanel(graphics, this.frameX, this.frameY, this.frameW, this.frameH, GeneUiStyle.FRAME_BG);
        int titleWidth = this.font.width(this.title);
        graphics.text(this.font, this.title, this.frameX + (this.frameW - titleWidth) / 2, this.frameY + 6, 0xFF2A2A2A, false);

        renderPanel(graphics, this.leftPanelX, this.leftPanelY, this.leftPanelW, this.leftPanelH, "Library");
        renderPanel(graphics, this.canvasX, this.canvasY, this.canvasW, this.canvasH, "Node Editor");
        GeneUiStyle.drawInsetPanel(graphics, this.canvasX + 1, this.canvasY + 1, this.canvasW - 2, this.canvasH - 2);

        rebuildUiElements();
        if (this.activeTab == LeftTab.GENES) {
            renderGeneList(graphics);
        }
        this.uiRoot.render(graphics, this.font, mouseX, mouseY);
        Component uiTooltip = this.uiRoot.tooltip();
        if (uiTooltip != null) {
            graphics.setTooltipForNextFrame(this.font, uiTooltip, mouseX, mouseY);
        }

        graphics.enableScissor(this.canvasX + 1, this.canvasY + 1, this.canvasX + this.canvasW - 1, this.canvasY + this.canvasH - 1);
        this.canvasRenderer.render(
                graphics,
                this.font,
                this.graph,
                this.viewport,
                this.selection,
                this.connectionDraft,
                this.canvasX + 1,
                this.canvasY + 1,
                this.canvasW - 2,
                this.canvasH - 2
        );
        graphics.disableScissor();
        renderCanvasTooltips(graphics, mouseX, mouseY);

        if (this.draggedTemplate != null) {
            renderDraggedTemplateGhost(graphics, this.draggedTemplate, mouseX, mouseY);
        }
        renderHints(graphics);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.dragMouseX = event.x();
        this.dragMouseY = event.y();

        if (this.uiRoot.onMouseClicked(event.button(), event.x(), event.y())) {
            if (this.categoryDropdown != null) {
                this.categoryDropdownExpanded = this.categoryDropdown.isExpanded();
            }
            return true;
        }
        if (event.button() == 0 && handleGeneListClick(event.x(), event.y())) {
            return true;
        }

        if (isInsideCanvas(event.x(), event.y())) {
            if (this.inputController.onMouseClicked(
                    event.button(),
                    event.x(),
                    event.y(),
                    this.canvasX + 1,
                    this.canvasY + 1,
                    this.shiftHeld)
            ) {
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        this.dragMouseX = event.x();
        this.dragMouseY = event.y();

        if (this.draggedTemplate != null) {
            return true;
        }

        if (this.inputController.onMouseDragged(
                event.button(),
                event.x(),
                event.y(),
                dragX,
                dragY,
                this.canvasX + 1,
                this.canvasY + 1)
        ) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragMouseX = event.x();
        this.dragMouseY = event.y();

        if (event.button() == 0 && this.draggedTemplate != null) {
            if (isInsideCanvas(event.x(), event.y())) {
                spawnTemplateAtCanvasPosition(this.draggedTemplate, this.draggedGeneInstanceId, event.x(), event.y());
            }
            this.draggedTemplate = null;
            this.draggedGeneInstanceId = null;
            return true;
        }

        if (this.inputController.onMouseReleased(
                event.button(),
                event.x(),
                event.y(),
                this.canvasX + 1,
                this.canvasY + 1,
                this.shiftHeld)
        ) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInsideCanvas(mouseX, mouseY)) {
            this.viewport.zoomAt(scrollY, mouseX, mouseY, this.canvasX + 1, this.canvasY + 1);
            return true;
        }
        if (this.uiRoot.onMouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (this.activeTab == LeftTab.GENES && isInsideGeneList(mouseX, mouseY) && this.geneTotalRows > this.geneVisibleRows) {
            this.geneScrollOffset += scrollY > 0 ? -1 : 1;
            clampGeneScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_LEFT_SHIFT || event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            this.shiftHeld = true;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DELETE) {
            releaseConsumedGeneInstances(this.selection.selectedNodeIds());
            this.graph.removeNodes(this.selection.selectedNodeIds());
            this.selection.clearSelection();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_C) {
            centerViewportOnGraph();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_LEFT_SHIFT || event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            this.shiftHeld = false;
            return true;
        }
        return super.keyReleased(event);
    }

    private void layoutPanels() {
        this.frameW = Math.min(FRAME_WIDTH, this.width - 16);
        this.frameH = Math.min(FRAME_HEIGHT, this.height - 16);
        this.frameX = (this.width - this.frameW) / 2;
        this.frameY = (this.height - this.frameH) / 2;

        int contentY = this.frameY + OUTER_PADDING + HEADER_HEIGHT;
        int contentHeight = this.frameH - HEADER_HEIGHT - 26;

        this.leftPanelX = this.frameX + OUTER_PADDING;
        this.leftPanelY = contentY + 13;
        this.leftPanelW = LEFT_PANEL_WIDTH;
        this.leftPanelH = contentHeight - 13;

        this.canvasX = this.leftPanelX + this.leftPanelW + PANEL_GAP;
        this.canvasY = contentY;
        this.canvasW = this.frameX + this.frameW - OUTER_PADDING - this.canvasX;
        this.canvasH = contentHeight;
        this.footerY = this.frameY + this.frameH - 18;
    }

    private void rebuildUiElements() {
        this.uiRoot.clearChildren();
        this.topActionButtons.clear();
        this.machineItems.clear();
        this.geneItems.clear();

        rebuildTopActionButtons();
        rebuildTabButtons();

        int tabsBottom = this.leftPanelY + 6 + TAB_HEIGHT;
        if (this.activeTab == LeftTab.MACHINES) {
            buildMachineItems(tabsBottom);
            return;
        }
        buildGeneItems(tabsBottom);
    }

    private void buildMachineItems(int tabsBottom) {
        int slotSize = 18;
        int startX = this.leftPanelX + 8;
        int startY = tabsBottom + 6;
        int spacing = 2;
        int columns = Math.max(1, (this.leftPanelW - 16 + spacing) / (slotSize + spacing));

        for (int i = 0; i < this.machineTemplates.size(); i++) {
            NodeTemplate template = this.machineTemplates.get(i);
            int row = i / columns;
            int col = i % columns;
            int x = startX + col * (slotSize + spacing);
            int y = startY + row * (slotSize + spacing);

            UiListItem<NodeTemplate> item = new UiListItem<>(
                    new UiRect(x, y, slotSize, slotSize),
                    template,
                    (slot, graphics, font, mouseX, mouseY) -> {
                        UiRect rect = slot.bounds();
                        GeneUiStyle.drawSlot(graphics, rect.x(), rect.y(), rect.width(), rect.height(), slot.isHovered(), false);
                        graphics.item(machineIconForTemplate(slot.data()), rect.x() + 1, rect.y() + 1);
                    },
                    (slot, button, mouseX, mouseY) -> {
                        if (button != 0 || !slot.contains(mouseX, mouseY)) {
                            return false;
                        }
                        this.draggedTemplate = slot.data();
                        this.draggedGeneInstanceId = null;
                        return true;
                    },
                    slot -> Component.literal(slot.data().title())
            );
            this.machineItems.add(item);
            this.uiRoot.addChild(item);
        }
    }

    private void buildGeneItems(int tabsBottom) {
        int dropdownX = this.leftPanelX + 8;
        int dropdownY = tabsBottom + 6;
        int dropdownW = this.leftPanelW - 16;
        this.categoryDropdown = new UiDropdown(
                new UiRect(dropdownX, dropdownY, dropdownW, DROPDOWN_HEIGHT),
                DROPDOWN_HEIGHT,
                () -> this.geneCategories,
                () -> this.selectedGeneCategory,
                category -> {
                    this.selectedGeneCategory = category;
                    this.categoryDropdownExpanded = false;
                    this.geneScrollOffset = 0;
                },
                () -> Component.literal("Choose gene category")
        );
        this.categoryDropdown.setExpanded(this.categoryDropdownExpanded);
        this.uiRoot.addChild(this.categoryDropdown);

        int listStartY = dropdownY + DROPDOWN_HEIGHT + 4;
        List<GeneListRow> rows = buildGeneRows(this.selectedGeneCategory);
        buildGeneSlots(rows, this.geneItems, this.leftPanelX + 8, listStartY, this.leftPanelW - 16);
    }

    private void buildGeneSlots(List<GeneListRow> rows, List<UiListItem<GeneListRow>> out, int x, int startY, int width) {
        this.geneListStartY = startY;
        this.geneListEndY = this.leftPanelY + this.leftPanelH - 8;
        int rowHeight = ENTRY_HEIGHT + LIST_ROW_GAP;
        int visibleHeight = Math.max(0, this.geneListEndY - this.geneListStartY);
        this.geneVisibleRows = Math.max(1, visibleHeight / rowHeight);
        this.geneTotalRows = rows.size();
        clampGeneScroll();

        int y = startY;
        int end = Math.min(rows.size(), this.geneScrollOffset + this.geneVisibleRows);
        for (int i = this.geneScrollOffset; i < end; i++) {
            GeneListRow row = rows.get(i);
            UiListItem<GeneListRow> item = new UiListItem<>(
                    new UiRect(x, y, width, ENTRY_HEIGHT),
                    row,
                    this::renderGeneRowItem,
                    (slot, button, mouseX, mouseY) -> false,
                    this::geneItemTooltip
            );
            out.add(item);
            y += rowHeight;
        }
    }

    private void rebuildTabButtons() {
        int x = this.leftPanelX + 4;
        int y = this.leftPanelY - TAB_HEIGHT + 1;
        int w = (this.leftPanelW) / 2;
        UiTextButton machinesButton = new UiTextButton(
                new UiRect(x, y, w, TAB_HEIGHT),
                () -> Component.literal("Machines").withColor(ChatFormatting.WHITE.getColor()),
                () -> {
                    this.activeTab = LeftTab.MACHINES;
                    this.categoryDropdownExpanded = false;
                    this.geneScrollOffset = 0;
                },
                () -> Component.literal("Open Machines palette")
        );
        machinesButton.setActive(this.activeTab == LeftTab.MACHINES);
        this.uiRoot.addChild(machinesButton);

        UiTextButton genesButton = new UiTextButton(
                new UiRect(x + w + 4, y, w, TAB_HEIGHT),
                () -> Component.literal("Genes").withColor(ChatFormatting.WHITE.getColor()),
                () -> {
                    this.activeTab = LeftTab.GENES;
                    this.geneScrollOffset = 0;
                },
                () -> Component.literal("Open Genes palette")
        );
        genesButton.setActive(this.activeTab == LeftTab.GENES);
        this.uiRoot.addChild(genesButton);
    }

    private void renderPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, String title) {
        GeneUiStyle.drawTiledTexture(graphics, GeneUiStyle.FRAME_TEXTURE, x, y, width, height);
        GeneUiStyle.drawBevelPanel(graphics, x, y, width, height, GeneUiStyle.PANEL_BG);
        graphics.text(this.font, title, x + 4, y + 5, 0xFF202020, false);
    }

    private void renderGeneList(GuiGraphicsExtractor graphics) {
        graphics.enableScissor(this.leftPanelX + 6, this.geneListStartY - 1, this.leftPanelX + this.leftPanelW - 6, this.geneListEndY + 1);
        for (UiListItem<GeneListRow> item : this.geneItems) {
            item.updateHover(this.dragMouseX, this.dragMouseY);
            item.render(graphics, this.font, (int) this.dragMouseX, (int) this.dragMouseY);
            if (item.isHovered()) {
                Component tooltip = item.tooltip();
                if (tooltip != null) {
                    graphics.setTooltipForNextFrame(this.font, tooltip, (int) this.dragMouseX, (int) this.dragMouseY);
                }
            }
        }
        graphics.disableScissor();
        renderGeneScrollBar(graphics);
    }

    private void renderGeneRowItem(UiListItem<GeneListRow> item, GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
        GeneListRow row = item.data();
        UiRect rect = item.bounds();
        if (row.groupHeader()) {
            GeneUiStyle.drawSlot(graphics, rect.x(), rect.y(), rect.width(), rect.height(), item.isHovered(), true);
            boolean expanded = this.expandedGeneTemplateIds.contains(row.template().templateId());
            String arrow = expanded ? "v " : "> ";
            String title = arrow + row.template().title() + " (" + row.availableCount() + "/" + row.totalCount() + ")";
            String clipped = font.plainSubstrByWidth(title, Math.max(8, rect.width() - 6));
            graphics.text(font, clipped, rect.x() + 4, rect.y() + 4, 0xFF181818, false);
            return;
        }

        boolean used = this.consumedGeneInstanceIds.contains(row.instanceId());
        GeneUiStyle.drawSlot(graphics, rect.x(), rect.y(), rect.width(), rect.height(), item.isHovered(), false);
        graphics.fill(rect.x(), rect.y(), rect.x() + 3, rect.y() + rect.height(), row.template().color());

        String title = font.plainSubstrByWidth(row.template().title(), Math.max(8, rect.width() - 46));
        int textColor = used ? 0xFF7A7A7A : 0xFF1B1B1B;
        graphics.text(font, title, rect.x() + 10, rect.y() + 4, textColor, false);

        String pct = row.qualityPercent() + "%";
        int pctColor = used ? 0xFF8A8A8A : 0xFF2D2D2D;
        graphics.text(font, pct, rect.x() + rect.width() - font.width(pct) - 4, rect.y() + 4, pctColor, false);
    }

    private Component geneItemTooltip(UiListItem<GeneListRow> item) {
        GeneListRow row = item.data();
        if (row.groupHeader()) {
            return Component.literal("Click to expand/collapse");
        }
        boolean used = this.consumedGeneInstanceIds.contains(row.instanceId());
        String tip = used ? "Used in graph" : "Drag to canvas";
        return Component.literal(row.template().title() + " (" + row.qualityPercent() + "%) - " + tip);
    }

    private void renderDraggedTemplateGhost(GuiGraphicsExtractor graphics, NodeTemplate template, int mouseX, int mouseY) {
        if (template.kind() == NodeKind.MACHINE) {
            int size = 24;
            int x = mouseX - size / 2;
            int y = mouseY - size / 2;
            GeneUiStyle.drawSlot(graphics, x, y, size, size, false, true);
            graphics.item(machineIconForTemplate(template), x + 4, y + 4);
            return;
        }
        int width = 112;
        int height = 22;
        int x = mouseX - width / 2;
        int y = mouseY - height / 2;
        GeneUiStyle.drawSlot(graphics, x, y, width, height, false, true);
        graphics.fill(x, y, x + 5, y + height, template.color());
        graphics.text(this.font, template.title(), x + 8, y + 7, GeneUiStyle.TEXT_BRIGHT, false);
    }

    private void renderHints(GuiGraphicsExtractor graphics) {
        GeneUiStyle.drawBevelPanel(graphics, this.frameX + OUTER_PADDING, this.footerY, this.frameW - (OUTER_PADDING * 2), 14, GeneUiStyle.PANEL_BG);
        String line = this.draggedTemplate != null
                ? "Release on canvas to place node"
                : "LMB select/drag | Shift multi | RMB pan | Wheel zoom";
        graphics.centeredText(this.font, line, this.frameX + this.frameW / 2, this.footerY + 3, 0xFFD6D6D6);
    }

    private void renderCanvasTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!isInsideCanvas(mouseX, mouseY)) {
            return;
        }
        double worldX = this.viewport.screenToWorldX(mouseX, this.canvasX + 1);
        double worldY = this.viewport.screenToWorldY(mouseY, this.canvasY + 1);
        GraphHitTest.PortHit portHit = GraphHitTest.findPortAt(this.graph.nodes(), worldX, worldY);
        if (portHit != null) {
            GeneNode node = this.graph.nodeById(portHit.nodeId());
            String nodeLabel = node == null ? portHit.nodeId() : node.title();
            String side = portHit.direction() == PortDirection.INPUT ? "Input" : "Output";
            graphics.setTooltipForNextFrame(this.font, Component.literal(nodeLabel + " - " + side), mouseX, mouseY);
            return;
        }
        GeneNode nodeHit = GraphHitTest.findNodeAt(this.graph.nodes(), worldX, worldY);
        if (nodeHit != null) {
            graphics.setTooltipForNextFrame(this.font, Component.literal(nodeHit.title()), mouseX, mouseY);
        }
    }

    private boolean handleGeneListClick(double mouseX, double mouseY) {
        if (this.activeTab != LeftTab.GENES) {
            return false;
        }
        for (UiListItem<GeneListRow> item : this.geneItems) {
            if (!item.contains(mouseX, mouseY)) {
                continue;
            }
            GeneListRow row = item.data();
            if (row.groupHeader()) {
                toggleGeneGroup(row.template().templateId());
                return true;
            }
            if (row.instanceId() == null || this.consumedGeneInstanceIds.contains(row.instanceId())) {
                return true;
            }
            this.draggedTemplate = row.template();
            this.draggedGeneInstanceId = row.instanceId();
            return true;
        }
        return false;
    }

    private void spawnTemplateAtCanvasPosition(NodeTemplate template, String geneInstanceId, double mouseX, double mouseY) {
        String nodeId = this.graph.nextNodeId(template.kind());
        double worldX = this.viewport.screenToWorldX(mouseX, this.canvasX + 1) - GeneNode.WIDTH / 2.0D;
        double worldY = this.viewport.screenToWorldY(mouseY, this.canvasY + 1) - 20.0D;

        List<GenePort> inputs = new ArrayList<>();
        List<GenePort> outputs = new ArrayList<>();
        for (int i = 0; i < template.inputCount(); i++) {
            String portId = nodeId + "_in_" + i;
            inputs.add(new GenePort(portId, nodeId, PortDirection.INPUT, template.dataType(), "In " + (i + 1)));
        }
        for (int i = 0; i < template.outputCount(); i++) {
            String portId = nodeId + "_out_" + i;
            outputs.add(new GenePort(portId, nodeId, PortDirection.OUTPUT, template.dataType(), "Out " + (i + 1)));
        }

        GeneNode node = new GeneNode(nodeId, template.kind(), template.title(), template.color(), worldX, worldY, inputs, outputs);
        this.graph.addNode(node);
        this.selection.setSingleSelection(nodeId);
        if (template.kind() == NodeKind.GENE && geneInstanceId != null) {
            this.consumedGeneInstanceIds.add(geneInstanceId);
            this.geneInstanceByNodeId.put(nodeId, geneInstanceId);
        }
    }

    private boolean isInsideCanvas(double mouseX, double mouseY) {
        return mouseX >= this.canvasX + 1
                && mouseY >= this.canvasY + 1
                && mouseX <= this.canvasX + this.canvasW - 1
                && mouseY <= this.canvasY + this.canvasH - 1;
    }

    private ItemStack machineIconForTemplate(NodeTemplate template) {
        return switch (template.templateId()) {
            case "combiner" -> new ItemStack(Blocks.CRAFTING_TABLE);
            case "sequencer" -> new ItemStack(Blocks.LECTERN);
            case "stabilizer" -> new ItemStack(Blocks.BEACON);
            case "extractor" -> new ItemStack(Blocks.BLAST_FURNACE);
            default -> new ItemStack(Blocks.IRON_BLOCK);
        };
    }

    private void centerViewportOnGraph() {
        if (this.graph.nodes().isEmpty()) {
            this.viewport.setPan(0.0D, 0.0D);
            return;
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (GeneNode node : this.graph.nodes()) {
            minX = Math.min(minX, node.worldX());
            minY = Math.min(minY, node.worldY());
            maxX = Math.max(maxX, node.worldX() + GeneNode.WIDTH);
            maxY = Math.max(maxY, node.worldY() + node.height());
        }

        this.viewport.centerOnWorld((minX + maxX) / 2.0D, (minY + maxY) / 2.0D, this.canvasX + 1, this.canvasY + 1, this.canvasW - 2, this.canvasH - 2);
    }

    private void buildGeneInventory() {
        this.geneInventoryByCategory.clear();
        for (Map.Entry<String, List<NodeTemplate>> entry : this.genesByCategory.entrySet()) {
            List<GeneInventoryEntry> rows = new ArrayList<>();
            for (NodeTemplate template : entry.getValue()) {
                for (int i = 1; i <= 4; i++) {
                    int quality = qualityFor(template.templateId(), i);
                    rows.add(new GeneInventoryEntry(template.templateId() + "#" + i, template, quality));
                }
            }
            this.geneInventoryByCategory.put(entry.getKey(), rows);
        }
    }

    private static int qualityFor(String templateId, int instance) {
        int seed = Math.abs((templateId + ":" + instance).hashCode());
        return 45 + (seed % 56);
    }

    private List<GeneInventoryEntry> sortedGeneEntries(String category) {
        List<GeneInventoryEntry> source = this.geneInventoryByCategory.getOrDefault(category, List.of());
        return source.stream()
                .sorted(Comparator
                        .comparing((GeneInventoryEntry entry) -> entry.template().title())
                        .thenComparing(Comparator.comparingInt(GeneInventoryEntry::qualityPercent).reversed()))
                .toList();
    }

    private List<GeneListRow> buildGeneRows(String category) {
        List<GeneInventoryEntry> source = sortedGeneEntries(category);
        Map<String, NodeTemplate> templateById = new LinkedHashMap<>();
        Map<String, List<GeneInventoryEntry>> entriesByTemplate = new LinkedHashMap<>();
        for (GeneInventoryEntry entry : source) {
            String templateId = entry.template().templateId();
            templateById.putIfAbsent(templateId, entry.template());
            entriesByTemplate.computeIfAbsent(templateId, ignored -> new ArrayList<>()).add(entry);
        }

        List<GeneListRow> rows = new ArrayList<>();
        for (Map.Entry<String, NodeTemplate> group : templateById.entrySet()) {
            String templateId = group.getKey();
            NodeTemplate template = group.getValue();
            List<GeneInventoryEntry> instances = entriesByTemplate.getOrDefault(templateId, List.of());
            int total = instances.size();
            int available = 0;
            for (GeneInventoryEntry instance : instances) {
                if (!this.consumedGeneInstanceIds.contains(instance.instanceId())) {
                    available++;
                }
            }
            rows.add(GeneListRow.group(template, available, total));
            if (!this.expandedGeneTemplateIds.contains(templateId)) {
                continue;
            }
            for (GeneInventoryEntry instance : instances) {
                if (HIDE_USED_GENES && this.consumedGeneInstanceIds.contains(instance.instanceId())) {
                    continue;
                }
                rows.add(GeneListRow.instance(template, instance.instanceId(), instance.qualityPercent(), available, total));
            }
        }
        return rows;
    }

    private void clampGeneScroll() {
        int maxOffset = Math.max(0, this.geneTotalRows - this.geneVisibleRows);
        if (this.geneScrollOffset < 0) {
            this.geneScrollOffset = 0;
        } else if (this.geneScrollOffset > maxOffset) {
            this.geneScrollOffset = maxOffset;
        }
    }

    private boolean isInsideGeneList(double mouseX, double mouseY) {
        return mouseX >= this.leftPanelX + 8
                && mouseX <= this.leftPanelX + this.leftPanelW - 8
                && mouseY >= this.geneListStartY
                && mouseY <= this.geneListEndY;
    }

    private void renderGeneScrollBar(GuiGraphicsExtractor graphics) {
        if (this.geneTotalRows <= this.geneVisibleRows) {
            return;
        }
        int trackX = this.leftPanelX + this.leftPanelW - 7;
        int trackY = this.geneListStartY;
        int trackH = Math.max(8, this.geneListEndY - this.geneListStartY);
        GeneUiStyle.drawInsetPanel(graphics, trackX, trackY, 3, trackH);

        int thumbH = Math.max(8, (int) Math.round(trackH * (this.geneVisibleRows / (double) this.geneTotalRows)));
        int maxOffset = Math.max(1, this.geneTotalRows - this.geneVisibleRows);
        int thumbTravel = Math.max(1, trackH - thumbH);
        int thumbY = trackY + (int) Math.round((this.geneScrollOffset / (double) maxOffset) * thumbTravel);
        graphics.fill(trackX + 1, thumbY, trackX + 2, thumbY + thumbH, 0xFFCBCBCB);
    }

    private void rebuildTopActionButtons() {
        this.topActionButtons.clear();
        int size = 12;
        int gap = 2;
        int y = this.frameY + 3;
        int x = this.frameX + this.frameW - OUTER_PADDING - (size * 3) - (gap * 2);
        addTopActionButton(x, y, size, "C", "Center graph", this::centerViewportOnGraph);
        addTopActionButton(x + size + gap, y, size, "1", "Reset zoom to 1x", this.viewport::resetZoom);
        addTopActionButton(x + (size + gap) * 2, y, size, "X", "Clear graph", () -> {
            this.graph.clear();
            this.selection.clearSelection();
            this.geneInstanceByNodeId.clear();
            this.consumedGeneInstanceIds.clear();
        });
    }

    private void addTopActionButton(int x, int y, int size, String icon, String tooltip, Runnable onPress) {
        Supplier<String> iconSupplier = () -> icon;
        UiIconButton button = new UiIconButton(
                new UiRect(x, y, size, size),
                iconSupplier,
                onPress,
                () -> Component.literal(tooltip)
        );
        this.topActionButtons.add(button);
        this.uiRoot.addChild(button);
    }

    private void releaseConsumedGeneInstances(Set<String> nodeIds) {
        for (String nodeId : nodeIds) {
            String geneInstanceId = this.geneInstanceByNodeId.remove(nodeId);
            if (geneInstanceId != null) {
                this.consumedGeneInstanceIds.remove(geneInstanceId);
            }
        }
    }

    private void toggleGeneGroup(String templateId) {
        if (this.expandedGeneTemplateIds.contains(templateId)) {
            this.expandedGeneTemplateIds.remove(templateId);
        } else {
            this.expandedGeneTemplateIds.add(templateId);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum LeftTab {
        MACHINES,
        GENES
    }


    private record GeneListRow(
            NodeTemplate template,
            String instanceId,
            int qualityPercent,
            int availableCount,
            int totalCount,
            boolean groupHeader
    ) {
        private static GeneListRow group(NodeTemplate template, int availableCount, int totalCount) {
            return new GeneListRow(template, null, -1, availableCount, totalCount, true);
        }

        private static GeneListRow instance(NodeTemplate template, String instanceId, int qualityPercent, int availableCount, int totalCount) {
            return new GeneListRow(template, instanceId, qualityPercent, availableCount, totalCount, false);
        }
    }

    private record GeneInventoryEntry(String instanceId, NodeTemplate template, int qualityPercent) {
    }

}
