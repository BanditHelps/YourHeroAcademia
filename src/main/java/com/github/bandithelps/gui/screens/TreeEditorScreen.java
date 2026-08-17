package com.github.bandithelps.gui.screens;

import com.github.bandithelps.gui.tree.TreeEditorDraft;
import com.github.bandithelps.gui.tree.TreeEditorExporter;
import com.github.bandithelps.gui.tree.TreeEditorNode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.threetag.palladium.client.renderer.icon.IconRenderer;
import net.threetag.palladium.logic.context.DataContext;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TreeEditorScreen extends Screen {
    private static final int TOP_BAR = 28;
    private static final int GRID_SIZE = 50;
    private static final int NODE_HIT = 13;
    private static final Identifier FRAME_SPRITE = Identifier.fromNamespaceAndPath("palladium", "powers/ability_frame_unlocked");

    private final TreeEditorDraft draft;
    private int panX;
    private int panY;
    private boolean panning;
    @Nullable
    private TreeEditorNode dragging;
    @Nullable
    private TreeEditorNode selected;
    @Nullable
    private TreeEditorNode parentLinkSource;
    @Nullable
    private ContextMenu contextMenu;
    private String status = "Right-click empty space to add a node. Drag nodes to move. E exports JSON.";

    public TreeEditorScreen(TreeEditorDraft draft) {
        super(Component.literal("Power Tree Editor"));
        this.draft = draft;
        this.panX = -this.width / 2;
        this.panY = -this.height / 2;
    }

    @Override
    protected void init() {
        super.init();
        if (this.panX == 0 && this.panY == 0) {
            this.panX = Math.max(0, 400 - this.width / 2);
            this.panY = Math.max(0, 300 - this.height / 2);
        }
        this.addRenderableWidget(Button.builder(Component.literal("Export"), button -> this.exportDraft())
                .bounds(this.width - 148, 4, 68, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> this.onClose())
                .bounds(this.width - 74, 4, 64, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xFF101820);
        graphics.fill(0, 0, this.width, TOP_BAR, 0xFF1B2734);
        graphics.text(this.font, "Tree Editor: " + this.draft.getPowerName() + " (" + this.draft.getPowerId() + ")",
                8, 8, 0xFFE6F2FF, false);

        graphics.enableScissor(0, TOP_BAR, this.width, this.height);
        this.drawGrid(graphics);
        this.drawConnections(graphics);
        for (TreeEditorNode node : this.draft.getNodes()) {
            this.drawNode(graphics, node, this.isHoveringNode(node, mouseX, mouseY));
        }
        graphics.disableScissor();

        graphics.fill(0, this.height - 18, this.width, this.height, 0xCC111A26);
        String footer = this.parentLinkSource != null
                ? "Parent mode: click a node to parent '" + this.parentLinkSource.getKey() + "'. Esc cancels."
                : this.status;
        graphics.text(this.font, footer, 8, this.height - 14, 0xFF9FC9EE, false);

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
        if (mouseY < TOP_BAR) {
            return false;
        }

        TreeEditorNode hit = this.nodeAt(mouseX, mouseY);
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
            this.panX = Math.max(0, this.panX - (int) dragX);
            this.panY = Math.max(0, this.panY - (int) dragY);
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
            String json = TreeEditorExporter.toJson(this.draft);
            GLFW.glfwSetClipboardString(this.minecraft.getWindow().handle(), json);
            Path file = TreeEditorExporter.writeToGameDir(this.minecraft, this.draft);
            this.status = "Exported to clipboard and " + file.toAbsolutePath();
        } catch (Exception exception) {
            this.status = "Export failed: " + exception.getMessage();
        }
    }

    private void drawGrid(GuiGraphicsExtractor graphics) {
        int startX = -((this.panX % GRID_SIZE) + GRID_SIZE) % GRID_SIZE;
        int startY = TOP_BAR - ((this.panY % GRID_SIZE) + GRID_SIZE) % GRID_SIZE;
        for (int x = startX; x < this.width; x += GRID_SIZE) {
            graphics.fill(x, TOP_BAR, x + 1, this.height, 0x22FFFFFF);
        }
        for (int y = startY; y < this.height; y += GRID_SIZE) {
            graphics.fill(0, y, this.width, y + 1, 0x22FFFFFF);
        }
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
            List<Integer> childYs = new ArrayList<>();
            for (TreeEditorNode child : children) {
                int childY = this.nodeScreenY(child);
                childYs.add(childY);
                minY = Math.min(minY, childY);
                maxY = Math.max(maxY, childY);
            }
            int color = 0xFF55CCCC;
            graphics.fill(busX - 1, minY - 1, busX, maxY, color);
            graphics.fill(startX - 1, startY - 1, busX, startY, color);
            for (int index = 0; index < children.size(); index++) {
                TreeEditorNode child = children.get(index);
                int childX = this.nodeScreenX(child);
                int childY = childYs.get(index);
                graphics.fill(busX - 1, childY - 1, childX, childY, color);
            }
        }
    }

    private void drawNode(GuiGraphicsExtractor graphics, TreeEditorNode node, boolean hovered) {
        int x = this.nodeScreenX(node);
        int y = this.nodeScreenY(node);
        boolean selectedNode = node == this.selected || node == this.parentLinkSource;
        int frame = selectedNode ? 0xFF79B8FF : (hovered ? 0xFF9FC9EE : 0xFF3A4A5A);
        if (node.isCreated()) {
            frame = selectedNode ? 0xFF7CFF9A : 0xFF3D8A55;
        }
        graphics.fill(x - NODE_HIT, y - NODE_HIT, x + NODE_HIT, y + NODE_HIT, 0xFF111A26);
        graphics.fill(x - NODE_HIT, y - NODE_HIT, x + NODE_HIT, y - NODE_HIT + 1, frame);
        graphics.fill(x - NODE_HIT, y + NODE_HIT - 1, x + NODE_HIT, y + NODE_HIT, frame);
        graphics.fill(x - NODE_HIT, y - NODE_HIT, x - NODE_HIT + 1, y + NODE_HIT, frame);
        graphics.fill(x + NODE_HIT - 1, y - NODE_HIT, x + NODE_HIT, y + NODE_HIT, frame);
        try {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME_SPRITE, x - NODE_HIT, y - NODE_HIT, 26, 26);
        } catch (RuntimeException ignored) {
            // Sprite may be missing in some resource reload states; the fill frame is enough.
        }
        if (node.getIcon() != null && this.minecraft != null && this.minecraft.player != null) {
            IconRenderer.drawIcon(node.getIcon(), this.minecraft, graphics, DataContext.forEntity(this.minecraft.player), x - 8, y - 8);
        }
        if (hovered || selectedNode) {
            graphics.text(this.font, node.getTitle() + " [" + node.getKey() + "]", x + 16, y - 4, 0xFFFFFFFF, true);
        }
    }

    @Nullable
    private TreeEditorNode nodeAt(int mouseX, int mouseY) {
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

    private int nodeScreenX(TreeEditorNode node) {
        return Math.round(node.getGridX() * GRID_SIZE + (GRID_SIZE / 2.0F)) - this.panX;
    }

    private int nodeScreenY(TreeEditorNode node) {
        return TOP_BAR + Math.round(node.getGridY() * GRID_SIZE + (GRID_SIZE / 2.0F)) - this.panY;
    }

    private float screenToGridX(int mouseX) {
        return (mouseX + this.panX - (GRID_SIZE / 2.0F)) / GRID_SIZE;
    }

    private float screenToGridY(int mouseY) {
        return (mouseY - TOP_BAR + this.panY - (GRID_SIZE / 2.0F)) / GRID_SIZE;
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
            return new ContextMenu(mouseX, mouseY, List.of(
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
            return new ContextMenu(mouseX, mouseY, items);
        }

        void draw(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font) {
            int width = 120;
            int height = 4 + this.items.size() * 16;
            graphics.fill(this.x, this.y, this.x + width, this.y + height, 0xF0111A26);
            graphics.fill(this.x, this.y, this.x + width, this.y + 1, 0xFF79B8FF);
            graphics.fill(this.x, this.y + height - 1, this.x + width, this.y + height, 0xFF79B8FF);
            graphics.fill(this.x, this.y, this.x + 1, this.y + height, 0xFF79B8FF);
            graphics.fill(this.x + width - 1, this.y, this.x + width, this.y + height, 0xFF79B8FF);
            for (int index = 0; index < this.items.size(); index++) {
                graphics.text(font, this.items.get(index).label, this.x + 6, this.y + 4 + index * 16, 0xFFE6F2FF, false);
            }
        }

        boolean click(int mouseX, int mouseY) {
            int width = 120;
            for (int index = 0; index < this.items.size(); index++) {
                int itemY = this.y + 2 + index * 16;
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
}
