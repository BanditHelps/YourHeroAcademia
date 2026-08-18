package com.github.bandithelps.gui.screens;

import com.github.bandithelps.gui.tree.TreeEditorCostSchema;
import com.github.bandithelps.gui.tree.TreeEditorDraft;
import com.github.bandithelps.gui.tree.TreeEditorExporter;
import com.github.bandithelps.gui.tree.TreeEditorLayoutBackground;
import com.github.bandithelps.gui.tree.TreeEditorNode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Block;
import net.threetag.palladium.client.renderer.icon.IconRenderer;
import net.threetag.palladium.logic.context.DataContext;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TreeEditorScreen extends Screen {
    private static final int GRID_SIZE = 50;
    private static final int NODE_HIT = 13;
    private static final int LEFT_WIDTH = 100;
    private static final int LEFT_PADDING = 7;
    private static final int LEFT_FOOTER = 28;
    private static final int MIN_TREE_WIDTH = 250;
    private static final int MIN_TREE_HEIGHT = 200;
    private static final int CHIP_HEIGHT = 28;
    private static final int CHIP_GAP = 2;
    private static final int LINE_CYAN = 0xFF00FFFF;
    private static final int LINE_OUTLINE = 0xFF000000;
    private static final int TEXT_LIGHT = 0xFFFFFFFF;
    private static final Identifier FRAME_UNLOCKED = Identifier.fromNamespaceAndPath("palladium", "powers/ability_frame_unlocked");
    private static final Identifier FRAME_LOCKED = Identifier.fromNamespaceAndPath("palladium", "powers/ability_frame_locked");
    private static final Identifier VIGNETTE = Identifier.fromNamespaceAndPath("palladium", "powers/vignette");

    private final TreeEditorDraft draft;
    private List<TreeEditorCostSchema> costSchemas = List.of();
    @Nullable
    private TextureAtlasSprite treeBackgroundSprite;
    private int leftX;
    private int leftY;
    private int treeX;
    private int treeY;
    private int treeW;
    private int treeH;
    private int leftH;
    private int chipX;
    private int chipY;
    private int panX;
    private int panY;
    private boolean viewReady;
    private boolean showGrid;
    private boolean showGridNumbers;
    private boolean panning;
    @Nullable
    private TreeEditorNode dragging;
    @Nullable
    private TreeEditorNode selected;
    @Nullable
    private TreeEditorNode parentLinkSource;
    @Nullable
    private ContextMenu contextMenu;
    @Nullable
    private HoverBox hoverBox;
    private String status = "Right-click empty space to add a node. Drag nodes to move. E exports JSON.";

    public TreeEditorScreen(TreeEditorDraft draft) {
        super(Component.literal("Power Tree Editor"));
        this.draft = draft;
    }

    @Override
    protected void init() {
        super.init();
        this.layoutChrome();
        if (this.minecraft != null) {
            if (this.costSchemas.isEmpty()) {
                this.costSchemas = TreeEditorCostSchema.load(this.minecraft);
            }
            if (this.draft.getBackgroundTexture().equals(TreeEditorLayoutBackground.FALLBACK)) {
                this.draft.setBackgroundTexture(TreeEditorLayoutBackground.resolve(this.minecraft, this.draft.getPowerId()));
            }
            this.applyBackground(this.draft.getBackgroundTexture());
        }
        if (!this.viewReady) {
            this.panX = 0;
            this.panY = 0;
            this.viewReady = true;
        }
        this.addChipButtons();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.layoutChrome();
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);

        this.drawModalPanel(graphics, this.leftX, this.leftY, LEFT_WIDTH, this.leftH);
        this.drawLeftPanel(graphics);

        this.drawTreeBackground(graphics);

        graphics.enableScissor(this.treeX, this.treeY, this.treeX + this.treeW, this.treeY + this.treeH);
        if (this.showGrid) {
            this.drawGrid(graphics);
        }
        this.drawConnections(graphics);
        TreeEditorNode hovered = this.hoveredNode(mouseX, mouseY);
        for (TreeEditorNode node : this.draft.getNodes()) {
            this.drawNode(graphics, node);
        }
        graphics.disableScissor();

        this.blitSprite(graphics, VIGNETTE, this.treeX, this.treeY, this.treeW, this.treeH);

        if (hovered != null || this.selected != null || this.parentLinkSource != null) {
            TreeEditorNode labeled = hovered != null ? hovered : (this.parentLinkSource != null ? this.parentLinkSource : this.selected);
            this.hoverBox = this.drawTitleBox(graphics, labeled);
        } else {
            this.hoverBox = null;
        }

        this.drawChip(graphics, this.treeX, this.chipY, this.treeW, CHIP_HEIGHT);

        if (this.contextMenu != null) {
            this.contextMenu.draw(graphics, this.font);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        if (this.contextMenu != null) {
            if (this.contextMenu.click(mouseX, mouseY)) {
                this.contextMenu = null;
                return true;
            }
            this.contextMenu = null;
        }
        if (!this.isInTree(mouseX, mouseY)) {
            if (this.parentLinkSource != null) {
                this.parentLinkSource = null;
                this.status = "Parent mode cancelled.";
            }
            return true;
        }

        TreeEditorNode hit = this.hoveredNode(mouseX, mouseY);
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            this.dragging = null;
            this.panning = false;
            this.contextMenu = hit == null
                    ? ContextMenu.forEmpty(this, mouseX, mouseY)
                    : ContextMenu.forNode(this, hit, mouseX, mouseY);
            return true;
        }
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (this.parentLinkSource != null) {
            if (hit != null) {
                if (this.draft.setParent(this.parentLinkSource, hit)) {
                    this.status = "Parent of " + this.parentLinkSource.getKey() + " set to " + hit.getKey();
                } else {
                    this.status = "Could not parent to " + hit.getKey() + " (cycle or self).";
                }
            }
            this.parentLinkSource = null;
            this.selected = hit;
            return true;
        }
        if (hit != null) {
            this.selected = hit;
            this.dragging = hit;
            return true;
        }
        this.selected = null;
        this.panning = true;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            float gridX = TreeEditorDraft.snap(this.screenToGridX((int) event.x()));
            float gridY = TreeEditorDraft.snap(this.screenToGridY((int) event.y()));
            this.dragging.setGrid(gridX, gridY);
            return true;
        }
        if (this.panning && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.panX -= (int) dragX;
            this.panY -= (int) dragY;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = null;
        this.panning = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && this.parentLinkSource != null) {
            this.parentLinkSource = null;
            this.status = "Parent mode cancelled.";
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_E) {
            this.exportDraft();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DELETE && this.selected != null) {
            if (this.draft.remove(this.selected)) {
                this.status = "Deleted " + this.selected.getKey();
                this.selected = null;
            } else {
                this.status = "Existing datapack nodes cannot be deleted here.";
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void beginParentLink(TreeEditorNode node) {
        this.parentLinkSource = node;
        this.selected = node;
        this.status = "Click a node to parent '" + node.getKey() + "'.";
    }

    public void unparent(TreeEditorNode node) {
        this.draft.setParent(node, null);
        this.status = "Removed parent from " + node.getKey();
    }

    public void addNodeAt(int mouseX, int mouseY) {
        TreeEditorNode node = this.draft.addDummy(this.screenToGridX(mouseX), this.screenToGridY(mouseY));
        this.selected = node;
        this.openEdit(node);
    }

    public void openEdit(TreeEditorNode node) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new TreeEditorEditPopupScreen(this, this.draft, node));
        }
    }

    public void deleteNode(TreeEditorNode node) {
        if (this.draft.remove(node)) {
            if (this.selected == node) {
                this.selected = null;
            }
            this.status = "Deleted " + node.getKey();
        } else {
            this.status = "Existing datapack nodes cannot be deleted here.";
        }
    }

    public void exportDraft() {
        if (this.minecraft == null) {
            return;
        }
        try {
            String json = TreeEditorExporter.toJson(this.draft, this.costSchemas);
            Path file = TreeEditorExporter.writeToGameDir(this.minecraft, this.draft, json);
            String path = file.toAbsolutePath().toString();
            this.status = "Exported to " + path;
            this.minecraft.setScreen(new TreeEditorExportPopupScreen(this, path));
        } catch (Exception exception) {
            this.status = "Export failed: " + exception.getMessage();
        }
    }

    private void layoutChrome() {
        int margin = 8;
        int chipSpace = CHIP_GAP + CHIP_HEIGHT;
        this.treeW = Math.max(MIN_TREE_WIDTH, this.width - margin * 2 - LEFT_WIDTH);
        this.treeH = Math.max(MIN_TREE_HEIGHT, this.height - margin * 2 - chipSpace);
        this.treeW = Math.min(this.treeW, Math.max(MIN_TREE_WIDTH, this.width - margin * 2 - LEFT_WIDTH));
        this.treeH = Math.min(this.treeH, Math.max(MIN_TREE_HEIGHT, this.height - margin * 2 - chipSpace));
        int totalW = LEFT_WIDTH + this.treeW;
        int totalH = this.treeH + chipSpace;
        int originX = Math.max(margin, (this.width - totalW) / 2);
        int originY = Math.max(margin, (this.height - totalH) / 2);
        this.leftX = originX;
        this.leftY = originY;
        this.leftH = this.treeH;
        this.treeX = originX + LEFT_WIDTH;
        this.treeY = originY;
        this.chipX = this.treeX;
        this.chipY = this.treeY + this.treeH + CHIP_GAP;
    }

    private void drawModalPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF2B2B2B);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFFC6C6C6);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, 0xFFFFFFFF);
        graphics.fill(x + 4, y + 4, x + width - 4, y + height - LEFT_FOOTER, 0xFF000000);
    }

    private void drawChip(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF2B2B2B);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFFC6C6C6);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, 0xFFFFFFFF);
    }

    private void drawLeftPanel(GuiGraphicsExtractor graphics) {
        int textX = this.leftX + LEFT_PADDING;
        int textY = this.leftY + LEFT_PADDING;
        int textW = LEFT_WIDTH - LEFT_PADDING * 2;
        graphics.centeredText(this.font, this.draft.getPowerName(), this.leftX + LEFT_WIDTH / 2, textY, TEXT_LIGHT);
        textY += 16;
        textY = this.drawWrapped(graphics, this.draft.getPowerId().toString(), textX, textY, textW, TEXT_LIGHT) + 6;

        if (this.selected != null) {
            textY = this.drawWrapped(graphics, this.selected.getTitle(), textX, textY, textW, TEXT_LIGHT) + 2;
            textY = this.drawWrapped(graphics, this.selected.getKey(), textX, textY, textW, TEXT_LIGHT) + 2;
            if (!this.selected.getDescription().isBlank()) {
                textY = this.drawWrapped(graphics, this.selected.getDescription(), textX, textY, textW, 0xFFCCCCCC) + 2;
            }
            textY = this.drawWrapped(graphics, this.selected.getCost().summary(this.costSchemas), textX, textY, textW, TEXT_LIGHT) + 6;
        }

        String help = this.parentLinkSource != null
                ? "Parent mode: click a node to parent '" + this.parentLinkSource.getKey() + "'. Esc cancels."
                : this.status;
        this.drawWrapped(graphics, help, textX, textY, textW, TEXT_LIGHT);
    }

    private int drawWrapped(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color) {
        for (FormattedCharSequence line : this.font.split(Component.literal(text), maxWidth)) {
            graphics.text(this.font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private void drawConnections(GuiGraphicsExtractor graphics) {
        for (TreeEditorNode parent : this.draft.getNodes()) {
            List<TreeEditorNode> children = this.draft.childrenOf(parent);
            if (children.isEmpty()) {
                continue;
            }
            int startX = this.nodeScreenX(parent);
            int startY = this.nodeScreenY(parent);
            int endX = this.nodeScreenX(children.getFirst());
            int busX = startX + (endX - startX) / 2;
            int minY = startY;
            int maxY = startY;
            List<Integer> childXs = new ArrayList<>();
            List<Integer> childYs = new ArrayList<>();
            for (TreeEditorNode child : children) {
                int childX = this.nodeScreenX(child);
                int childY = this.nodeScreenY(child);
                childXs.add(childX);
                childYs.add(childY);
                minY = Math.min(minY, childY);
                maxY = Math.max(maxY, childY);
            }
            this.drawBus(graphics, startX, startY, busX, childXs, childYs, minY, maxY, LINE_OUTLINE, true);
            this.drawBus(graphics, startX, startY, busX, childXs, childYs, minY, maxY, LINE_CYAN, false);
        }
    }

    private void drawBus(
            GuiGraphicsExtractor graphics,
            int startX,
            int startY,
            int busX,
            List<Integer> childXs,
            List<Integer> childYs,
            int minY,
            int maxY,
            int color,
            boolean outline
    ) {
        this.drawVLine(graphics, busX, minY, maxY, color, outline);
        this.drawHLine(graphics, startX, busX, startY, color, outline);
        for (int index = 0; index < childXs.size(); index++) {
            this.drawHLine(graphics, busX, childXs.get(index), childYs.get(index), color, outline);
        }
    }

    private void drawHLine(GuiGraphicsExtractor graphics, int x1, int x2, int y, int color, boolean outline) {
        int min = Math.min(x1, x2);
        int max = Math.max(x1, x2);
        if (outline) {
            graphics.fill(min - 2, y - 2, max + 1, y + 1, color);
        } else {
            graphics.fill(min - 1, y - 1, max, y, color);
        }
    }

    private void drawVLine(GuiGraphicsExtractor graphics, int x, int y1, int y2, int color, boolean outline) {
        int min = Math.min(y1, y2);
        int max = Math.max(y1, y2);
        if (outline) {
            graphics.fill(x - 2, min - 2, x + 1, max + 1, color);
        } else {
            graphics.fill(x - 1, min - 1, x, max, color);
        }
    }

    private void drawNode(GuiGraphicsExtractor graphics, TreeEditorNode node) {
        int x = this.nodeScreenX(node);
        int y = this.nodeScreenY(node);
        Identifier frame = node.isCreated() ? FRAME_LOCKED : FRAME_UNLOCKED;
        this.blitSprite(graphics, frame, x - NODE_HIT, y - NODE_HIT, 26, 26);
        if (node.getIcon() != null && this.minecraft != null && this.minecraft.player != null) {
            IconRenderer.drawIcon(node.getIcon(), this.minecraft, graphics, DataContext.forEntity(this.minecraft.player), x - 8, y - 8);
        }
    }

    private HoverBox drawTitleBox(GuiGraphicsExtractor graphics, TreeEditorNode node) {
        int x = this.nodeScreenX(node);
        int y = this.nodeScreenY(node);
        String label = node.getTitle() + " [" + node.getKey() + "]";
        String description = node.getDescription();
        int maxTextWidth = 188;
        List<FormattedCharSequence> descLines = description.isBlank()
                ? List.of()
                : this.font.split(Component.literal(description), maxTextWidth);
        int textWidth = this.font.width(label);
        for (FormattedCharSequence line : descLines) {
            textWidth = Math.max(textWidth, this.font.width(line));
        }
        int boxWidth = Math.min(200, Math.max(40, textWidth + 12));
        int boxHeight = 18 + (descLines.isEmpty() ? 8 : 4 + descLines.size() * 10);
        int boxX = x + NODE_HIT + 2;
        int boxY = y - NODE_HIT;
        if (boxX + boxWidth > this.treeX + this.treeW) {
            boxX = x - NODE_HIT - 2 - boxWidth;
        }
        if (boxY + boxHeight > this.treeY + this.treeH) {
            boxY = Math.max(this.treeY, this.treeY + this.treeH - boxHeight);
        }
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xF0101010);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, 0xFFFFFFFF);
        graphics.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, 0xFF555555);
        graphics.fill(boxX, boxY, boxX + 1, boxY + boxHeight, 0xFFFFFFFF);
        graphics.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF555555);
        graphics.text(this.font, label, boxX + 6, boxY + 5, TEXT_LIGHT, false);
        int lineY = boxY + 16;
        for (FormattedCharSequence line : descLines) {
            graphics.text(this.font, line, boxX + 6, lineY, 0xFFCCCCCC, false);
            lineY += 10;
        }
        return new HoverBox(node, boxX, boxY, boxWidth, boxHeight);
    }

    @Nullable
    private TreeEditorNode hoveredNode(int mouseX, int mouseY) {
        if (this.hoverBox != null && this.hoverBox.contains(mouseX, mouseY)) {
            return this.hoverBox.node();
        }
        return this.nodeAt(mouseX, mouseY);
    }

    @Nullable
    private TreeEditorNode nodeAt(int mouseX, int mouseY) {
        if (!this.isInTree(mouseX, mouseY)) {
            return null;
        }
        TreeEditorNode hit = null;
        for (TreeEditorNode node : this.draft.getNodes()) {
            if (this.isHoveringNode(node, mouseX, mouseY)) {
                hit = node;
            }
        }
        return hit;
    }

    private boolean isHoveringNode(TreeEditorNode node, int mouseX, int mouseY) {
        int x = this.nodeScreenX(node);
        int y = this.nodeScreenY(node);
        return mouseX >= x - NODE_HIT && mouseX <= x + NODE_HIT && mouseY >= y - NODE_HIT && mouseY <= y + NODE_HIT;
    }

    private boolean isInTree(int mouseX, int mouseY) {
        return mouseX >= this.treeX && mouseX < this.treeX + this.treeW
                && mouseY >= this.treeY && mouseY < this.treeY + this.treeH;
    }

    private int nodeScreenX(TreeEditorNode node) {
        return this.originX() + Math.round(node.getGridX() * GRID_SIZE) - this.panX;
    }

    private int nodeScreenY(TreeEditorNode node) {
        return this.originY() + Math.round(node.getGridY() * GRID_SIZE) - this.panY;
    }

    private float screenToGridX(int mouseX) {
        return (mouseX - this.originX() + this.panX) / (float) GRID_SIZE;
    }

    private float screenToGridY(int mouseY) {
        return (mouseY - this.originY() + this.panY) / (float) GRID_SIZE;
    }

    private int originX() {
        return this.treeX + this.treeW / 2;
    }

    private int originY() {
        return this.treeY + this.treeH / 2;
    }

    public List<TreeEditorCostSchema> costSchemas() {
        return this.costSchemas;
    }

    private void addChipButtons() {
        int y = this.chipY + 4;
        int gap = 3;
        int pad = 4;
        int available = Math.max(0, this.treeW - pad * 2);
        int[] widths = {76, 58, 58, 60, 56};
        int needed = widths[0] + widths[1] + widths[2] + widths[3] + widths[4] + gap * 4;
        if (needed > available) {
            int shrink = needed - available;
            for (int i = 0; i < widths.length && shrink > 0; i++) {
                int reduce = Math.min(shrink, widths[i] - 42);
                widths[i] -= reduce;
                shrink -= reduce;
            }
        }
        int leftX = this.treeX + pad;
        int closeX = this.treeX + this.treeW - pad - widths[4];
        int exportX = closeX - gap - widths[3];
        this.addRenderableWidget(Button.builder(Component.literal("Background"), button -> this.openBackgroundPicker())
                .bounds(leftX, y, widths[0], 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(this.gridLabel()), button -> {
                    this.showGrid = !this.showGrid;
                    button.setMessage(Component.literal(this.gridLabel()));
                })
                .bounds(leftX + widths[0] + gap, y, widths[1], 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(this.numbersLabel()), button -> {
                    this.showGridNumbers = !this.showGridNumbers;
                    button.setMessage(Component.literal(this.numbersLabel()));
                })
                .bounds(leftX + widths[0] + gap + widths[1] + gap, y, widths[2], 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Export"), button -> this.exportDraft())
                .bounds(exportX, y, widths[3], 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> this.onClose())
                .bounds(closeX, y, widths[4], 20)
                .build());
    }

    private String gridLabel() {
        return this.showGrid ? "Grid: On" : "Grid: Off";
    }

    private String numbersLabel() {
        return this.showGridNumbers ? "Nums: On" : "Nums: Off";
    }

    public void applyBackground(Identifier textureOrBlock) {
        Identifier blockId = TreeEditorPickerScreen.blockIdFromTexture(textureOrBlock);
        this.draft.setBackgroundTexture(TreeEditorPickerScreen.blockTexture(blockId));
        this.treeBackgroundSprite = this.resolveBlockSprite(blockId);
    }

    private void openBackgroundPicker() {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(new TreeEditorPickerScreen(this, "Select Background Block", TreeEditorPickerScreen.Mode.BLOCKS, this::applyBackground));
    }

    private void drawTreeBackground(GuiGraphicsExtractor graphics) {
        if (this.treeBackgroundSprite == null) {
            graphics.fill(this.treeX, this.treeY, this.treeX + this.treeW, this.treeY + this.treeH, 0xFF2A6F7A);
            return;
        }
        graphics.enableScissor(this.treeX, this.treeY, this.treeX + this.treeW, this.treeY + this.treeH);
        for (int x = 0; x < this.treeW; x += 16) {
            for (int y = 0; y < this.treeH; y += 16) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.treeBackgroundSprite, this.treeX + x, this.treeY + y, 16, 16);
            }
        }
        graphics.disableScissor();
    }

    @Nullable
    private TextureAtlasSprite resolveBlockSprite(Identifier blockId) {
        if (this.minecraft == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(blockId).map(holder -> holder.value()).orElse(null);
        if (block == null) {
            return null;
        }
        return this.minecraft.getModelManager()
                .getBlockStateModelSet()
                .getParticleMaterial(block.defaultBlockState())
                .sprite();
    }

    private void drawGrid(GuiGraphicsExtractor graphics) {
        int originX = this.originX() - this.panX;
        int originY = this.originY() - this.panY;
        int firstCol = (int) Math.floor((this.treeX - originX) / (double) GRID_SIZE) - 1;
        int lastCol = (int) Math.ceil((this.treeX + this.treeW - originX) / (double) GRID_SIZE) + 1;
        int firstRow = (int) Math.floor((this.treeY - originY) / (double) GRID_SIZE) - 1;
        int lastRow = (int) Math.ceil((this.treeY + this.treeH - originY) / (double) GRID_SIZE) + 1;
        for (int col = firstCol; col <= lastCol; col++) {
            int x = originX + col * GRID_SIZE;
            int color = col == 0 ? 0x88FFFFFF : 0x33FFFFFF;
            graphics.fill(x, this.treeY, x + 1, this.treeY + this.treeH, color);
            if (this.showGridNumbers) {
                graphics.text(this.font, Integer.toString(col), x + 2, this.treeY + 2, TEXT_LIGHT, false);
            }
        }
        for (int row = firstRow; row <= lastRow; row++) {
            int y = originY + row * GRID_SIZE;
            int color = row == 0 ? 0x88FFFFFF : 0x33FFFFFF;
            graphics.fill(this.treeX, y, this.treeX + this.treeW, y + 1, color);
            if (this.showGridNumbers) {
                graphics.text(this.font, Integer.toString(row), this.treeX + 2, y + 2, TEXT_LIGHT, false);
            }
        }
    }

    private void blitSprite(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int width, int height) {
        try {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
        } catch (RuntimeException ignored) {
            // Sprite may be missing during resource reload.
        }
    }

    private static final class ContextMenu {
        private final int x;
        private final int y;
        private final List<Item> items;

        private ContextMenu(int x, int y, List<Item> items) {
            this.x = x;
            this.y = y;
            this.items = items;
        }

        static ContextMenu forEmpty(TreeEditorScreen screen, int mouseX, int mouseY) {
            return clamped(screen, mouseX, mouseY, List.of(
                    new Item("Add dummy node", () -> screen.addNodeAt(mouseX, mouseY))
            ));
        }

        static ContextMenu forNode(TreeEditorScreen screen, TreeEditorNode node, int mouseX, int mouseY) {
            List<Item> items = new ArrayList<>();
            items.add(new Item("Set parent...", () -> screen.beginParentLink(node)));
            items.add(new Item("Unparent", () -> screen.unparent(node)));
            items.add(new Item("Edit...", () -> screen.openEdit(node)));
            if (node.isCreated()) {
                items.add(new Item("Delete", () -> screen.deleteNode(node)));
            }
            return clamped(screen, mouseX, mouseY, items);
        }

        private static ContextMenu clamped(TreeEditorScreen screen, int mouseX, int mouseY, List<Item> items) {
            int width = 120;
            int height = 8 + items.size() * 16;
            int x = Math.min(mouseX, screen.width - width - 2);
            int y = Math.min(mouseY, screen.height - height - 2);
            return new ContextMenu(Math.max(2, x), Math.max(2, y), items);
        }

        void draw(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font) {
            int width = 120;
            int height = 8 + this.items.size() * 16;
            graphics.fill(this.x, this.y, this.x + width, this.y + height, 0xF0101010);
            graphics.fill(this.x, this.y, this.x + width, this.y + 1, 0xFFFFFFFF);
            graphics.fill(this.x, this.y + height - 1, this.x + width, this.y + height, 0xFF555555);
            graphics.fill(this.x, this.y, this.x + 1, this.y + height, 0xFFFFFFFF);
            graphics.fill(this.x + width - 1, this.y, this.x + width, this.y + height, 0xFF555555);
            for (int index = 0; index < this.items.size(); index++) {
                graphics.text(font, this.items.get(index).label, this.x + 6, this.y + 6 + index * 16, TEXT_LIGHT, false);
            }
        }

        boolean click(int mouseX, int mouseY) {
            int width = 120;
            for (int index = 0; index < this.items.size(); index++) {
                int itemY = this.y + 4 + index * 16;
                if (mouseX >= this.x && mouseX <= this.x + width && mouseY >= itemY && mouseY <= itemY + 16) {
                    this.items.get(index).action.run();
                    return true;
                }
            }
            return false;
        }

        private record Item(String label, Runnable action) {
        }
    }

    private record HoverBox(TreeEditorNode node, int x, int y, int width, int height) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width
                    && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }
}
