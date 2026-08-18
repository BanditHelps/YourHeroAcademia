package com.github.bandithelps.gui.screens;

import com.github.bandithelps.gui.tree.TreeEditorCostSchema;
import com.github.bandithelps.gui.tree.TreeEditorDraft;
import com.github.bandithelps.gui.tree.TreeEditorExporter;
import com.github.bandithelps.gui.tree.TreeEditorLayoutBackground;
import com.github.bandithelps.gui.tree.TreeEditorNode;
import com.github.bandithelps.gui.tree.TreeConnectionPath;
import com.github.bandithelps.gui.tree.TreeConnectionRenderer;
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
import net.minecraft.world.phys.Vec2;
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
    private boolean showNodeHover;
    private boolean panning;
    @Nullable
    private TreeEditorNode dragging;
    @Nullable
    private TreeEditorNode draggingWaypointNode;
    private int draggingWaypointIndex;
    @Nullable
    private TreeEditorNode selected;
    @Nullable
    private TreeEditorNode parentLinkSource;
    @Nullable
    private ContextMenu contextMenu;
    private String status = "Right-click empty space to add a node. Drag nodes to move. Right-click lines to add vertices. E exports JSON.";

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
        TreeEditorNode hovered = this.nodeAt(mouseX, mouseY);
        VertexPick hoveredVertex = this.hoveredVertex(mouseX, mouseY);
        for (TreeEditorNode node : this.draft.getNodes()) {
            this.drawNode(graphics, node);
        }
        this.drawWaypointHandles(graphics, hoveredVertex);
        graphics.disableScissor();

        this.blitSprite(graphics, VIGNETTE, this.treeX, this.treeY, this.treeW, this.treeH);

        if (this.showNodeHover && hovered != null) {
            this.drawTitleBox(graphics, hovered);
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

        VertexPick vertex = this.hoveredVertex(mouseX, mouseY);
        TreeEditorNode hit = vertex == null ? this.hoveredNode(mouseX, mouseY) : null;
        SegmentPick segment = vertex == null && hit == null ? this.hoveredSegment(mouseX, mouseY) : null;

        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            this.dragging = null;
            this.draggingWaypointNode = null;
            this.panning = false;
            if (vertex != null) {
                this.contextMenu = ContextMenu.forVertex(this, vertex.child(), vertex.index(), mouseX, mouseY);
            } else if (hit != null) {
                this.contextMenu = ContextMenu.forNode(this, hit, mouseX, mouseY);
            } else if (segment != null) {
                this.contextMenu = ContextMenu.forSegment(this, segment.child(), segment.segmentIndex(), mouseX, mouseY);
            } else {
                this.contextMenu = ContextMenu.forEmpty(this, mouseX, mouseY);
            }
            return true;
        }
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (this.parentLinkSource != null) {
            TreeEditorNode target = this.hoveredNode(mouseX, mouseY);
            if (target != null) {
                if (this.draft.setParent(this.parentLinkSource, target)) {
                    this.status = "Parent of " + this.parentLinkSource.getKey() + " set to " + target.getKey();
                } else {
                    this.status = "Could not parent to " + target.getKey() + " (cycle or self).";
                }
            }
            this.parentLinkSource = null;
            this.selected = target;
            return true;
        }
        if (vertex != null) {
            this.draggingWaypointNode = vertex.child();
            this.draggingWaypointIndex = vertex.index();
            return true;
        }
        if (hit != null) {
            this.selected = hit;
            this.dragging = hit;
            return true;
        }
        if (segment != null) {
            if (doubleClick) {
                this.insertVertex(segment.child(), segment.segmentIndex(), this.screenToGridX(mouseX), this.screenToGridY(mouseY));
            }
            return true;
        }
        this.selected = null;
        this.panning = true;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingWaypointNode != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            float gridX = TreeEditorDraft.snap(this.screenToGridX((int) event.x()));
            float gridY = TreeEditorDraft.snap(this.screenToGridY((int) event.y()));
            this.draggingWaypointNode.setConnectionPath(
                    this.draggingWaypointNode.getConnectionPath().withReplaced(this.draggingWaypointIndex, gridX, gridY)
            );
            return true;
        }
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
        this.draggingWaypointNode = null;
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
            if (this.draggingWaypointNode == node) {
                this.draggingWaypointNode = null;
            }
            this.status = "Deleted " + node.getKey();
        } else {
            this.status = "Existing datapack nodes cannot be deleted here.";
        }
    }

    public void insertVertex(TreeEditorNode child, int segmentIndex, float gridX, float gridY) {
        TreeEditorNode parent = this.draft.find(child.getParentKey());
        if (parent == null) {
            return;
        }
        List<Vec2> points = this.fullGridPoints(parent, child);
        int insertAt = Math.max(1, Math.min(segmentIndex + 1, points.size() - 1));
        points.add(insertAt, new Vec2(TreeEditorDraft.snap(gridX), TreeEditorDraft.snap(gridY)));
        child.setConnectionPath(this.waypointsFromFullPath(points));
        this.status = "Added vertex on " + child.getKey();
    }

    public void deleteVertex(TreeEditorNode child, int index) {
        child.setConnectionPath(child.getConnectionPath().withRemoved(index));
        if (this.draggingWaypointNode == child) {
            this.draggingWaypointNode = null;
        }
        this.status = child.getConnectionPath().isEmpty()
                ? "Removed path from " + child.getKey() + "; using default bus."
                : "Deleted vertex on " + child.getKey();
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
            List<TreeEditorNode> defaultChildren = this.defaultChildrenOf(parent);
            if (!defaultChildren.isEmpty()) {
                List<Integer> childXs = new ArrayList<>();
                List<Integer> childYs = new ArrayList<>();
                for (TreeEditorNode child : defaultChildren) {
                    childXs.add(this.nodeScreenX(child));
                    childYs.add(this.nodeScreenY(child));
                }
                TreeConnectionRenderer.drawBus(
                        graphics,
                        this.nodeScreenX(parent),
                        this.nodeScreenY(parent),
                        childXs,
                        childYs,
                        true,
                        LINE_OUTLINE
                );
                TreeConnectionRenderer.drawBus(
                        graphics,
                        this.nodeScreenX(parent),
                        this.nodeScreenY(parent),
                        childXs,
                        childYs,
                        false,
                        LINE_CYAN
                );
            }
            for (TreeEditorNode child : this.draft.childrenOf(parent)) {
                if (child.getConnectionPath().isEmpty()) {
                    continue;
                }
                List<TreeConnectionRenderer.Pixel> pixels = this.toScreenPixels(this.fullGridPoints(parent, child));
                TreeConnectionRenderer.drawPolyline(graphics, pixels, true, LINE_OUTLINE);
                TreeConnectionRenderer.drawPolyline(graphics, pixels, false, LINE_CYAN);
            }
        }
    }

    private void drawWaypointHandles(GuiGraphicsExtractor graphics, @Nullable VertexPick hovered) {
        for (TreeEditorNode child : this.draft.getNodes()) {
            if (child.getConnectionPath().isEmpty()) {
                continue;
            }
            List<Vec2> waypoints = child.getConnectionPath().waypoints();
            for (int index = 0; index < waypoints.size(); index++) {
                Vec2 point = waypoints.get(index);
                boolean highlighted = hovered != null && hovered.child() == child && hovered.index() == index;
                TreeConnectionRenderer.drawHandle(
                        graphics,
                        this.gridToScreenX(point.x),
                        this.gridToScreenY(point.y),
                        highlighted
                );
            }
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

    private void drawTitleBox(GuiGraphicsExtractor graphics, TreeEditorNode node) {
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
    }

    @Nullable
    private TreeEditorNode hoveredNode(int mouseX, int mouseY) {
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

    private int gridToScreenX(float gridX) {
        return this.originX() + Math.round(gridX * GRID_SIZE) - this.panX;
    }

    private int gridToScreenY(float gridY) {
        return this.originY() + Math.round(gridY * GRID_SIZE) - this.panY;
    }

    private List<TreeEditorNode> defaultChildrenOf(TreeEditorNode parent) {
        List<TreeEditorNode> children = new ArrayList<>();
        for (TreeEditorNode child : this.draft.childrenOf(parent)) {
            if (child.getConnectionPath().isEmpty()) {
                children.add(child);
            }
        }
        return children;
    }

    private int busScreenX(TreeEditorNode parent) {
        List<TreeEditorNode> defaults = this.defaultChildrenOf(parent);
        int startX = this.nodeScreenX(parent);
        int endX = defaults.isEmpty() ? startX : this.nodeScreenX(defaults.getFirst());
        return startX + (endX - startX) / 2;
    }

    private List<Vec2> fullGridPoints(TreeEditorNode parent, TreeEditorNode child) {
        List<Vec2> points = new ArrayList<>();
        points.add(new Vec2(parent.getGridX(), parent.getGridY()));
        if (child.getConnectionPath().isEmpty()) {
            float busGridX = TreeEditorDraft.snap(this.screenToGridX(this.busScreenX(parent)));
            points.add(new Vec2(busGridX, parent.getGridY()));
            points.add(new Vec2(busGridX, child.getGridY()));
        } else {
            for (Vec2 waypoint : child.getConnectionPath().waypoints()) {
                points.add(new Vec2(waypoint.x, waypoint.y));
            }
        }
        points.add(new Vec2(child.getGridX(), child.getGridY()));
        return points;
    }

    private TreeConnectionPath waypointsFromFullPath(List<Vec2> points) {
        if (points.size() < 3) {
            return TreeConnectionPath.EMPTY;
        }
        Vec2 start = points.getFirst();
        Vec2 end = points.getLast();
        List<Vec2> waypoints = new ArrayList<>();
        for (int index = 1; index < points.size() - 1; index++) {
            Vec2 point = points.get(index);
            if (samePoint(point, start) || samePoint(point, end)) {
                continue;
            }
            if (!waypoints.isEmpty() && samePoint(waypoints.getLast(), point)) {
                continue;
            }
            waypoints.add(point);
        }
        return waypoints.isEmpty() ? TreeConnectionPath.EMPTY : new TreeConnectionPath(waypoints);
    }

    private List<TreeConnectionRenderer.Pixel> toScreenPixels(List<Vec2> points) {
        List<TreeConnectionRenderer.Pixel> pixels = new ArrayList<>(points.size());
        for (Vec2 point : points) {
            pixels.add(new TreeConnectionRenderer.Pixel(this.gridToScreenX(point.x), this.gridToScreenY(point.y)));
        }
        return pixels;
    }

    @Nullable
    private VertexPick hoveredVertex(int mouseX, int mouseY) {
        if (!this.isInTree(mouseX, mouseY)) {
            return null;
        }
        VertexPick hit = null;
        for (TreeEditorNode child : this.draft.getNodes()) {
            if (child.getConnectionPath().isEmpty()) {
                continue;
            }
            List<Vec2> waypoints = child.getConnectionPath().waypoints();
            for (int index = 0; index < waypoints.size(); index++) {
                Vec2 point = waypoints.get(index);
                if (TreeConnectionRenderer.hitsHandle(mouseX, mouseY, this.gridToScreenX(point.x), this.gridToScreenY(point.y))) {
                    hit = new VertexPick(child, index);
                }
            }
        }
        return hit;
    }

    @Nullable
    private SegmentPick hoveredSegment(int mouseX, int mouseY) {
        if (!this.isInTree(mouseX, mouseY)) {
            return null;
        }
        SegmentPick best = null;
        double bestDistance = TreeConnectionRenderer.HIT_THRESHOLD;
        for (TreeEditorNode child : this.draft.getNodes()) {
            TreeEditorNode parent = this.draft.find(child.getParentKey());
            if (parent == null) {
                continue;
            }
            List<TreeConnectionRenderer.Pixel> pixels = child.getConnectionPath().isEmpty()
                    ? this.defaultBusPixels(parent, child)
                    : this.toScreenPixels(this.fullGridPoints(parent, child));
            TreeConnectionRenderer.SegmentHit hit = TreeConnectionRenderer.hitTest(pixels, mouseX, mouseY, bestDistance);
            if (hit != null) {
                bestDistance = hit.distance();
                best = new SegmentPick(child, hit.segmentIndex());
            }
        }
        return best;
    }

    private List<TreeConnectionRenderer.Pixel> defaultBusPixels(TreeEditorNode parent, TreeEditorNode child) {
        int busX = this.busScreenX(parent);
        List<TreeConnectionRenderer.Pixel> pixels = new ArrayList<>(4);
        pixels.add(new TreeConnectionRenderer.Pixel(this.nodeScreenX(parent), this.nodeScreenY(parent)));
        pixels.add(new TreeConnectionRenderer.Pixel(busX, this.nodeScreenY(parent)));
        pixels.add(new TreeConnectionRenderer.Pixel(busX, this.nodeScreenY(child)));
        pixels.add(new TreeConnectionRenderer.Pixel(this.nodeScreenX(child), this.nodeScreenY(child)));
        return pixels;
    }

    private static boolean samePoint(Vec2 left, Vec2 right) {
        return Float.compare(left.x, right.x) == 0 && Float.compare(left.y, right.y) == 0;
    }

    public List<TreeEditorCostSchema> costSchemas() {
        return this.costSchemas;
    }

    private void addChipButtons() {
        int y = this.chipY + 4;
        int gap = 3;
        int pad = 4;
        int available = Math.max(0, this.treeW - pad * 2);
        int[] widths = {70, 52, 52, 62, 56, 52};
        int needed = widths[0] + widths[1] + widths[2] + widths[3] + widths[4] + widths[5] + gap * 5;
        if (needed > available) {
            int shrink = needed - available;
            for (int i = 0; i < widths.length && shrink > 0; i++) {
                int reduce = Math.min(shrink, widths[i] - 40);
                widths[i] -= reduce;
                shrink -= reduce;
            }
        }
        int leftX = this.treeX + pad;
        int closeX = this.treeX + this.treeW - pad - widths[5];
        int exportX = closeX - gap - widths[4];
        int hoverX = leftX + widths[0] + gap + widths[1] + gap + widths[2] + gap;
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
        this.addRenderableWidget(Button.builder(Component.literal(this.hoverLabel()), button -> {
                    this.showNodeHover = !this.showNodeHover;
                    button.setMessage(Component.literal(this.hoverLabel()));
                })
                .bounds(hoverX, y, widths[3], 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Export"), button -> this.exportDraft())
                .bounds(exportX, y, widths[4], 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> this.onClose())
                .bounds(closeX, y, widths[5], 20)
                .build());
    }

    private String gridLabel() {
        return this.showGrid ? "Grid: On" : "Grid: Off";
    }

    private String numbersLabel() {
        return this.showGridNumbers ? "Nums: On" : "Nums: Off";
    }

    private String hoverLabel() {
        return this.showNodeHover ? "Hover: On" : "Hover: Off";
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

        static ContextMenu forVertex(TreeEditorScreen screen, TreeEditorNode child, int index, int mouseX, int mouseY) {
            return clamped(screen, mouseX, mouseY, List.of(
                    new Item("Delete vertex", () -> screen.deleteVertex(child, index))
            ));
        }

        static ContextMenu forSegment(TreeEditorScreen screen, TreeEditorNode child, int segmentIndex, int mouseX, int mouseY) {
            float gridX = screen.screenToGridX(mouseX);
            float gridY = screen.screenToGridY(mouseY);
            return clamped(screen, mouseX, mouseY, List.of(
                    new Item("Add vertex", () -> screen.insertVertex(child, segmentIndex, gridX, gridY))
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

    private record VertexPick(TreeEditorNode child, int index) {
    }

    private record SegmentPick(TreeEditorNode child, int segmentIndex) {
    }
}
