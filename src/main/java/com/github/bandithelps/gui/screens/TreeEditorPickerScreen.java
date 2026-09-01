package com.github.bandithelps.gui.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class TreeEditorPickerScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 250;
    private static final int COLUMNS = 14;
    private static final int SLOT = 20;
    private static final int TEXT_LIGHT = TreeEditorTheme.TEXT;

    public enum Mode {
        BLOCKS,
        ITEMS,
        ICONS
    }

    private final Screen parent;
    private final List<Mode> modes;
    private final Consumer<Identifier> onSelect;
    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private Mode mode;
    private EditBox searchBox;
    private int scroll;
    private String hoverLabel = "";
    private String lastHoverLabel = "";

    public TreeEditorPickerScreen(Screen parent, String title, Mode mode, Consumer<Identifier> onSelect) {
        this(parent, title, List.of(mode), onSelect);
    }

    public TreeEditorPickerScreen(Screen parent, String title, List<Mode> modes, Consumer<Identifier> onSelect) {
        super(Component.literal(title));
        this.parent = parent;
        this.modes = modes == null || modes.isEmpty() ? List.of(Mode.ITEMS) : List.copyOf(modes);
        this.mode = this.modes.getFirst();
        this.onSelect = onSelect;
    }

    public static TreeEditorPickerScreen forIcons(Screen parent, String title, Consumer<Identifier> onSelect) {
        return new TreeEditorPickerScreen(parent, title, List.of(Mode.ITEMS, Mode.ICONS), onSelect);
    }

    @Override
    protected void init() {
        super.init();
        int x = this.panelX();
        int y = this.panelY();
        this.searchBox = new EditBox(this.font, x + 10, y + 28, this.panelW() - 20, 20, Component.literal("Search"));
        this.searchBox.setResponder(value -> {
            this.scroll = 0;
            this.applyFilter();
        });
        this.addRenderableWidget(this.searchBox);
        if (this.modes.size() > 1) {
            int tabX = x + 10;
            for (Mode tab : this.modes) {
                String label = tab == Mode.ICONS ? "Icons" : tab == Mode.ITEMS ? "Items" : "Blocks";
                this.addRenderableWidget(new TreeEditorFlatButton(tabX, y + this.panelH() - 30, 58, 22, label, TreeEditorFlatButton.Style.TOGGLE, () -> this.setActiveMode(tab))
                        .highlightWhen(() -> this.mode == tab));
                tabX += 62;
            }
        }
        this.addRenderableWidget(new TreeEditorFlatButton(x + this.panelW() - 90, y + this.panelH() - 30, 80, 22, "Cancel", this::onClose));
        this.setInitialFocus(this.searchBox);
        this.reloadEntries();
        this.applyFilter();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.parent.extractRenderState(graphics, Integer.MIN_VALUE, Integer.MIN_VALUE, partialTick);
        graphics.fill(0, 0, this.width, this.height, TreeEditorTheme.OVERLAY);
        int x = this.panelX();
        int y = this.panelY();
        int panelW = this.panelW();
        int panelH = this.panelH();
        TreeEditorTheme.dialog(graphics, this.font, x, y, panelW, panelH, this.title.getString());

        int gridX = x + 10;
        int gridY = y + 54;
        int visibleRows = this.visibleRows();
        int gridW = this.visibleColumns() * SLOT;
        int gridH = visibleRows * SLOT;
        int rows = Math.max(1, (this.filtered.size() + this.visibleColumns() - 1) / this.visibleColumns());
        int maxScroll = Math.max(0, rows - visibleRows);
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll));

        this.hoverLabel = "";
        graphics.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);
        for (int index = 0; index < this.filtered.size(); index++) {
            int row = index / this.visibleColumns();
            int col = index % this.visibleColumns();
            if (row < this.scroll || row >= this.scroll + visibleRows) {
                continue;
            }
            int slotX = gridX + col * SLOT;
            int slotY = gridY + (row - this.scroll) * SLOT;
            Entry entry = this.filtered.get(index);
            boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT && mouseY >= slotY && mouseY < slotY + SLOT;
            graphics.fill(slotX, slotY, slotX + SLOT, slotY + SLOT, hovered ? TreeEditorTheme.SELECT : TreeEditorTheme.INPUT);
            this.drawEntry(graphics, entry, slotX + 2, slotY + 2);
            if (hovered) {
                this.hoverLabel = entry.label() + " (" + entry.id() + ")";
            }
        }
        graphics.disableScissor();

        if (!this.hoverLabel.isEmpty()) {
            this.lastHoverLabel = this.hoverLabel;
        }
        if (!this.lastHoverLabel.isEmpty()) {
            int labelX = x + 8;
            int labelY = gridY + gridH + 4;
            int labelW = panelW - 16;
            String clipped = this.ellipsize(this.lastHoverLabel, labelW);
            graphics.text(this.font, clipped, labelX, labelY, TEXT_LIGHT, false);
            int clippedW = Math.min(labelW, this.font.width(clipped));
            boolean overLabel = mouseX >= labelX && mouseX < labelX + clippedW
                    && mouseY >= labelY - 2 && mouseY < labelY + 12;
            if (overLabel) {
                graphics.setTooltipForNextFrame(this.font, Component.literal(this.lastHoverLabel), mouseX, mouseY);
            }
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
        int x = this.panelX();
        int y = this.panelY();
        int gridX = x + 10;
        int gridY = y + 54;
        int visibleRows = this.visibleRows();
        int visibleColumns = this.visibleColumns();
        if (mouseX < gridX || mouseY < gridY || mouseX >= gridX + visibleColumns * SLOT || mouseY >= gridY + visibleRows * SLOT) {
            return true;
        }
        int col = (mouseX - gridX) / SLOT;
        int row = this.scroll + (mouseY - gridY) / SLOT;
        int index = row * visibleColumns + col;
        if (index < 0 || index >= this.filtered.size()) {
            return true;
        }
        this.onSelect.accept(this.filtered.get(index).id());
        this.onClose();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scroll -= (int) Math.signum(scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    public static Identifier blockTexture(Identifier blockId) {
        Identifier resolved = blockIdFromTexture(blockId);
        return Identifier.fromNamespaceAndPath(resolved.getNamespace(), "textures/block/" + resolved.getPath() + ".png");
    }

    public static Identifier blockIdFromTexture(Identifier textureOrBlock) {
        String path = textureOrBlock.getPath();
        if (path.startsWith("textures/block/") && path.endsWith(".png")) {
            return Identifier.fromNamespaceAndPath(
                    textureOrBlock.getNamespace(),
                    path.substring("textures/block/".length(), path.length() - ".png".length())
            );
        }
        if (path.startsWith("block/")) {
            return Identifier.fromNamespaceAndPath(textureOrBlock.getNamespace(), path.substring("block/".length()));
        }
        return textureOrBlock;
    }

    private void setActiveMode(Mode mode) {
        if (this.mode == mode) {
            return;
        }
        this.mode = mode;
        this.scroll = 0;
        this.reloadEntries();
        this.applyFilter();
    }

    private void reloadEntries() {
        this.all.clear();
        if (this.mode == Mode.BLOCKS) {
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block == Blocks.AIR) {
                    continue;
                }
                Identifier id = BuiltInRegistries.BLOCK.getKey(block);
                Item item = block.asItem();
                this.all.add(Entry.item(id, new ItemStack(item), block.getName().getString()));
            }
            return;
        }
        if (this.mode == Mode.ICONS) {
            if (this.minecraft != null) {
                this.minecraft.getResourceManager()
                        .listResources("textures/icons", id -> id.getPath().endsWith(".png"))
                        .keySet()
                        .stream()
                        .sorted(Comparator.comparing(Identifier::toString))
                        .forEach(id -> this.all.add(Entry.icon(id)));
            }
            return;
        }
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            ItemStack stack = new ItemStack(item);
            this.all.add(Entry.item(id, stack, stack.getHoverName().getString()));
        }
    }

    private void drawEntry(GuiGraphicsExtractor graphics, Entry entry, int x, int y) {
        if (entry.texture() != null) {
            try {
                graphics.blit(RenderPipelines.GUI_TEXTURED, entry.texture(), x, y, 0.0F, 0.0F, 16, 16, 16, 16);
                return;
            } catch (RuntimeException ignored) {
                TreeEditorTheme.rect(graphics, x, y, 16, 16, TreeEditorTheme.INPUT, TreeEditorTheme.BORDER);
                return;
            }
        }
        graphics.item(entry.stack(), x, y);
    }

    private int panelW() {
        return Math.min(PANEL_WIDTH, Math.max(180, this.width - 16));
    }

    private int panelH() {
        return Math.min(PANEL_HEIGHT, Math.max(140, this.height - 16));
    }

    private int panelX() {
        return Math.max(0, (this.width - this.panelW()) / 2);
    }

    private int panelY() {
        return Math.max(0, (this.height - this.panelH()) / 2);
    }

    private int visibleColumns() {
        return Math.max(6, Math.min(COLUMNS, (this.panelW() - 16) / SLOT));
    }

    private int visibleRows() {
        return Math.max(3, Math.min(7, (this.panelH() - 86) / SLOT));
    }

    private String ellipsize(String text, int maxWidth) {
        if (text == null || text.isEmpty() || this.font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "...";
        int budget = Math.max(0, maxWidth - this.font.width(ellipsis));
        String trimmed = text;
        while (!trimmed.isEmpty() && this.font.width(trimmed) > budget) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    private void applyFilter() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        this.filtered.clear();
        for (Entry entry : this.all) {
            if (query.isEmpty()
                    || entry.id().toString().contains(query)
                    || entry.label().toLowerCase(Locale.ROOT).contains(query)) {
                this.filtered.add(entry);
            }
        }
    }

    private record Entry(Identifier id, ItemStack stack, @Nullable Identifier texture, String label) {
        static Entry item(Identifier id, ItemStack stack, String label) {
            return new Entry(id, stack, null, label);
        }

        static Entry icon(Identifier texture) {
            String path = texture.getPath();
            String label = path;
            if (path.startsWith("textures/icons/") && path.endsWith(".png")) {
                label = path.substring("textures/icons/".length(), path.length() - ".png".length());
            }
            return new Entry(texture, ItemStack.EMPTY, texture, label);
        }
    }
}
