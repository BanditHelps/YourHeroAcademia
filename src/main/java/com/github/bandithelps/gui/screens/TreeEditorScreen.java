package com.github.bandithelps.gui.screens;

import com.github.bandithelps.gui.tree.TreeEditorCostSchema;
import com.github.bandithelps.gui.tree.TreeEditorDraft;
import com.github.bandithelps.gui.tree.TreeEditorExporter;
import com.github.bandithelps.gui.tree.TreeEditorNode;
import com.github.bandithelps.gui.tree.TreeEditorSettings;
import com.github.bandithelps.gui.tree.TreeEditorStateSync;
import com.github.bandithelps.gui.tree.TreeConnectionPath;
import com.github.bandithelps.gui.tree.TreeConnectionRenderer;
import com.github.bandithelps.gui.tree.schema.DocField;
import com.github.bandithelps.gui.tree.schema.DocFieldKind;
import com.github.bandithelps.gui.tree.schema.DocSchema;
import com.github.bandithelps.gui.tree.schema.PalladiumDocCatalog;
import com.github.bandithelps.gui.tree.TreeEditorJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec2;
import net.threetag.palladium.client.gui.ui.background.RepeatingTextureBackground;
import net.threetag.palladium.client.renderer.icon.IconRenderer;
import net.threetag.palladium.client.texture.TextureReference;
import net.threetag.palladium.logic.context.DataContext;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class TreeEditorScreen extends Screen {
    private static final int GRID_SIZE = 50;
    private static final int NODE_HIT = 13;
    private static final int INSPECTOR_MIN = 240;
    private static final int INSPECTOR_MAX = 320;
    private static final int MIN_TREE_WIDTH = 280;
    private static final int MIN_TREE_HEIGHT = 180;
    private static final float STEP_MIN = 0.05F;
    private static final float STEP_MAX = 2.0F;
    private static final float STEP_TICK = 0.05F;
    private static final float ZOOM_MIN = 0.35F;
    private static final float ZOOM_MAX = 2.5F;
    private static final int LINE_CYAN = 0xFF79C8D6;
    private static final int LINE_HOVER = 0xFFB8EAF2;
    private static final int LINE_OUTLINE = 0xFF0A0B0D;
    private static final int TEXT_LIGHT = TreeEditorTheme.TEXT;
    private static final int HANDLE_HOVER = 0xFFFFFF66;
    private static final int HANDLE_DEFAULT = 0xFF00FFFF;

    private enum Menu {
        NONE,
        FILE,
        VIEW
    }
    private static final Identifier FRAME_UNLOCKED = Identifier.fromNamespaceAndPath("palladium", "powers/ability_frame_unlocked");
    private static final Identifier FRAME_LOCKED = Identifier.fromNamespaceAndPath("palladium", "powers/ability_frame_locked");
    private static final Identifier VIGNETTE = Identifier.fromNamespaceAndPath("palladium", "powers/vignette");

    private TreeEditorDraft draft;
    private PalladiumDocCatalog catalog = new PalladiumDocCatalog(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    private List<TreeEditorCostSchema> costSchemas = List.of();
    private final TreeEditorInspector inspector = new TreeEditorInspector(this);
    @Nullable
    private RepeatingTextureBackground treeBackground;
    private int treeX;
    private int treeY;
    private int treeW;
    private int treeH;
    private int inspectorX;
    private int inspectorY;
    private int inspectorW;
    private int inspectorH;
    private Menu openMenu = Menu.NONE;
    private int panX;
    private int panY;
    private boolean viewReady;
    private boolean showGrid;
    private boolean showGridNumbers;
    private boolean showNodeHover;
    private boolean showHidden;
    private float stepSize = TreeEditorDraft.GRID_SNAP;
    private float zoom = 1.0F;
    private boolean panning;
    @Nullable
    private TreeEditorNode dragging;
    private final List<TreeEditorNode> dragGroup = new ArrayList<>();
    private final List<Float> dragStartX = new ArrayList<>();
    private final List<Float> dragStartY = new ArrayList<>();
    private float dragAnchorX;
    private float dragAnchorY;
    private boolean dragMoved;
    private boolean pendingSelectionCollapse;
    @Nullable
    private TreeEditorNode pendingCollapseNode;
    private final List<VertexPick> dragVertexGroup = new ArrayList<>();
    private final List<Float> dragVertexStartX = new ArrayList<>();
    private final List<Float> dragVertexStartY = new ArrayList<>();
    private final LinkedHashSet<TreeEditorNode> selection = new LinkedHashSet<>();
    private final LinkedHashSet<VertexPick> selectedVertices = new LinkedHashSet<>();
    private boolean boxing;
    private int boxStartX;
    private int boxStartY;
    private int boxEndX;
    private int boxEndY;
    private final LinkedHashSet<TreeEditorNode> boxBase = new LinkedHashSet<>();
    private final LinkedHashSet<VertexPick> boxBaseVertices = new LinkedHashSet<>();
    @Nullable
    private SegmentPick selectedConnection;
    @Nullable
    private TreeEditorNode parentLinkSource;
    @Nullable
    private ContextMenu contextMenu;
    private String status = "Right-click the tree to add abilities. Properties edit on the right.";

    public TreeEditorScreen(TreeEditorDraft draft) {
        super(Component.literal("Power Tree Editor"));
        this.draft = draft;
    }

    @Override
    protected void init() {
        super.init();
        this.layoutChrome();
        if (this.minecraft != null) {
            if (this.catalog.abilities().isEmpty() && this.catalog.costs().isEmpty()) {
                this.catalog = PalladiumDocCatalog.load(this.minecraft);
                this.costSchemas = this.catalog.costs();
            }
            this.applyBackground(TreeEditorSettings.background(this.minecraft), false);
            if (!this.viewReady) {
                this.stepSize = TreeEditorSettings.step(this.minecraft);
            }
        }
        if (!this.viewReady) {
            this.panX = 0;
            this.panY = 0;
            this.viewReady = true;
        }
        this.addMenuButtons();
        this.inspector.rebuild();
        this.addStatusSlider();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.layoutChrome();
        graphics.fill(0, 0, this.width, this.height, TreeEditorTheme.BG);
        this.drawMenuBar(graphics);
        this.drawTreeBackground(graphics);
        TreeEditorTheme.border(graphics, this.treeX, this.treeY, this.treeW, this.treeH, TreeEditorTheme.BORDER);

        graphics.enableScissor(this.treeX, this.treeY, this.treeX + this.treeW, this.treeY + this.treeH);
        if (this.showGrid) {
            this.drawGrid(graphics);
        }
        TreeEditorNode hovered = this.nodeAt(mouseX, mouseY);
        VertexPick hoveredVertex = this.hoveredVertex(mouseX, mouseY);
        SegmentPick hoveredSegment = hoveredVertex == null && hovered == null ? this.hoveredSegment(mouseX, mouseY) : null;
        this.drawConnections(graphics, hoveredSegment);
        for (TreeEditorNode node : this.displayedNodes()) {
            this.drawNode(graphics, node);
        }
        this.drawWaypointHandles(graphics, hoveredVertex, hoveredSegment);
        if (this.boxing) {
            this.drawSelectionBox(graphics);
        }
        graphics.disableScissor();

        this.blitSprite(graphics, VIGNETTE, this.treeX, this.treeY, this.treeW, this.treeH);
        this.inspector.draw(graphics);
        this.drawStatusBar(graphics);

        if (this.showNodeHover && hovered != null) {
            this.drawTitleBox(graphics, hovered);
        }
        if (this.openMenu != Menu.NONE) {
            this.drawMenuDropdown(graphics, mouseX, mouseY);
        }
        this.renderWidgetsClipped(graphics, mouseX, mouseY, partialTick);
        if (this.contextMenu != null) {
            this.contextMenu.draw(graphics, this.font);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        if (this.contextMenu != null) {
            this.contextMenu.click(mouseX, mouseY);
            this.contextMenu = null;
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (this.openMenu != Menu.NONE) {
            if (this.clickMenuDropdown(mouseX, mouseY)) {
                this.openMenu = Menu.NONE;
                return true;
            }
            this.openMenu = Menu.NONE;
            if (mouseY < TreeEditorTheme.MENU_H) {
                return true;
            }
        }
        if (this.isInInspector(mouseX, mouseY) && this.inspector.mouseClicked(mouseX, mouseY)) {
            return true;
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
            this.clearVertexDrag();
            this.panning = false;
            this.boxing = false;
            this.boxBase.clear();
            this.boxBaseVertices.clear();
            if (vertex != null) {
                this.contextMenu = ContextMenu.forVertex(this, vertex.child(), vertex.parentKey(), vertex.index(), mouseX, mouseY);
            } else if (hit != null) {
                if (!this.selection.contains(hit)) {
                    this.selectNode(hit);
                }
                this.contextMenu = ContextMenu.forNode(this, hit, mouseX, mouseY);
            } else if (segment != null) {
                this.contextMenu = ContextMenu.forSegment(this, segment.child(), segment.parentKey(), segment.segmentIndex(), mouseX, mouseY);
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
                if (this.draft.addParent(this.parentLinkSource, target, this.costSchemas)) {
                    this.status = "Added parent " + target.getKey() + " to " + this.parentLinkSource.getKey();
                } else {
                    this.status = "Could not add parent " + target.getKey() + " (already linked, cycle, or self).";
                }
            }
            this.parentLinkSource = null;
            this.selectNode(target);
            return true;
        }
        if (vertex != null) {
            this.selectedConnection = null;
            this.beginVertexDrag(vertex);
            return true;
        }
        if (hit != null) {
            boolean ctrl = event.hasControlDown();
            this.selectedConnection = null;
            if (ctrl) {
                this.toggleSelect(hit);
                if (this.selection.contains(hit)) {
                    this.beginNodeDrag(hit);
                }
            } else if (this.selection.contains(hit) && this.selection.size() > 1) {
                this.pendingSelectionCollapse = true;
                this.pendingCollapseNode = hit;
                this.beginNodeDrag(hit);
            } else {
                this.selectNode(hit);
                this.beginNodeDrag(hit);
            }
            return true;
        }
        if (segment != null && !event.hasControlDown()) {
            if (doubleClick) {
                this.insertVertex(segment.child(), segment.parentKey(), segment.segmentIndex(), this.screenToGridX(mouseX), this.screenToGridY(mouseY));
            } else {
                this.selectedVertices.clear();
                this.selectedConnection = segment;
            }
            return true;
        }
        if (event.hasControlDown()) {
            this.beginBoxSelect(mouseX, mouseY);
            return true;
        }
        this.selectNode(null);
        this.panning = true;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if ((this.dragging != null || !this.dragVertexGroup.isEmpty()) && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.dragMoved = true;
            this.pendingSelectionCollapse = false;
            float gridX = this.snap(this.screenToGridX((int) event.x()));
            float gridY = this.snap(this.screenToGridY((int) event.y()));
            float dx = gridX - this.dragAnchorX;
            float dy = gridY - this.dragAnchorY;
            for (int index = 0; index < this.dragGroup.size(); index++) {
                this.dragGroup.get(index).setGrid(this.dragStartX.get(index) + dx, this.dragStartY.get(index) + dy);
            }
            this.applyVertexDrag(dx, dy);
            this.draft.markDirty();
            return true;
        }
        if (this.boxing && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.updateBoxSelect((int) event.x(), (int) event.y());
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
        if (this.pendingSelectionCollapse && !this.dragMoved && this.pendingCollapseNode != null) {
            this.selectNode(this.pendingCollapseNode);
        }
        if (this.boxing) {
            this.finishBoxSelect();
        }
        this.dragging = null;
        this.dragGroup.clear();
        this.dragStartX.clear();
        this.dragStartY.clear();
        this.clearVertexDrag();
        this.dragMoved = false;
        this.pendingSelectionCollapse = false;
        this.pendingCollapseNode = null;
        this.panning = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.inspector.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (this.isInTree((int) mouseX, (int) mouseY)) {
            this.zoomAt((int) mouseX, (int) mouseY, scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && this.parentLinkSource != null) {
            this.parentLinkSource = null;
            this.status = "Parent mode cancelled.";
            return true;
        }
        boolean ctrl = event.hasControlDown();
        if (ctrl && event.key() == GLFW.GLFW_KEY_S) {
            this.openSave();
            return true;
        }
        if (ctrl && event.key() == GLFW.GLFW_KEY_O) {
            this.openLoad();
            return true;
        }
        if (ctrl && event.key() == GLFW.GLFW_KEY_N) {
            this.openNew();
            return true;
        }
        if (ctrl && event.key() == GLFW.GLFW_KEY_D && !this.selection.isEmpty() && !(this.getFocused() instanceof TreeEditorFieldBox)) {
            this.duplicateNode(this.selectedNode());
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DELETE && !this.selection.isEmpty() && !(this.getFocused() instanceof TreeEditorFieldBox)) {
            this.deleteNode(this.selectedNode());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void beginParentLink(TreeEditorNode node) {
        this.selectNode(node);
        this.parentLinkSource = node;
        this.status = "Click a node to add as a parent of '" + node.getKey() + "'.";
    }

    public void unparent(TreeEditorNode node) {
        List<TreeEditorNode> targets = this.actionTargets(node);
        for (TreeEditorNode target : targets) {
            this.draft.clearParents(target, this.costSchemas);
            this.selectedVertices.removeIf(vertex -> vertex.child() == target);
            if (this.selectedConnection != null && this.selectedConnection.child() == target) {
                this.selectedConnection = null;
            }
        }
        this.status = targets.size() == 1
                ? "Removed parents from " + node.getKey()
                : "Removed parents from " + targets.size() + " nodes";
    }

    public void addNodeAt(int mouseX, int mouseY) {
        TreeEditorNode node = this.draft.addDummy(this.snap(this.screenToGridX(mouseX)), this.snap(this.screenToGridY(mouseY)));
        this.selectNode(node);
        this.status = "Added dummy. Edit it in the inspector.";
    }

    public void addAbilityAt(int mouseX, int mouseY) {
        if (this.minecraft == null) {
            return;
        }
        float gridX = this.snap(this.screenToGridX(mouseX));
        float gridY = this.snap(this.screenToGridY(mouseY));
        this.minecraft.setScreen(new TreeEditorTypePickerScreen(this, this.catalog.abilities(), typeId -> {
            TreeEditorNode node = this.draft.addAbility(typeId, gridX, gridY);
            this.selectNode(node);
            this.status = "Added " + typeId + ". Edit it in the inspector.";
        }));
    }

    public void toggleHidden(TreeEditorNode node) {
        List<TreeEditorNode> targets = this.actionTargets(node);
        boolean hide = !node.isHiddenInGui();
        this.setNodesHiddenInTree(targets, hide);
        this.status = hide
                ? (targets.size() == 1 ? "Hidden " + node.getKey() : "Hidden " + targets.size() + " nodes")
                : (targets.size() == 1 ? "Showing " + node.getKey() : "Showing " + targets.size() + " nodes");
    }

    public void setNodesHiddenInTree(List<TreeEditorNode> nodes, boolean hidden) {
        for (TreeEditorNode node : nodes) {
            node.setHiddenInGui(hidden);
            if (hidden && !this.showHidden) {
                this.selection.remove(node);
            }
        }
        this.draft.markDirty();
        this.refreshWidgets();
    }

    public void setNodesHiddenInBar(List<TreeEditorNode> nodes, boolean hidden) {
        for (TreeEditorNode node : nodes) {
            node.setHiddenInBar(hidden);
        }
        this.draft.markDirty();
        this.refreshWidgets();
    }

    public void replaceDraft(TreeEditorDraft draft) {
        this.draft = draft;
        this.selection.clear();
        this.selectedVertices.clear();
        this.selectedConnection = null;
        this.dragging = null;
        this.dragGroup.clear();
        this.clearVertexDrag();
        this.boxing = false;
        this.boxBase.clear();
        this.boxBaseVertices.clear();
        this.parentLinkSource = null;
        this.viewReady = false;
        this.status = "Loaded " + draft.getPowerName();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this);
            this.init(this.width, this.height);
        }
    }

    public void duplicateNode(TreeEditorNode node) {
        List<TreeEditorNode> targets = this.actionTargets(node);
        this.selection.clear();
        this.selectedVertices.clear();
        this.selectedConnection = null;
        TreeEditorNode last = null;
        for (TreeEditorNode target : targets) {
            last = this.draft.duplicate(target);
            this.selection.add(last);
        }
        this.status = targets.size() == 1
                ? "Duplicated " + node.getKey() + " as " + (last == null ? "" : last.getKey())
                : "Duplicated " + targets.size() + " nodes";
        this.inspector.resetScroll();
        this.refreshWidgets();
    }

    public void deleteNode(TreeEditorNode node) {
        if (node == null) {
            return;
        }
        List<TreeEditorNode> targets = this.actionTargets(node);
        int count = targets.size();
        String key = node.getKey();
        for (TreeEditorNode target : List.copyOf(targets)) {
            this.removeNodeQuiet(target);
        }
        this.status = count == 1 ? "Deleted " + key : "Deleted " + count + " nodes";
        this.refreshWidgets();
    }

    public void discardCreatedNode(TreeEditorNode node) {
        this.removeNodeQuiet(node);
        this.status = "Cancelled new node.";
        this.refreshWidgets();
    }

    private void removeNodeQuiet(TreeEditorNode node) {
        this.draft.remove(node);
        this.selection.remove(node);
        this.selectedVertices.removeIf(vertex -> vertex.child() == node || node.getKey().equals(vertex.parentKey()));
        this.dragVertexGroup.removeIf(vertex -> vertex.child() == node || node.getKey().equals(vertex.parentKey()));
        if (this.selectedConnection != null
                && (this.selectedConnection.child() == node || node.getKey().equals(this.selectedConnection.parentKey()))) {
            this.selectedConnection = null;
        }
        if (this.dragging == node) {
            this.dragging = null;
        }
        this.dragGroup.remove(node);
    }

    public void insertVertex(TreeEditorNode child, String parentKey, int segmentIndex, float gridX, float gridY) {
        TreeEditorNode parent = this.draft.find(parentKey);
        if (parent == null) {
            return;
        }
        List<Vec2> points = this.fullGridPoints(parent, child);
        int insertAt = Math.max(1, Math.min(segmentIndex + 1, points.size() - 1));
        points.add(insertAt, new Vec2(this.snap(gridX), this.snap(gridY)));
        child.setConnectionPath(parentKey, this.waypointsFromFullPath(points));
        this.clearVerticesOnPath(child, parentKey);
        this.draft.markDirty();
        this.status = "Added vertex on " + child.getKey() + " → " + parentKey;
    }

    public void deleteVertex(TreeEditorNode child, String parentKey, int index) {
        child.setConnectionPath(parentKey, child.getConnectionPath(parentKey).withRemoved(index));
        this.clearVerticesOnPath(child, parentKey);
        this.clearVertexDrag();
        this.draft.markDirty();
        this.status = child.getConnectionPath(parentKey).isEmpty()
                ? "Removed path from " + child.getKey() + " → " + parentKey + "; using default bus."
                : "Deleted vertex on " + child.getKey() + " → " + parentKey;
    }

    public void exportDraft() {
        this.openSave();
    }

    public void openSave() {
        if (this.draft.hasSaveName()) {
            this.saveToFile(this.draft.getExportFileName());
            return;
        }
        this.openSaveAs();
    }

    public void openSaveAs() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new TreeEditorSavePopupScreen(this, this.draft, this.costSchemas));
        }
    }

    public void openLoad() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new TreeEditorLoadPopupScreen(this, this.draft.isDirty()));
        }
    }

    public void saveToFile(String fileName) {
        if (this.minecraft == null) {
            return;
        }
        try {
            this.draft.setExportFileName(fileName);
            this.draft.markSaveNamed();
            String json = TreeEditorExporter.toJson(this.draft, this.costSchemas);
            Path file = TreeEditorExporter.writeToGameDir(this.minecraft, this.draft, json);
            this.draft.markClean();
            String path = file.toAbsolutePath().toString();
            this.status = "Saved to " + path;
            if (this.minecraft.screen != this) {
                this.minecraft.setScreen(this);
            }
        } catch (Exception exception) {
            this.status = "Save failed: " + exception.getMessage();
        }
    }

    private void layoutChrome() {
        this.inspectorW = Math.max(INSPECTOR_MIN, Math.min(INSPECTOR_MAX, this.width / 3));
        this.treeX = 0;
        this.treeY = TreeEditorTheme.MENU_H;
        this.treeW = Math.max(MIN_TREE_WIDTH, this.width - this.inspectorW);
        this.treeH = Math.max(MIN_TREE_HEIGHT, this.height - TreeEditorTheme.MENU_H - TreeEditorTheme.STATUS_H);
        this.inspectorX = this.treeX + this.treeW;
        this.inspectorY = TreeEditorTheme.MENU_H;
        this.inspectorH = this.treeH;
        if (this.treeX + this.treeW + this.inspectorW > this.width) {
            this.treeW = Math.max(MIN_TREE_WIDTH, this.width - this.inspectorW);
            this.inspectorX = this.treeX + this.treeW;
        }
    }

    private void drawConnections(GuiGraphicsExtractor graphics, @Nullable SegmentPick hovered) {
        for (TreeEditorNode parent : this.displayedNodes()) {
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
                if (!this.isDisplayed(child) || !this.usesCustomPath(parent, child)) {
                    continue;
                }
                List<TreeConnectionRenderer.Pixel> pixels = this.toScreenPixels(this.fullGridPoints(parent, child));
                TreeConnectionRenderer.drawPolyline(graphics, pixels, true, LINE_OUTLINE);
                TreeConnectionRenderer.drawPolyline(graphics, pixels, false, LINE_CYAN);
            }
        }
        if (hovered != null && !this.sameConnection(hovered, this.selectedConnection)) {
            this.drawHighlightedConnection(graphics, hovered.child(), hovered.parentKey(), LINE_HOVER);
        }
        if (this.selectedConnection != null) {
            this.drawHighlightedConnection(graphics, this.selectedConnection.child(), this.selectedConnection.parentKey(), TreeEditorTheme.ACCENT);
        }
    }

    private void drawHighlightedConnection(GuiGraphicsExtractor graphics, TreeEditorNode child, String parentKey, int fill) {
        TreeEditorNode parent = this.draft.find(parentKey);
        if (parent == null || !this.isDisplayed(parent) || !this.isDisplayed(child)) {
            return;
        }
        List<TreeConnectionRenderer.Pixel> pixels = this.usesCustomPath(parent, child)
                ? this.toScreenPixels(this.fullGridPoints(parent, child))
                : this.defaultBusPixels(parent, child);
        TreeConnectionRenderer.drawPolyline(graphics, pixels, true, LINE_OUTLINE);
        TreeConnectionRenderer.drawPolyline(graphics, pixels, false, fill);
    }

    private boolean sameConnection(@Nullable SegmentPick left, @Nullable SegmentPick right) {
        return left != null && right != null && left.child() == right.child() && left.parentKey().equals(right.parentKey());
    }

    private void drawWaypointHandles(GuiGraphicsExtractor graphics, @Nullable VertexPick hovered, @Nullable SegmentPick hoveredSegment) {
        for (TreeEditorNode child : this.displayedNodes()) {
            for (String parentKey : child.getParentKeys()) {
                TreeConnectionPath path = child.getConnectionPath(parentKey);
                if (path.isEmpty()) {
                    continue;
                }
                boolean lineSelected = this.connectionMatches(this.selectedConnection, child, parentKey);
                boolean lineHover = this.connectionMatches(hoveredSegment, child, parentKey)
                        && !this.sameConnection(hoveredSegment, this.selectedConnection);
                List<Vec2> waypoints = path.waypoints();
                for (int index = 0; index < waypoints.size(); index++) {
                    Vec2 point = waypoints.get(index);
                    VertexPick pick = new VertexPick(child, parentKey, index);
                    boolean highlighted = hovered != null && hovered.equals(pick);
                    boolean selected = this.selectedVertices.contains(pick) || this.isBoxSelectingVertex(pick);
                    int fill = highlighted ? HANDLE_HOVER
                            : (selected || lineSelected) ? TreeEditorTheme.ACCENT
                            : lineHover ? LINE_HOVER
                            : HANDLE_DEFAULT;
                    TreeConnectionRenderer.drawHandle(
                            graphics,
                            this.gridToScreenX(point.x),
                            this.gridToScreenY(point.y),
                            fill
                    );
                }
            }
        }
    }

    private boolean connectionMatches(@Nullable SegmentPick pick, TreeEditorNode child, String parentKey) {
        return pick != null && pick.child() == child && parentKey.equals(pick.parentKey());
    }

    private void drawNode(GuiGraphicsExtractor graphics, TreeEditorNode node) {
        int x = this.nodeScreenX(node);
        int y = this.nodeScreenY(node);
        int hit = this.nodeHit();
        Identifier frame = node.isCreated() ? FRAME_LOCKED : FRAME_UNLOCKED;
        this.blitSprite(graphics, frame, x - hit, y - hit, hit * 2, hit * 2);
        if (hit >= 10 && node.getIcon() != null && this.minecraft != null && this.minecraft.player != null) {
            IconRenderer.drawIcon(node.getIcon(), this.minecraft, graphics, DataContext.forEntity(this.minecraft.player), x - 8, y - 8);
        }
        if (this.selection.contains(node) || this.isBoxSelecting(node)) {
            graphics.fill(x - hit - 2, y - hit - 2, x + hit + 2, y - hit, TreeEditorTheme.ACCENT);
            graphics.fill(x - hit - 2, y + hit, x + hit + 2, y + hit + 2, TreeEditorTheme.ACCENT);
            graphics.fill(x - hit - 2, y - hit, x - hit, y + hit, TreeEditorTheme.ACCENT);
            graphics.fill(x + hit, y - hit, x + hit + 2, y + hit, TreeEditorTheme.ACCENT);
        }
        if (node.isHiddenInGui()) {
            graphics.fill(x - hit, y - hit, x + hit, y + hit, 0x88000000);
            graphics.text(this.font, "H", x - 3, y - 4, TreeEditorTheme.ACCENT, false);
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
        int hit = this.nodeHit();
        int boxX = x + hit + 2;
        int boxY = y - hit;
        if (boxX + boxWidth > this.treeX + this.treeW) {
            boxX = x - hit - 2 - boxWidth;
        }
        if (boxY + boxHeight > this.treeY + this.treeH) {
            boxY = Math.max(this.treeY, this.treeY + this.treeH - boxHeight);
        }
        TreeEditorTheme.rect(graphics, boxX, boxY, boxWidth, boxHeight, TreeEditorTheme.PANEL, TreeEditorTheme.ACCENT_DIM);
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
        for (TreeEditorNode node : this.displayedNodes()) {
            if (this.isHoveringNode(node, mouseX, mouseY)) {
                hit = node;
            }
        }
        return hit;
    }

    private boolean isHoveringNode(TreeEditorNode node, int mouseX, int mouseY) {
        int x = this.nodeScreenX(node);
        int y = this.nodeScreenY(node);
        int hit = this.nodeHit();
        return mouseX >= x - hit && mouseX <= x + hit && mouseY >= y - hit && mouseY <= y + hit;
    }

    private boolean isInTree(int mouseX, int mouseY) {
        return mouseX >= this.treeX && mouseX < this.treeX + this.treeW
                && mouseY >= this.treeY && mouseY < this.treeY + this.treeH;
    }

    public boolean isInInspector(int mouseX, int mouseY) {
        return mouseX >= this.inspectorX && mouseX < this.inspectorX + this.inspectorW
                && mouseY >= this.inspectorY && mouseY < this.inspectorY + this.inspectorH;
    }

    private int nodeScreenX(TreeEditorNode node) {
        return this.originX() + Math.round(node.getGridX() * this.cellSize()) - this.panX;
    }

    private int nodeScreenY(TreeEditorNode node) {
        return this.originY() + Math.round(node.getGridY() * this.cellSize()) - this.panY;
    }

    private float screenToGridX(int mouseX) {
        return (mouseX - this.originX() + this.panX) / this.cellSize();
    }

    private float screenToGridY(int mouseY) {
        return (mouseY - this.originY() + this.panY) / this.cellSize();
    }

    private float cellSize() {
        return GRID_SIZE * this.zoom;
    }

    private int nodeHit() {
        return Math.max(6, Math.round(NODE_HIT * this.zoom));
    }

    private void zoomAt(int mouseX, int mouseY, double scrollY) {
        float oldZoom = this.zoom;
        float factor = scrollY > 0 ? 1.12F : 1.0F / 1.12F;
        this.zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, this.zoom * factor));
        if (Float.compare(oldZoom, this.zoom) == 0) {
            return;
        }
        float gridX = (mouseX - this.originX() + this.panX) / (GRID_SIZE * oldZoom);
        float gridY = (mouseY - this.originY() + this.panY) / (GRID_SIZE * oldZoom);
        this.panX = this.originX() + Math.round(gridX * this.cellSize()) - mouseX;
        this.panY = this.originY() + Math.round(gridY * this.cellSize()) - mouseY;
    }

    private void renderWidgetsClipped(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.enableScissor(this.inspector.clipLeft(), this.inspector.clipTop(), this.inspector.clipRight(), this.inspector.clipBottom());
        for (var child : this.children()) {
            if (child instanceof AbstractWidget widget && widget.visible && this.inspector.isInspectorWidget(widget)) {
                widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }
        }
        graphics.disableScissor();
        for (var child : this.children()) {
            if (child instanceof AbstractWidget widget && widget.visible && !this.inspector.isInspectorWidget(widget)) {
                widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private int originX() {
        return this.treeX + this.treeW / 2;
    }

    private int originY() {
        return this.treeY + this.treeH / 2;
    }

    private int gridToScreenX(float gridX) {
        return this.originX() + Math.round(gridX * this.cellSize()) - this.panX;
    }

    private int gridToScreenY(float gridY) {
        return this.originY() + Math.round(gridY * this.cellSize()) - this.panY;
    }

    private List<TreeEditorNode> defaultChildrenOf(TreeEditorNode parent) {
        List<TreeEditorNode> children = new ArrayList<>();
        for (TreeEditorNode child : this.draft.childrenOf(parent)) {
            if (this.isDisplayed(child) && !this.usesCustomPath(parent, child)) {
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
        TreeConnectionPath path = child.getConnectionPath(parent.getKey());
        if (path.isEmpty()) {
            float busGridX = this.snap(this.screenToGridX(this.busScreenX(parent)));
            points.add(new Vec2(busGridX, parent.getGridY()));
            points.add(new Vec2(busGridX, child.getGridY()));
        } else {
            for (Vec2 waypoint : path.waypoints()) {
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
        for (TreeEditorNode child : this.displayedNodes()) {
            for (String parentKey : child.getParentKeys()) {
                TreeConnectionPath path = child.getConnectionPath(parentKey);
                if (path.isEmpty()) {
                    continue;
                }
                List<Vec2> waypoints = path.waypoints();
                for (int index = 0; index < waypoints.size(); index++) {
                    Vec2 point = waypoints.get(index);
                    if (TreeConnectionRenderer.hitsHandle(mouseX, mouseY, this.gridToScreenX(point.x), this.gridToScreenY(point.y))) {
                        hit = new VertexPick(child, parentKey, index);
                    }
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
        for (TreeEditorNode child : this.displayedNodes()) {
            for (String parentKey : child.getParentKeys()) {
                TreeEditorNode parent = this.draft.find(parentKey);
                if (parent == null || !this.isDisplayed(parent) || !this.isDisplayed(child)) {
                    continue;
                }
                List<TreeConnectionRenderer.Pixel> pixels = this.usesCustomPath(parent, child)
                        ? this.toScreenPixels(this.fullGridPoints(parent, child))
                        : this.defaultBusPixels(parent, child);
                TreeConnectionRenderer.SegmentHit hit = TreeConnectionRenderer.hitTest(pixels, mouseX, mouseY, bestDistance);
                if (hit != null) {
                    bestDistance = hit.distance();
                    best = new SegmentPick(child, parentKey, hit.segmentIndex());
                }
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

    public PalladiumDocCatalog catalog() {
        return this.catalog;
    }

    public TreeEditorDraft draft() {
        return this.draft;
    }

    @Nullable
    public TreeEditorNode selectedNode() {
        TreeEditorNode last = null;
        for (TreeEditorNode node : this.selection) {
            last = node;
        }
        return last;
    }

    public List<TreeEditorNode> selectedNodes() {
        return List.copyOf(this.selection);
    }

    public int selectionSize() {
        return this.selection.size();
    }

    public Font getFont() {
        return this.font;
    }

    public <T extends AbstractWidget> T addEditorWidget(T widget) {
        return this.addRenderableWidget(widget);
    }

    public int inspectorX() {
        return this.inspectorX;
    }

    public int inspectorY() {
        return this.inspectorY;
    }

    public int inspectorW() {
        return this.inspectorW;
    }

    public int inspectorH() {
        return this.inspectorH;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void openTextEditor(String title, String value, int maxLength, java.util.function.Consumer<String> onSave) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new TreeEditorTextEditScreen(this, title, value, maxLength, onSave));
        }
    }

    public void selectNode(@Nullable TreeEditorNode node) {
        boolean changed;
        if (node == null) {
            changed = !this.selection.isEmpty();
            this.selection.clear();
            this.selectedVertices.clear();
            this.selectedConnection = null;
        } else if (this.selection.size() == 1 && this.selection.contains(node)) {
            changed = false;
            this.selectedConnection = null;
        } else {
            changed = true;
            this.selection.clear();
            this.selection.add(node);
            this.selectedVertices.clear();
            this.selectedConnection = null;
        }
        if (changed) {
            this.inspector.resetScroll();
            this.refreshWidgets();
        }
    }

    private void toggleSelect(TreeEditorNode node) {
        if (this.selection.contains(node)) {
            this.selection.remove(node);
        } else {
            this.selection.add(node);
        }
        this.inspector.resetScroll();
        this.refreshWidgets();
    }

    private void beginNodeDrag(TreeEditorNode grabbed) {
        this.dragging = grabbed;
        this.dragMoved = false;
        this.dragGroup.clear();
        this.dragStartX.clear();
        this.dragStartY.clear();
        List<TreeEditorNode> group = this.selection.contains(grabbed) ? List.copyOf(this.selection) : List.of(grabbed);
        for (TreeEditorNode node : group) {
            this.dragGroup.add(node);
            this.dragStartX.add(node.getGridX());
            this.dragStartY.add(node.getGridY());
        }
        this.snapshotSelectedVertices();
        this.dragAnchorX = grabbed.getGridX();
        this.dragAnchorY = grabbed.getGridY();
    }

    private void beginVertexDrag(VertexPick vertex) {
        this.dragging = null;
        this.dragMoved = false;
        this.dragGroup.clear();
        this.dragStartX.clear();
        this.dragStartY.clear();
        if (this.selectedVertices.contains(vertex)) {
            for (TreeEditorNode node : this.selection) {
                this.dragGroup.add(node);
                this.dragStartX.add(node.getGridX());
                this.dragStartY.add(node.getGridY());
            }
            this.snapshotSelectedVertices();
        } else {
            this.selectedVertices.clear();
            this.selectedVertices.add(vertex);
            this.snapshotSelectedVertices();
        }
        TreeConnectionPath path = vertex.child().getConnectionPath(vertex.parentKey());
        if (vertex.index() < 0 || vertex.index() >= path.size()) {
            this.clearVertexDrag();
            return;
        }
        Vec2 point = path.get(vertex.index());
        this.dragAnchorX = point.x;
        this.dragAnchorY = point.y;
    }

    private void snapshotSelectedVertices() {
        this.dragVertexGroup.clear();
        this.dragVertexStartX.clear();
        this.dragVertexStartY.clear();
        for (VertexPick vertex : this.selectedVertices) {
            TreeConnectionPath path = vertex.child().getConnectionPath(vertex.parentKey());
            if (vertex.index() < 0 || vertex.index() >= path.size()) {
                continue;
            }
            Vec2 point = path.get(vertex.index());
            this.dragVertexGroup.add(vertex);
            this.dragVertexStartX.add(point.x);
            this.dragVertexStartY.add(point.y);
        }
    }

    private void applyVertexDrag(float dx, float dy) {
        for (int index = 0; index < this.dragVertexGroup.size(); index++) {
            VertexPick vertex = this.dragVertexGroup.get(index);
            vertex.child().setConnectionPath(
                    vertex.parentKey(),
                    vertex.child().getConnectionPath(vertex.parentKey())
                            .withReplaced(vertex.index(), this.dragVertexStartX.get(index) + dx, this.dragVertexStartY.get(index) + dy)
            );
        }
    }

    private void clearVertexDrag() {
        this.dragVertexGroup.clear();
        this.dragVertexStartX.clear();
        this.dragVertexStartY.clear();
    }

    private void clearVerticesOnPath(TreeEditorNode child, String parentKey) {
        this.selectedVertices.removeIf(vertex -> vertex.child() == child && parentKey.equals(vertex.parentKey()));
        this.dragVertexGroup.removeIf(vertex -> vertex.child() == child && parentKey.equals(vertex.parentKey()));
    }

    private void beginBoxSelect(int mouseX, int mouseY) {
        this.boxing = true;
        this.panning = false;
        this.boxStartX = this.boxEndX = this.clampTreeX(mouseX);
        this.boxStartY = this.boxEndY = this.clampTreeY(mouseY);
        this.boxBase.clear();
        this.boxBase.addAll(this.selection);
        this.boxBaseVertices.clear();
        this.boxBaseVertices.addAll(this.selectedVertices);
    }

    private void updateBoxSelect(int mouseX, int mouseY) {
        this.boxEndX = this.clampTreeX(mouseX);
        this.boxEndY = this.clampTreeY(mouseY);
    }

    private void finishBoxSelect() {
        if (!this.boxing) {
            return;
        }
        this.boxing = false;
        LinkedHashSet<TreeEditorNode> next = new LinkedHashSet<>(this.boxBase);
        for (TreeEditorNode node : this.displayedNodes()) {
            if (this.nodeIntersectsBox(node)) {
                next.add(node);
            }
        }
        LinkedHashSet<VertexPick> nextVertices = new LinkedHashSet<>(this.boxBaseVertices);
        this.collectVerticesInBox(nextVertices);
        this.boxBase.clear();
        this.boxBaseVertices.clear();
        boolean changed = next.size() != this.selection.size() || !this.selection.containsAll(next);
        this.selectedVertices.clear();
        this.selectedVertices.addAll(nextVertices);
        if (changed) {
            this.selection.clear();
            this.selection.addAll(next);
            this.inspector.resetScroll();
            this.refreshWidgets();
        }
    }

    private boolean isBoxSelecting(TreeEditorNode node) {
        return this.boxing && (this.boxBase.contains(node) || this.nodeIntersectsBox(node));
    }

    private boolean nodeIntersectsBox(TreeEditorNode node) {
        int x0 = Math.min(this.boxStartX, this.boxEndX);
        int y0 = Math.min(this.boxStartY, this.boxEndY);
        int x1 = Math.max(this.boxStartX, this.boxEndX);
        int y1 = Math.max(this.boxStartY, this.boxEndY);
        int x = this.nodeScreenX(node);
        int y = this.nodeScreenY(node);
        int hit = this.nodeHit();
        return x + hit >= x0 && x - hit <= x1 && y + hit >= y0 && y - hit <= y1;
    }

    private boolean isBoxSelectingVertex(VertexPick vertex) {
        if (!this.boxing) {
            return false;
        }
        if (this.boxBaseVertices.contains(vertex)) {
            return true;
        }
        TreeConnectionPath path = vertex.child().getConnectionPath(vertex.parentKey());
        if (vertex.index() < 0 || vertex.index() >= path.size()) {
            return false;
        }
        Vec2 point = path.get(vertex.index());
        return this.handleIntersectsBox(this.gridToScreenX(point.x), this.gridToScreenY(point.y));
    }

    private void collectVerticesInBox(LinkedHashSet<VertexPick> into) {
        for (TreeEditorNode child : this.displayedNodes()) {
            for (String parentKey : child.getParentKeys()) {
                TreeConnectionPath path = child.getConnectionPath(parentKey);
                if (path.isEmpty()) {
                    continue;
                }
                List<Vec2> waypoints = path.waypoints();
                for (int index = 0; index < waypoints.size(); index++) {
                    Vec2 point = waypoints.get(index);
                    if (this.handleIntersectsBox(this.gridToScreenX(point.x), this.gridToScreenY(point.y))) {
                        into.add(new VertexPick(child, parentKey, index));
                    }
                }
            }
        }
    }

    private boolean handleIntersectsBox(int handleX, int handleY) {
        int x0 = Math.min(this.boxStartX, this.boxEndX);
        int y0 = Math.min(this.boxStartY, this.boxEndY);
        int x1 = Math.max(this.boxStartX, this.boxEndX);
        int y1 = Math.max(this.boxStartY, this.boxEndY);
        int hit = TreeConnectionRenderer.HANDLE_HIT;
        return handleX + hit >= x0 && handleX - hit <= x1 && handleY + hit >= y0 && handleY - hit <= y1;
    }

    private void drawSelectionBox(GuiGraphicsExtractor graphics) {
        int x0 = Math.min(this.boxStartX, this.boxEndX);
        int y0 = Math.min(this.boxStartY, this.boxEndY);
        int x1 = Math.max(this.boxStartX, this.boxEndX);
        int y1 = Math.max(this.boxStartY, this.boxEndY);
        if (x1 <= x0 && y1 <= y0) {
            return;
        }
        graphics.fill(x0, y0, x1 + 1, y1 + 1, 0x40D4A25A);
        TreeEditorTheme.border(graphics, x0, y0, Math.max(1, x1 - x0 + 1), Math.max(1, y1 - y0 + 1), TreeEditorTheme.ACCENT);
    }

    private int clampTreeX(int x) {
        return Math.max(this.treeX, Math.min(this.treeX + this.treeW - 1, x));
    }

    private int clampTreeY(int y) {
        return Math.max(this.treeY, Math.min(this.treeY + this.treeH - 1, y));
    }

    List<TreeEditorNode> actionTargets(TreeEditorNode node) {
        if (node != null && this.selection.contains(node) && this.selection.size() > 1) {
            return List.copyOf(this.selection);
        }
        return node == null ? List.of() : List.of(node);
    }

    public void refreshWidgets() {
        if (this.minecraft != null && this.width > 0 && this.height > 0) {
            this.init(this.width, this.height);
        }
    }

    public void openNew() {
        Runnable create = () -> this.replaceDraft(TreeEditorDraft.blank());
        if (this.draft.isDirty() && this.minecraft != null) {
            this.minecraft.setScreen(new TreeEditorConfirmPopupScreen(this, "Discard unsaved changes?", create));
        } else {
            create.run();
        }
    }

    public void openPowerIconPicker() {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(TreeEditorPickerScreen.forIcons(this, "Select Power Icon", id -> {
            this.draft.setPowerIcon(id.toString());
            this.refreshWidgets();
        }));
    }

    public void openNodeIconPicker(TreeEditorNode node) {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(TreeEditorPickerScreen.forIcons(this, "Select Icon", id -> {
            for (TreeEditorNode target : this.actionTargets(node)) {
                target.setIconId(id.toString());
            }
            this.draft.markDirty();
            this.refreshWidgets();
        }));
    }

    public void openAbilityTypePicker(TreeEditorNode node) {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(new TreeEditorTypePickerScreen(this, this.catalog.abilities(), typeId -> {
            JsonObject fields = fieldsFromExample(typeId);
            for (TreeEditorNode target : this.actionTargets(node)) {
                target.setTypeId(typeId);
                target.setTypeFields(fields.deepCopy());
            }
            this.draft.markDirty();
            this.refreshWidgets();
        }));
    }

    public void openTypeFieldsJson(TreeEditorNode node) {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(new TreeEditorJsonEditScreen(this, "Type fields", node.getTypeFields(), value -> {
            if (value != null && value.isJsonObject()) {
                JsonObject object = value.getAsJsonObject();
                for (TreeEditorNode target : this.actionTargets(node)) {
                    target.setTypeFields(object.deepCopy());
                }
                this.draft.markDirty();
            }
            this.refreshWidgets();
        }));
    }

    public void openUnlockingCondition(TreeEditorNode node, int index) {
        this.openConditionAt(TreeEditorJson.asConditionList(node.getUnlocking()), index, value -> {
            this.writeConditionList(node, true, list -> replaceOrRemove(list, index, value));
            TreeEditorStateSync.applyCost(node, this.costSchemas);
        });
    }

    public void removeUnlockingCondition(TreeEditorNode node, int index) {
        List<String> before = List.copyOf(node.getParentKeys());
        this.writeConditionList(node, true, list -> {
            if (index >= 0 && index < list.size()) {
                list.remove(index);
            }
        });
        TreeEditorStateSync.applyCost(node, this.costSchemas);
        for (String parentKey : before) {
            if (!node.hasParent(parentKey)) {
                node.removeConnectionPath(parentKey);
            }
        }
        this.draft.markDirty();
        this.refreshWidgets();
    }

    public void addUnlockingCondition(TreeEditorNode node) {
        this.openAddCondition(value -> {
            this.writeConditionList(node, true, list -> list.add(value));
            TreeEditorStateSync.applyCost(node, this.costSchemas);
        });
    }

    public void openUnlockingJson(TreeEditorNode node) {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(new TreeEditorJsonEditScreen(this, "Unlocking", node.getUnlocking(), value -> {
            node.setUnlocking(value);
            this.draft.markDirty();
            this.refreshWidgets();
        }));
    }

    public void openEnablingCondition(TreeEditorNode node, int index) {
        this.openConditionAt(TreeEditorJson.asEnablingList(node.getEnabling()), index, value -> this.writeConditionList(node, false, list -> replaceOrRemove(list, index, value)));
    }

    public void removeEnablingCondition(TreeEditorNode node, int index) {
        this.writeConditionList(node, false, list -> {
            if (index >= 0 && index < list.size()) {
                list.remove(index);
            }
        });
        this.draft.markDirty();
        this.refreshWidgets();
    }

    public void addEnablingCondition(TreeEditorNode node) {
        this.openAddCondition(value -> this.writeConditionList(node, false, list -> list.add(value)));
    }

    public void openEnablingJson(TreeEditorNode node) {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(new TreeEditorJsonEditScreen(this, "Enabling", node.getEnabling(), value -> {
            node.setEnabling(value);
            this.draft.markDirty();
            this.refreshWidgets();
        }));
    }

    private void openConditionAt(List<JsonElement> list, int index, java.util.function.Consumer<JsonElement> onSave) {
        if (this.minecraft == null) {
            return;
        }
        JsonElement value = index >= 0 && index < list.size() ? list.get(index) : null;
        this.minecraft.setScreen(new TreeEditorTypedObjectScreen(this, TreeEditorTypedObjectScreen.Kind.CONDITION, this.catalog, this.costSchemas, value, saved -> {
            onSave.accept(saved);
            this.draft.markDirty();
            this.refreshWidgets();
        }));
    }

    private void openAddCondition(java.util.function.Consumer<JsonElement> onAdded) {
        if (this.minecraft == null) {
            return;
        }
        List<DocSchema> schemas = new ArrayList<>(this.catalog.conditions());
        schemas.add(0, new DocSchema(TreeEditorStateSync.TYPE_KEY_BIND, "Key Bind", "Enables the ability from a key press.", List.of(), null));
        schemas.add(0, new DocSchema(TreeEditorStateSync.TYPE_BUYABLE, "Buyable", "Unlock by paying a cost.", List.of(), null));
        this.minecraft.setScreen(new TreeEditorTypePickerScreen(this, schemas, id -> {
            DocSchema schema = this.catalog.findCondition(id);
            JsonObject initial = TreeEditorJson.typedObject(id, schema == null ? null : schema.example());
            this.minecraft.setScreen(new TreeEditorTypedObjectScreen(this, TreeEditorTypedObjectScreen.Kind.CONDITION, this.catalog, this.costSchemas, initial, value -> {
                if (value != null) {
                    onAdded.accept(value);
                }
                this.draft.markDirty();
                this.refreshWidgets();
            }));
        }));
    }

    private static void replaceOrRemove(List<JsonElement> list, int index, @Nullable JsonElement value) {
        if (index < 0 || index >= list.size()) {
            if (value != null) {
                list.add(value);
            }
            return;
        }
        if (value == null) {
            list.remove(index);
        } else {
            list.set(index, value);
        }
    }

    private void writeConditionList(TreeEditorNode node, boolean unlocking, java.util.function.Consumer<List<JsonElement>> editor) {
        List<JsonElement> list = unlocking
                ? TreeEditorJson.asConditionList(node.getUnlocking())
                : TreeEditorJson.asEnablingList(node.getEnabling());
        editor.accept(list);
        JsonElement written = unlocking
                ? TreeEditorJson.fromConditionList(list)
                : TreeEditorJson.fromEnablingList(list);
        if (unlocking) {
            node.setUnlocking(written);
        } else {
            node.setEnabling(written);
        }
    }

    private boolean usesCustomPath(TreeEditorNode parent, TreeEditorNode child) {
        return !child.getConnectionPath(parent.getKey()).isEmpty();
    }

    public void cycleCost(TreeEditorNode node) {
        int index = 0;
        for (int i = 0; i < this.costSchemas.size(); i++) {
            if (this.costSchemas.get(i).id().equals(node.getCost().getTypeId())) {
                index = i;
                break;
            }
        }
        node.getCost().setTypeId(this.costSchemas.get((index + 1) % this.costSchemas.size()).id(), this.costSchemas);
        TreeEditorStateSync.applyParentAndCost(node, this.costSchemas);
        this.draft.markDirty();
        this.refreshWidgets();
    }

    public void openCostItemPicker(TreeEditorNode node, String key) {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(new TreeEditorPickerScreen(this, "Select Item", TreeEditorPickerScreen.Mode.ITEMS, id -> {
            node.getCost().set(key, id.toString());
            TreeEditorStateSync.applyParentAndCost(node, this.costSchemas);
            this.draft.markDirty();
            this.refreshWidgets();
        }));
    }

    public void openTypeFieldEditor(TreeEditorNode node, DocField field, DocFieldKind kind) {
        if (this.minecraft == null) {
            return;
        }
        if (kind == DocFieldKind.STRING_LIST) {
            this.minecraft.setScreen(new TreeEditorStringListScreen(this, field.key(), node.getTypeFields().get(field.key()), value -> {
                this.writeTypeField(node, field.key(), value);
            }));
            return;
        }
        if (kind == DocFieldKind.VALUE || kind == DocFieldKind.CONDITION || kind == DocFieldKind.ACTION) {
            TreeEditorTypedObjectScreen.Kind typed = kind == DocFieldKind.CONDITION
                    ? TreeEditorTypedObjectScreen.Kind.CONDITION
                    : kind == DocFieldKind.ACTION
                    ? TreeEditorTypedObjectScreen.Kind.ACTION
                    : TreeEditorTypedObjectScreen.Kind.VALUE;
            this.minecraft.setScreen(new TreeEditorTypedObjectScreen(this, typed, this.catalog, this.costSchemas, node.getTypeFields().get(field.key()), value -> {
                this.writeTypeField(node, field.key(), value);
            }));
            return;
        }
        this.minecraft.setScreen(new TreeEditorJsonEditScreen(this, field.key(), node.getTypeFields().get(field.key()), value -> {
            this.writeTypeField(node, field.key(), value);
        }));
    }

    public void openTypeFieldItemPicker(TreeEditorNode node, String key) {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(new TreeEditorPickerScreen(this, "Select Item", TreeEditorPickerScreen.Mode.ITEMS, id -> {
            this.writeTypeField(node, key, new JsonPrimitive(id.toString()));
        }));
    }

    private void writeTypeField(TreeEditorNode node, String key, @Nullable JsonElement value) {
        for (TreeEditorNode target : this.actionTargets(node)) {
            if (value == null) {
                target.getTypeFields().remove(key);
            } else {
                target.getTypeFields().add(key, value.deepCopy());
            }
        }
        this.draft.markDirty();
        this.refreshWidgets();
    }

    private JsonObject fieldsFromExample(String typeId) {
        DocSchema schema = this.catalog.findAbility(typeId);
        JsonObject fields = new JsonObject();
        if (schema != null && schema.example() != null && schema.example().isJsonObject()) {
            JsonObject example = schema.example().getAsJsonObject();
            for (var entry : example.entrySet()) {
                if (!"type".equals(entry.getKey()) && !"properties".equals(entry.getKey()) && !"state".equals(entry.getKey())) {
                    fields.add(entry.getKey(), entry.getValue().deepCopy());
                }
            }
        }
        return fields;
    }

    private void drawMenuBar(GuiGraphicsExtractor graphics) {
        TreeEditorTheme.fill(graphics, 0, 0, this.width, TreeEditorTheme.MENU_H, TreeEditorTheme.HEADER);
        TreeEditorTheme.fill(graphics, 0, TreeEditorTheme.MENU_H - 1, this.width, 1, TreeEditorTheme.BORDER);
        String title = this.draft.getPowerName() + (this.draft.isDirty() ? " *" : "");
        int titleX = Math.max(120, (this.width - this.font.width(title)) / 2);
        graphics.text(this.font, title, titleX, 7, TreeEditorTheme.TEXT, false);
    }

    private void drawStatusBar(GuiGraphicsExtractor graphics) {
        int y = this.height - TreeEditorTheme.STATUS_H;
        TreeEditorTheme.fill(graphics, 0, y, this.width, TreeEditorTheme.STATUS_H, TreeEditorTheme.HEADER);
        TreeEditorTheme.fill(graphics, 0, y, this.width, 1, TreeEditorTheme.BORDER);
        String help = this.parentLinkSource != null
                ? "Parent mode: click a node to parent '" + this.parentLinkSource.getKey() + "'. Esc cancels."
                : (this.selection.size() > 1 ? this.selection.size() + " selected  ·  " : "")
                + this.status + "  ·  Zoom " + Math.round(this.zoom * 100) + "%";
        int maxW = Math.max(40, this.width - 170);
        String clipped = help;
        while (this.font.width(clipped) > maxW && clipped.length() > 4) {
            clipped = clipped.substring(0, clipped.length() - 2);
        }
        graphics.text(this.font, clipped, 10, y + 8, TreeEditorTheme.TEXT_MUTED, false);
    }

    private List<MenuItem> menuItems() {
        if (this.openMenu == Menu.FILE) {
            return List.of(
                    new MenuItem("New Power", "Ctrl+N", this::openNew),
                    new MenuItem("Open...", "Ctrl+O", this::openLoad),
                    new MenuItem("Save", "Ctrl+S", this::openSave),
                    new MenuItem("Save As...", "", this::openSaveAs),
                    new MenuItem("Close Editor", "", this::onClose)
            );
        }
        if (this.openMenu == Menu.VIEW) {
            return List.of(
                    new MenuItem(this.gridLabel(), "", () -> this.showGrid = !this.showGrid),
                    new MenuItem(this.numbersLabel(), "", () -> this.showGridNumbers = !this.showGridNumbers),
                    new MenuItem(this.hoverLabel(), "", () -> this.showNodeHover = !this.showNodeHover),
                    new MenuItem(this.hiddenLabel(), "", () -> this.showHidden = !this.showHidden),
                    new MenuItem("Reset Zoom", "", () -> this.zoom = 1.0F),
                    new MenuItem("Background...", "", this::openBackgroundPicker)
            );
        }
        return List.of();
    }

    private int menuDropdownX() {
        return this.openMenu == Menu.VIEW ? 56 : 8;
    }

    private int menuDropdownWidth() {
        return 168;
    }

    private void drawMenuDropdown(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<MenuItem> items = this.menuItems();
        if (items.isEmpty()) {
            return;
        }
        int x = this.menuDropdownX();
        int y = TreeEditorTheme.MENU_H;
        int w = this.menuDropdownWidth();
        int rowH = 20;
        int h = 6 + items.size() * rowH;
        TreeEditorTheme.rect(graphics, x, y, w, h, TreeEditorTheme.PANEL, TreeEditorTheme.BORDER);
        for (int index = 0; index < items.size(); index++) {
            MenuItem item = items.get(index);
            int itemY = y + 3 + index * rowH;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= itemY && mouseY < itemY + rowH;
            if (hovered) {
                TreeEditorTheme.fill(graphics, x + 1, itemY, w - 2, rowH, TreeEditorTheme.SELECT);
            }
            graphics.text(this.font, item.label, x + 8, itemY + 6, TreeEditorTheme.TEXT, false);
            if (!item.shortcut.isBlank()) {
                graphics.text(this.font, item.shortcut, x + w - 8 - this.font.width(item.shortcut), itemY + 6, TreeEditorTheme.TEXT_MUTED, false);
            }
        }
    }

    private boolean clickMenuDropdown(int mouseX, int mouseY) {
        List<MenuItem> items = this.menuItems();
        int x = this.menuDropdownX();
        int y = TreeEditorTheme.MENU_H;
        int w = this.menuDropdownWidth();
        int rowH = 20;
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + 6 + items.size() * rowH) {
            return false;
        }
        int index = (mouseY - y - 3) / rowH;
        if (index >= 0 && index < items.size()) {
            items.get(index).action.run();
            return true;
        }
        return false;
    }

    private record MenuItem(String label, String shortcut, Runnable action) {
    }

    private List<TreeEditorNode> displayedNodes() {
        return this.draft.visibleNodes(this.showHidden);
    }

    private boolean isDisplayed(TreeEditorNode node) {
        return this.showHidden || !node.isHiddenInGui();
    }

    private void addMenuButtons() {
        this.addRenderableWidget(new TreeEditorFlatButton(8, 1, 44, TreeEditorTheme.MENU_H - 2, "File", TreeEditorFlatButton.Style.MENU, () ->
                this.openMenu = this.openMenu == Menu.FILE ? Menu.NONE : Menu.FILE)
                .highlightWhen(() -> this.openMenu == Menu.FILE));
        this.addRenderableWidget(new TreeEditorFlatButton(56, 1, 44, TreeEditorTheme.MENU_H - 2, "View", TreeEditorFlatButton.Style.MENU, () ->
                this.openMenu = this.openMenu == Menu.VIEW ? Menu.NONE : Menu.VIEW)
                .highlightWhen(() -> this.openMenu == Menu.VIEW));
    }

    private void addStatusSlider() {
        int sliderW = 140;
        this.addRenderableWidget(new StepSlider(this.width - sliderW - 10, this.height - TreeEditorTheme.STATUS_H + 2, sliderW, 20));
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

    private String hiddenLabel() {
        return this.showHidden ? "Hidden: On" : "Hidden: Off";
    }

    private float snap(float value) {
        return TreeEditorDraft.snap(value, this.stepSize);
    }

    private static double sliderProgress(float step) {
        float clamped = Math.max(STEP_MIN, Math.min(STEP_MAX, step));
        return (clamped - STEP_MIN) / (STEP_MAX - STEP_MIN);
    }

    private static float stepFromProgress(double progress) {
        float raw = STEP_MIN + (float) progress * (STEP_MAX - STEP_MIN);
        float snapped = Math.round(raw / STEP_TICK) * STEP_TICK;
        return Math.max(STEP_MIN, Math.min(STEP_MAX, snapped));
    }

    private static String formatStep(float step) {
        String text = String.format(Locale.ROOT, "%.2f", step);
        if (text.indexOf('.') >= 0) {
            text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return text;
    }

    private final class StepSlider extends AbstractSliderButton {
        private StepSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), sliderProgress(TreeEditorScreen.this.stepSize));
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal("Step: " + formatStep(TreeEditorScreen.this.stepSize)));
        }

        @Override
        protected void applyValue() {
            TreeEditorScreen.this.stepSize = stepFromProgress(this.value);
            if (TreeEditorScreen.this.minecraft != null) {
                TreeEditorSettings.setStep(TreeEditorScreen.this.minecraft, TreeEditorScreen.this.stepSize);
            }
            this.updateMessage();
        }
    }

    public void applyBackground(Identifier textureOrBlock) {
        this.applyBackground(textureOrBlock, true);
    }

    private void applyBackground(Identifier textureOrBlock, boolean persist) {
        Identifier blockId = TreeEditorPickerScreen.blockIdFromTexture(textureOrBlock);
        Identifier texture = TreeEditorPickerScreen.blockTexture(blockId);
        this.draft.setBackgroundTexture(texture);
        this.treeBackground = new RepeatingTextureBackground(TextureReference.normal(texture), 16, 16);
        if (persist && this.minecraft != null) {
            TreeEditorSettings.setBackground(this.minecraft, texture);
        }
    }

    private void openBackgroundPicker() {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(new TreeEditorPickerScreen(this, "Select Background Block", TreeEditorPickerScreen.Mode.BLOCKS, this::applyBackground));
    }

    private void drawTreeBackground(GuiGraphicsExtractor graphics) {
        if (this.treeBackground == null && this.draft.getBackgroundTexture() != null) {
            this.treeBackground = new RepeatingTextureBackground(
                    TextureReference.normal(this.draft.getBackgroundTexture()),
                    16,
                    16
            );
        }
        if (this.treeBackground == null) {
            graphics.fill(this.treeX, this.treeY, this.treeX + this.treeW, this.treeY + this.treeH, 0xFF2A6F7A);
            return;
        }
        this.treeBackground.render(graphics, this.treeX, this.treeY, this.treeW, this.treeH);
    }

    private void drawGrid(GuiGraphicsExtractor graphics) {
        int cell = Math.max(4, Math.round(this.cellSize()));
        int originX = this.originX() - this.panX;
        int originY = this.originY() - this.panY;
        int firstCol = (int) Math.floor((this.treeX - originX) / (double) cell) - 1;
        int lastCol = (int) Math.ceil((this.treeX + this.treeW - originX) / (double) cell) + 1;
        int firstRow = (int) Math.floor((this.treeY - originY) / (double) cell) - 1;
        int lastRow = (int) Math.ceil((this.treeY + this.treeH - originY) / (double) cell) + 1;
        for (int col = firstCol; col <= lastCol; col++) {
            int x = originX + col * cell;
            int color = col == 0 ? 0x88FFFFFF : 0x33FFFFFF;
            graphics.fill(x, this.treeY, x + 1, this.treeY + this.treeH, color);
            if (this.showGridNumbers) {
                graphics.text(this.font, Integer.toString(col), x + 2, this.treeY + 2, TEXT_LIGHT, false);
            }
        }
        for (int row = firstRow; row <= lastRow; row++) {
            int y = originY + row * cell;
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
                    new Item("Add dummy node", () -> screen.addNodeAt(mouseX, mouseY)),
                    new Item("Add ability...", () -> screen.addAbilityAt(mouseX, mouseY))
            ));
        }

        static ContextMenu forVertex(TreeEditorScreen screen, TreeEditorNode child, String parentKey, int index, int mouseX, int mouseY) {
            return clamped(screen, mouseX, mouseY, List.of(
                    new Item("Delete vertex", () -> screen.deleteVertex(child, parentKey, index))
            ));
        }

        static ContextMenu forSegment(TreeEditorScreen screen, TreeEditorNode child, String parentKey, int segmentIndex, int mouseX, int mouseY) {
            float gridX = screen.screenToGridX(mouseX);
            float gridY = screen.screenToGridY(mouseY);
            return clamped(screen, mouseX, mouseY, List.of(
                    new Item("Add vertex", () -> screen.insertVertex(child, parentKey, segmentIndex, gridX, gridY))
            ));
        }

        static ContextMenu forNode(TreeEditorScreen screen, TreeEditorNode node, int mouseX, int mouseY) {
            List<Item> items = new ArrayList<>();
            int count = screen.actionTargets(node).size();
            String suffix = count > 1 ? " (" + count + ")" : "";
            items.add(new Item("Add parent...", () -> screen.beginParentLink(node)));
            items.add(new Item("Unparent" + suffix, () -> screen.unparent(node)));
            items.add(new Item("Duplicate" + suffix, () -> screen.duplicateNode(node)));
            items.add(new Item((node.isHiddenInGui() ? "Show in tree" : "Hide in tree") + suffix, () -> screen.toggleHidden(node)));
            items.add(new Item("Delete" + suffix, () -> screen.deleteNode(node)));
            return clamped(screen, mouseX, mouseY, items);
        }

        private static ContextMenu clamped(TreeEditorScreen screen, int mouseX, int mouseY, List<Item> items) {
            int width = 148;
            int height = 8 + items.size() * 20;
            int x = mouseX;
            if (x + width > screen.inspectorX) {
                x = mouseX - width;
            }
            int treeRight = screen.treeX + screen.treeW;
            int treeBottom = screen.treeY + screen.treeH;
            x = Math.max(screen.treeX + 2, Math.min(x, treeRight - width - 2));
            int y = Math.max(screen.treeY + 2, Math.min(mouseY, treeBottom - height - 2));
            x = Math.max(2, Math.min(x, screen.width - width - 2));
            y = Math.max(2, Math.min(y, screen.height - height - 2));
            return new ContextMenu(x, y, items);
        }

        void draw(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font) {
            int width = 148;
            int height = 8 + this.items.size() * 20;
            TreeEditorTheme.rect(graphics, this.x, this.y, width, height, TreeEditorTheme.PANEL, TreeEditorTheme.BORDER);
            for (int index = 0; index < this.items.size(); index++) {
                graphics.text(font, this.items.get(index).label, this.x + 8, this.y + 6 + index * 20, TEXT_LIGHT, false);
            }
        }

        boolean click(int mouseX, int mouseY) {
            int width = 148;
            for (int index = 0; index < this.items.size(); index++) {
                int itemY = this.y + 4 + index * 20;
                if (mouseX >= this.x && mouseX <= this.x + width && mouseY >= itemY && mouseY <= itemY + 20) {
                    this.items.get(index).action.run();
                    return true;
                }
            }
            return false;
        }

        private record Item(String label, Runnable action) {
        }
    }

    private record VertexPick(TreeEditorNode child, String parentKey, int index) {
    }

    private record SegmentPick(TreeEditorNode child, String parentKey, int segmentIndex) {
    }
}
