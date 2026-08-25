package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.client.creation.ClientCreationState;
import com.github.bandithelps.creation.CreationEnchantments;
import com.github.bandithelps.creation.CreationTab;
import com.github.bandithelps.network.CreationResearchPayload;
import com.github.bandithelps.network.CreationSyncPayload;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.threetag.palladium.client.gui.ui.screen.UiScreen;
import net.threetag.palladium.client.gui.ui.widget.UiWidget;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetProperties;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetSerializer;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

public class ResearchTablePanelUiComponent extends UiWidget {
    private static final int SLOT = 22;
    private static final int GRID_ICON = 14;
    private static final int DETAIL_ICON = 16;
    private static final int COLS = 8;
    private static final int VISIBLE_ROWS = 5;
    private static final int TAB_HEIGHT = 14;
    private static final int SEARCH_HEIGHT = 14;
    private static final int HEADER_HEIGHT = 16;
    private static final int CHECK_BOX = 10;
    private static final int CHECK_GREEN = 0xFF55FF55;
    private static final int CHECK_OUTLINE = 0xFF0E3A14;

    public static final MapCodec<ResearchTablePanelUiComponent> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("frame_color", 0xFF79B8FF).forGetter(ResearchTablePanelUiComponent::getFrameColor),
            Codec.INT.optionalFieldOf("panel_color", 0xCC172231).forGetter(ResearchTablePanelUiComponent::getPanelColor),
            Codec.INT.optionalFieldOf("text_color", 0xFFE6F2FF).forGetter(ResearchTablePanelUiComponent::getTextColor),
            propertiesCodec(378, 212)
    ).apply(instance, ResearchTablePanelUiComponent::new));

    private final int frameColor;
    private final int panelColor;
    private final int textColor;

    public ResearchTablePanelUiComponent(int frameColor, int panelColor, int textColor, UiWidgetProperties properties) {
        super(properties);
        this.frameColor = withOpaqueAlpha(frameColor);
        this.panelColor = withOpaqueAlpha(panelColor);
        this.textColor = withOpaqueAlpha(textColor);
    }

    @Override
    public UiWidgetSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.RESEARCH_TABLE_PANEL;
    }

    @Override
    public AbstractWidget buildWidget(UiScreen screen, ScreenRectangle rectangle, DataContext context) {
        return new ResearchWidget(this, this.getX(rectangle, context), this.getY(rectangle, context), this.getWidth(context), this.getHeight(context));
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

    private static final class ResearchWidget extends AbstractWidget {
        private final ResearchTablePanelUiComponent owner;
        private int scrollOffset;
        private String selectedId;
        private FilterTab filterTab = FilterTab.ALL;
        private String searchQuery = "";
        private boolean searchFocused;
        private static boolean hideLearned;

        private ResearchWidget(ResearchTablePanelUiComponent owner, int x, int y, int width, int height) {
            super(x, y, width, height, Component.literal("Research Table"));
            this.owner = owner;
        }

        private int gridX() {
            return this.getX() + 8;
        }

        private int gridY() {
            return this.getY() + HEADER_HEIGHT + TAB_HEIGHT + 4 + SEARCH_HEIGHT + 6;
        }

        private int gridW() {
            return COLS * SLOT;
        }

        private int gridH() {
            return VISIBLE_ROWS * SLOT;
        }

        private int searchX() {
            return gridX();
        }

        private int searchY() {
            return this.getY() + HEADER_HEIGHT + TAB_HEIGHT + 4;
        }

        private int tabY() {
            return this.getY() + HEADER_HEIGHT;
        }

        private int detailX() {
            return gridX() + gridW() + 10;
        }

        private int detailY() {
            return this.getY() + HEADER_HEIGHT;
        }

        private int detailW() {
            return this.getWidth() - (detailX() - this.getX()) - 8;
        }

        private int detailH() {
            return this.getHeight() - HEADER_HEIGHT - 8;
        }

        private int sacrificeX() {
            return detailX() + 8;
        }

        private int sacrificeY() {
            return detailY() + detailH() - 24;
        }

        private int sacrificeW() {
            return detailW() - 16;
        }

        private int sacrificeH() {
            return 16;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();
            drawFrame(gui, x, y, width, height, this.owner.panelColor, this.owner.frameColor);
            gui.text(minecraft.font, "Research", x + 8, y + 5, this.owner.textColor, false);
            drawHideLearnedCheckbox(gui, minecraft, mouseX, mouseY);

            List<CreationSyncPayload.ClientEntry> entries = this.filterTab.isEnchants() ? List.of() : filteredEntries();
            List<CreationSyncPayload.ClientEnchantEntry> enchantEntries = this.filterTab.isEnchants() ? filteredEnchantEntries() : List.of();
            int totalEntries = this.filterTab.isEnchants() ? enchantEntries.size() : entries.size();
            int maxScroll = Math.max(0, ceilDiv(totalEntries, COLS) - VISIBLE_ROWS);
            this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll);

            drawTabs(gui, minecraft, mouseX, mouseY);
            drawSearch(gui, minecraft);

            int gridX = gridX();
            int gridY = gridY();
            int gridW = gridW();
            int gridH = gridH();
            drawFrame(gui, gridX - 2, gridY - 2, gridW + 4, gridH + 4, 0xAA111A26, this.owner.frameColor);

            for (int row = 0; row < VISIBLE_ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    int index = (this.scrollOffset + row) * COLS + col;
                    int slotX = gridX + col * SLOT;
                    int slotY = gridY + row * SLOT;
                    boolean hovered = contains(mouseX, mouseY, slotX, slotY, SLOT, SLOT);
                    drawFrame(gui, slotX + 1, slotY + 1, SLOT - 2, SLOT - 2, hovered ? 0xAA2A3F54 : 0xAA182533, 0xFF3B5A78);
                    if (index < 0 || index >= totalEntries) {
                        continue;
                    }
                    ItemStack stack;
                    boolean unlocked;
                    boolean selected;
                    if (this.filterTab.isEnchants()) {
                        CreationSyncPayload.ClientEnchantEntry entry = enchantEntries.get(index);
                        stack = enchantBookStack(entry);
                        unlocked = entry.unlocked();
                        selected = entry.enchantId().equals(this.selectedId);
                    } else {
                        CreationSyncPayload.ClientEntry entry = entries.get(index);
                        stack = ClientCreationState.stackOf(entry.itemId());
                        unlocked = entry.unlocked();
                        selected = entry.itemId().equals(this.selectedId);
                    }
                    int iconX = slotX + (SLOT - GRID_ICON) / 2;
                    int iconY = slotY + (SLOT - GRID_ICON) / 2;
                    drawItem(gui, stack, iconX, iconY, GRID_ICON);
                    if (unlocked) {
                        drawCheckmark(gui, slotX + SLOT - 9, slotY + 2, CHECK_GREEN, CHECK_OUTLINE);
                    }
                    if (selected) {
                        drawFrame(gui, slotX + 1, slotY + 1, SLOT - 2, SLOT - 2, 0x00000000, 0xFFFFD27A);
                    }
                }
            }

            int detailX = detailX();
            int detailY = detailY();
            int detailW = detailW();
            int detailH = detailH();
            drawFrame(gui, detailX, detailY, detailW, detailH, 0xAA111A26, this.owner.frameColor);

            if (this.filterTab.isEnchants()) {
                CreationSyncPayload.ClientEnchantEntry selectedEnchant = ClientCreationState.findEnchant(this.selectedId);
                if (selectedEnchant == null) {
                    gui.text(minecraft.font, Component.translatable("gui.yha.creation.select_enchant").getString(), detailX + 8, detailY + 16, 0xFF9AAFC5, false);
                } else {
                    drawEnchantDetail(gui, minecraft, selectedEnchant, mouseX, mouseY, detailX, detailY, detailW);
                }
            } else {
                CreationSyncPayload.ClientEntry selected = ClientCreationState.find(this.selectedId);
                if (selected == null) {
                    gui.text(minecraft.font, Component.translatable("gui.yha.creation.select_item").getString(), detailX + 8, detailY + 16, 0xFF9AAFC5, false);
                } else {
                    drawItemDetail(gui, minecraft, selected, mouseX, mouseY, detailX, detailY, detailW);
                }
            }

            if (hitHideLearned(minecraft, mouseX, mouseY)) {
                gui.setTooltipForNextFrame(
                        minecraft.font,
                        Component.translatable("gui.yha.creation.hide_learned.tooltip"),
                        mouseX,
                        mouseY
                );
            } else if (contains(mouseX, mouseY, gridX, gridY, gridW, gridH)) {
                int col = (mouseX - gridX) / SLOT;
                int row = (mouseY - gridY) / SLOT;
                int index = (this.scrollOffset + row) * COLS + col;
                if (col >= 0 && col < COLS && row >= 0 && row < VISIBLE_ROWS && index >= 0 && index < totalEntries) {
                    if (this.filterTab.isEnchants()) {
                        CreationSyncPayload.ClientEnchantEntry entry = enchantEntries.get(index);
                        String name = enchantDisplayName(entry);
                        gui.setTooltipForNextFrame(minecraft.font, Component.literal(
                                name + (entry.unlocked() ? " (unlocked)" : " (" + entry.progress() + "/" + Math.max(1, entry.researchCost()) + ")")
                        ).withStyle(ChatFormatting.AQUA), mouseX, mouseY);
                    } else {
                        CreationSyncPayload.ClientEntry entry = entries.get(index);
                        ItemStack stack = ClientCreationState.stackOf(entry.itemId());
                        gui.setTooltipForNextFrame(minecraft.font, Component.literal(
                                (stack.isEmpty() ? entry.itemId() : stack.getHoverName().getString())
                                        + (entry.unlocked() ? " (unlocked)" : " (" + entry.progress() + "/" + Math.max(1, entry.researchCost()) + ")")
                        ).withStyle(ChatFormatting.AQUA), mouseX, mouseY);
                    }
                }
            }
        }

        private void drawTabs(GuiGraphicsExtractor gui, Minecraft minecraft, int mouseX, int mouseY) {
            int x = gridX();
            int y = tabY();
            int remaining = gridW();
            FilterTab[] tabs = FilterTab.values();
            int gap = 2;
            int tabW = (remaining - gap * (tabs.length - 1)) / tabs.length;
            for (int i = 0; i < tabs.length; i++) {
                FilterTab tab = tabs[i];
                int tabX = x + i * (tabW + gap);
                boolean selected = this.filterTab == tab;
                boolean hovered = contains(mouseX, mouseY, tabX, y, tabW, TAB_HEIGHT);
                int fill = selected ? 0xAA2A5A78 : (hovered ? 0xAA24384C : 0xAA111A26);
                int border = selected ? 0xFF9AD1FF : 0xFF3B5A78;
                drawFrame(gui, tabX, y, tabW, TAB_HEIGHT, fill, border);
                String label = tab.label();
                gui.text(
                        minecraft.font,
                        trim(minecraft, label, tabW - 4),
                        tabX + Math.max(2, (tabW - minecraft.font.width(trim(minecraft, label, tabW - 4))) / 2),
                        y + 3,
                        selected ? this.owner.textColor : 0xFF9AAFC5,
                        false
                );
            }
        }

        private void drawSearch(GuiGraphicsExtractor gui, Minecraft minecraft) {
            int x = searchX();
            int y = searchY();
            int w = gridW();
            int border = this.searchFocused ? 0xFF9AD1FF : 0xFF3E5A74;
            drawFrame(gui, x, y, w, SEARCH_HEIGHT, 0xAA0E1622, border);
            String placeholder = Component.translatable("gui.yha.creation.search").getString();
            String shown = this.searchQuery.isBlank() ? placeholder : this.searchQuery;
            int shownColor = this.searchQuery.isBlank() ? 0xFF7D8FA4 : 0xFFE6F2FF;
            gui.text(minecraft.font, trim(minecraft, shown, w - 8), x + 3, y + 3, shownColor, false);
            if (this.searchFocused && ((System.currentTimeMillis() / 400L) % 2L == 0L)) {
                int cursorX = x + 3 + minecraft.font.width(trim(minecraft, this.searchQuery, w - 10));
                gui.fill(cursorX, y + 2, cursorX + 1, y + SEARCH_HEIGHT - 2, 0xFFD2E9FF);
            }
        }

        private void drawHideLearnedCheckbox(GuiGraphicsExtractor gui, Minecraft minecraft, int mouseX, int mouseY) {
            String label = hideLearnedLabel();
            int boxX = hideLearnedBoxX(minecraft);
            int boxY = hideLearnedBoxY();
            boolean hovered = hitHideLearned(minecraft, mouseX, mouseY);
            int fill = hideLearned ? 0xAA2A5A78 : (hovered ? 0xAA24384C : 0xAA0E1622);
            int border = hideLearned || hovered ? 0xFF9AD1FF : 0xFF3B5A78;
            drawFrame(gui, boxX, boxY, CHECK_BOX, CHECK_BOX, fill, border);
            if (hideLearned) {
                drawCheckmark(gui, boxX + 1, boxY + 2, CHECK_GREEN, CHECK_OUTLINE);
            }
            gui.text(minecraft.font, label, boxX + CHECK_BOX + 4, this.getY() + 5, hovered || hideLearned ? this.owner.textColor : 0xFF9AAFC5, false);
        }

        private boolean hitHideLearned(Minecraft minecraft, int mouseX, int mouseY) {
            int boxX = hideLearnedBoxX(minecraft);
            int boxY = hideLearnedBoxY();
            int width = CHECK_BOX + 4 + minecraft.font.width(hideLearnedLabel());
            return contains(mouseX, mouseY, boxX, boxY, width, CHECK_BOX);
        }

        private int hideLearnedBoxX(Minecraft minecraft) {
            int titleEnd = this.getX() + 8 + minecraft.font.width("Research") + 8;
            int groupW = CHECK_BOX + 4 + minecraft.font.width(hideLearnedLabel());
            return Math.max(titleEnd, gridX() + gridW() - groupW);
        }

        private int hideLearnedBoxY() {
            return this.getY() + 3;
        }

        private static String hideLearnedLabel() {
            return Component.translatable("gui.yha.creation.hide_learned").getString();
        }

        private static void drawCheckmark(GuiGraphicsExtractor gui, int x, int y, int color, int outline) {
            drawCheckmarkShape(gui, x - 1, y, outline);
            drawCheckmarkShape(gui, x + 1, y, outline);
            drawCheckmarkShape(gui, x, y - 1, outline);
            drawCheckmarkShape(gui, x, y + 1, outline);
            drawCheckmarkShape(gui, x, y, color);
        }

        private static void drawCheckmarkShape(GuiGraphicsExtractor gui, int x, int y, int color) {
            gui.fill(x, y + 3, x + 2, y + 5, color);
            gui.fill(x + 1, y + 4, x + 3, y + 6, color);
            gui.fill(x + 2, y + 3, x + 4, y + 5, color);
            gui.fill(x + 3, y + 2, x + 5, y + 4, color);
            gui.fill(x + 4, y + 1, x + 6, y + 3, color);
            gui.fill(x + 5, y, x + 7, y + 2, color);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != 0) {
                return false;
            }
            int mouseX = (int) event.x();
            int mouseY = (int) event.y();

            Minecraft minecraft = Minecraft.getInstance();
            if (hitHideLearned(minecraft, mouseX, mouseY)) {
                hideLearned = !hideLearned;
                this.scrollOffset = 0;
                this.searchFocused = false;
                this.setFocused(false);
                clickSound();
                return true;
            }

            this.searchFocused = contains(mouseX, mouseY, searchX(), searchY(), gridW(), SEARCH_HEIGHT);
            this.setFocused(this.searchFocused);
            if (this.searchFocused) {
                return true;
            }

            FilterTab clickedTab = hitTab(mouseX, mouseY);
            if (clickedTab != null) {
                if (this.filterTab != clickedTab) {
                    this.filterTab = clickedTab;
                    this.scrollOffset = 0;
                    this.selectedId = null;
                }
                clickSound();
                return true;
            }

            if (this.filterTab.isEnchants()) {
                CreationSyncPayload.ClientEnchantEntry selectedEnchant = ClientCreationState.findEnchant(this.selectedId);
                if (selectedEnchant != null
                        && !selectedEnchant.unlocked()
                        && contains(mouseX, mouseY, sacrificeX(), sacrificeY(), sacrificeW(), sacrificeH())) {
                    if (countEnchantBooks(selectedEnchant.enchantId()) > 0) {
                        ClientPacketDistributor.sendToServer(new CreationResearchPayload(selectedEnchant.enchantId(), true));
                    }
                    clickSound();
                    return true;
                }
            } else {
                CreationSyncPayload.ClientEntry selected = ClientCreationState.find(this.selectedId);
                if (selected != null
                        && !selected.unlocked()
                        && contains(mouseX, mouseY, sacrificeX(), sacrificeY(), sacrificeW(), sacrificeH())) {
                    if (countInInventory(ClientCreationState.stackOf(selected.itemId())) > 0) {
                        ClientPacketDistributor.sendToServer(new CreationResearchPayload(selected.itemId()));
                    }
                    clickSound();
                    return true;
                }
            }

            int gridX = gridX();
            int gridY = gridY();
            if (!contains(mouseX, mouseY, gridX, gridY, gridW(), gridH())) {
                return false;
            }
            int col = (mouseX - gridX) / SLOT;
            int row = (mouseY - gridY) / SLOT;
            if (this.filterTab.isEnchants()) {
                List<CreationSyncPayload.ClientEnchantEntry> enchantEntries = filteredEnchantEntries();
                int index = (this.scrollOffset + row) * COLS + col;
                if (col < 0 || col >= COLS || row < 0 || row >= VISIBLE_ROWS || index < 0 || index >= enchantEntries.size()) {
                    return false;
                }
                this.selectedId = enchantEntries.get(index).enchantId();
            } else {
                List<CreationSyncPayload.ClientEntry> entries = filteredEntries();
                int index = (this.scrollOffset + row) * COLS + col;
                if (col < 0 || col >= COLS || row < 0 || row >= VISIBLE_ROWS || index < 0 || index >= entries.size()) {
                    return false;
                }
                this.selectedId = entries.get(index).itemId();
            }
            clickSound();
            return true;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (!contains((int) mouseX, (int) mouseY, gridX(), gridY(), gridW(), gridH())) {
                return false;
            }
            int size = this.filterTab.isEnchants() ? filteredEnchantEntries().size() : filteredEntries().size();
            int maxScroll = Math.max(0, ceilDiv(size, COLS) - VISIBLE_ROWS);
            if (scrollY > 0) {
                this.scrollOffset = Math.max(0, this.scrollOffset - 1);
            } else if (scrollY < 0) {
                this.scrollOffset = Math.min(maxScroll, this.scrollOffset + 1);
            }
            return true;
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (!this.searchFocused) {
                return false;
            }
            int key = event.key();
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                this.searchFocused = false;
                this.setFocused(false);
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!this.searchQuery.isEmpty()) {
                    this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
                    this.scrollOffset = 0;
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_DELETE) {
                this.searchQuery = "";
                this.scrollOffset = 0;
                return true;
            }
            return false;
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            if (!this.searchFocused) {
                return false;
            }
            int code = event.codepoint();
            if (code < 32 || code == 127) {
                return false;
            }
            this.searchQuery += Character.toString(code);
            this.scrollOffset = 0;
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            narrationElementOutput.add(NarratedElementType.TITLE, this.getMessage());
        }

        private FilterTab hitTab(int mouseX, int mouseY) {
            int x = gridX();
            int y = tabY();
            FilterTab[] tabs = FilterTab.values();
            int gap = 2;
            int tabW = (gridW() - gap * (tabs.length - 1)) / tabs.length;
            for (int i = 0; i < tabs.length; i++) {
                int tabX = x + i * (tabW + gap);
                if (contains(mouseX, mouseY, tabX, y, tabW, TAB_HEIGHT)) {
                    return tabs[i];
                }
            }
            return null;
        }

        private List<CreationSyncPayload.ClientEntry> filteredEntries() {
            List<CreationSyncPayload.ClientEntry> result = new ArrayList<>();
            String query = this.searchQuery.trim().toLowerCase(Locale.ROOT);
            for (CreationSyncPayload.ClientEntry entry : ClientCreationState.get().entries()) {
                if (this.filterTab.tab != null && CreationTab.fromId(entry.tab()) != this.filterTab.tab) {
                    continue;
                }
                if (hideLearned && entry.unlocked()) {
                    continue;
                }
                if (!query.isEmpty() && !matchesQuery(entry, query)) {
                    continue;
                }
                result.add(entry);
            }
            return result;
        }

        private List<CreationSyncPayload.ClientEnchantEntry> filteredEnchantEntries() {
            List<CreationSyncPayload.ClientEnchantEntry> result = new ArrayList<>();
            String query = this.searchQuery.trim().toLowerCase(Locale.ROOT);
            for (CreationSyncPayload.ClientEnchantEntry entry : ClientCreationState.enchants()) {
                if (!entry.researchable()) {
                    continue;
                }
                if (hideLearned && entry.unlocked()) {
                    continue;
                }
                if (!query.isEmpty() && !matchesEnchantQuery(entry, query)) {
                    continue;
                }
                result.add(entry);
            }
            return result;
        }

        private static boolean matchesQuery(CreationSyncPayload.ClientEntry entry, String query) {
            if (entry.itemId().toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
            ItemStack stack = ClientCreationState.stackOf(entry.itemId());
            return !stack.isEmpty() && stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query);
        }

        private static boolean matchesEnchantQuery(CreationSyncPayload.ClientEnchantEntry entry, String query) {
            if (entry.enchantId().toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
            return enchantDisplayName(entry).toLowerCase(Locale.ROOT).contains(query);
        }

        private void drawItemDetail(
                GuiGraphicsExtractor gui,
                Minecraft minecraft,
                CreationSyncPayload.ClientEntry selected,
                int mouseX,
                int mouseY,
                int detailX,
                int detailY,
                int detailW
        ) {
            ItemStack stack = ClientCreationState.stackOf(selected.itemId());
            int owned = countInInventory(stack);
            drawStudyDetail(
                    gui,
                    minecraft,
                    stack,
                    stack.isEmpty() ? selected.itemId() : stack.getHoverName().getString(),
                    selected.unlocked(),
                    selected.progress(),
                    selected.researchCost(),
                    Component.translatable("gui.yha.creation.inventory_count", owned).getString(),
                    owned,
                    mouseX,
                    mouseY,
                    detailX,
                    detailY,
                    detailW
            );
        }

        private void drawEnchantDetail(
                GuiGraphicsExtractor gui,
                Minecraft minecraft,
                CreationSyncPayload.ClientEnchantEntry selected,
                int mouseX,
                int mouseY,
                int detailX,
                int detailY,
                int detailW
        ) {
            ItemStack stack = enchantBookStack(selected);
            int owned = countEnchantBooks(selected.enchantId());
            drawStudyDetail(
                    gui,
                    minecraft,
                    stack,
                    enchantDisplayName(selected),
                    selected.unlocked(),
                    selected.progress(),
                    selected.researchCost(),
                    Component.translatable("gui.yha.creation.inventory_count_books", owned).getString(),
                    owned,
                    mouseX,
                    mouseY,
                    detailX,
                    detailY,
                    detailW
            );
        }

        private void drawStudyDetail(
                GuiGraphicsExtractor gui,
                Minecraft minecraft,
                ItemStack stack,
                String name,
                boolean unlocked,
                int progress,
                int researchCost,
                String ownedLabel,
                int owned,
                int mouseX,
                int mouseY,
                int detailX,
                int detailY,
                int detailW
        ) {
            int cx = detailX + detailW / 2;
            int cy = detailY + 36;
            int required = Math.max(1, researchCost);
            float ratio = unlocked ? 1.0f : progress / (float) required;
            int fill = unlocked ? 0xFF55FF55 : 0xFFFFC14A;
            drawCircularProgress(gui, cx, cy, 13, 17, ratio, fill, 0xFF3B5A78, required);
            drawItem(gui, stack, cx - DETAIL_ICON / 2, cy - DETAIL_ICON / 2, DETAIL_ICON);
            gui.text(minecraft.font, trim(minecraft, name, detailW - 12), detailX + 6, detailY + 60, this.owner.textColor, false);
            String status = unlocked ? "Understood" : progress + " / " + required;
            gui.text(minecraft.font, status, detailX + 6, detailY + 72, unlocked ? 0xFF8BD9A7 : 0xFFFFC14A, false);
            gui.text(minecraft.font, ownedLabel, detailX + 6, detailY + 88, owned > 0 ? 0xFFC5DCF0 : 0xFFFF9E9E, false);
            if (unlocked) {
                return;
            }
            boolean hovered = contains(mouseX, mouseY, sacrificeX(), sacrificeY(), sacrificeW(), sacrificeH());
            boolean canSacrifice = owned > 0;
            int fillColor = !canSacrifice ? 0xAA1C222B : (hovered ? 0xAA2A5A78 : 0xAA1C3D54);
            int border = !canSacrifice ? 0xFF44596E : (hovered ? 0xFF9AD1FF : 0xFF79B8FF);
            drawFrame(gui, sacrificeX(), sacrificeY(), sacrificeW(), sacrificeH(), fillColor, border);
            String label = Component.translatable("gui.yha.creation.sacrifice").getString();
            gui.text(
                    minecraft.font,
                    label,
                    sacrificeX() + (sacrificeW() - minecraft.font.width(label)) / 2,
                    sacrificeY() + 4,
                    canSacrifice ? this.owner.textColor : 0xFF7A8A9D,
                    false
            );
        }

        private static ItemStack enchantBookStack(CreationSyncPayload.ClientEnchantEntry entry) {
            try {
                Identifier id = Identifier.parse(entry.enchantId());
                HolderLookup.Provider access = clientAccess();
                return CreationEnchantments.bookPreview(access, id, Math.max(1, entry.maxLevel()));
            } catch (RuntimeException ignored) {
                return ItemStack.EMPTY;
            }
        }

        private static String enchantDisplayName(CreationSyncPayload.ClientEnchantEntry entry) {
            try {
                return CreationEnchantments.displayName(clientAccess(), Identifier.parse(entry.enchantId())).getString();
            } catch (RuntimeException ignored) {
                return entry.enchantId();
            }
        }

        private static HolderLookup.Provider clientAccess() {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.level == null ? null : minecraft.level.registryAccess();
        }

        private static int countEnchantBooks(String enchantId) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || enchantId == null) {
                return 0;
            }
            try {
                return CreationEnchantments.countBooksContaining(minecraft.player, Identifier.parse(enchantId));
            } catch (RuntimeException ignored) {
                return 0;
            }
        }

        private static int countInInventory(ItemStack match) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || match == null || match.isEmpty()) {
                return 0;
            }
            int count = 0;
            for (int i = 0; i < minecraft.player.getInventory().getContainerSize(); i++) {
                ItemStack slot = minecraft.player.getInventory().getItem(i);
                if (!slot.isEmpty() && slot.is(match.getItem())) {
                    count += slot.getCount();
                }
            }
            return count;
        }

        private static void drawCircularProgress(GuiGraphicsExtractor gui, int cx, int cy, int innerR, int outerR, float progress, int fill, int empty, int required) {
            int segments = Math.max(1, required);
            int filled = Math.round(segments * Mth.clamp(progress, 0.0f, 1.0f));
            double full = Math.PI * 2.0;
            double gap = full / segments * 0.18;
            for (int i = 0; i < segments; i++) {
                double start = -Math.PI / 2.0 + (full * i) / segments + gap / 2.0;
                double end = start + full / segments - gap;
                fillArc(gui, cx, cy, innerR, outerR, start, end, i < filled ? fill : empty);
            }
        }

        private static void fillArc(GuiGraphicsExtractor gui, int cx, int cy, int innerR, int outerR, double start, double end, int color) {
            for (int r = innerR; r <= outerR; r++) {
                int steps = Math.max(4, (int) Math.round(r * Math.abs(end - start) * 1.6));
                for (int s = 0; s <= steps; s++) {
                    double angle = start + (end - start) * s / (double) steps;
                    int x = cx + (int) Math.round(Math.cos(angle) * r);
                    int y = cy + (int) Math.round(Math.sin(angle) * r);
                    int thickness = r == innerR || r == outerR ? 1 : 2;
                    gui.fill(x, y, x + thickness, y + thickness, color);
                }
            }
        }

        private static void drawItem(GuiGraphicsExtractor gui, ItemStack stack, int x, int y, int size) {
            if (stack == null || stack.isEmpty() || size <= 0) {
                return;
            }
            float scale = size / 16.0f;
            Matrix3x2fStack pose = gui.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(scale, scale);
            gui.item(stack, 0, 0);
            pose.popMatrix();
        }

        private static void drawFrame(GuiGraphicsExtractor gui, int x, int y, int width, int height, int fill, int border) {
            gui.fill(x, y, x + width, y + height, fill);
            gui.fill(x, y, x + width, y + 1, border);
            gui.fill(x, y + height - 1, x + width, y + height, border);
            gui.fill(x, y, x + 1, y + height, border);
            gui.fill(x + width - 1, y, x + width, y + height, border);
        }

        private static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        private static int ceilDiv(int value, int divisor) {
            return (value + divisor - 1) / divisor;
        }

        private static String trim(Minecraft minecraft, String text, int maxWidth) {
            if (minecraft.font.width(text) <= maxWidth) {
                return text;
            }
            String ellipsis = "...";
            int budget = maxWidth - minecraft.font.width(ellipsis);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                if (minecraft.font.width(builder.toString() + text.charAt(i)) > budget) {
                    break;
                }
                builder.append(text.charAt(i));
            }
            return builder + ellipsis;
        }

        private static void clickSound() {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private enum FilterTab {
        ALL(null, "gui.yha.creation.tab.all"),
        MATERIALS(CreationTab.MATERIALS, "gui.yha.creation.tab.materials"),
        BLOCKS(CreationTab.BLOCKS, "gui.yha.creation.tab.blocks"),
        GEAR(CreationTab.GEAR, "gui.yha.creation.tab.gear"),
        ENCHANTS(null, "gui.yha.creation.tab.enchants");

        private final CreationTab tab;
        private final String langKey;

        FilterTab(CreationTab tab, String langKey) {
            this.tab = tab;
            this.langKey = langKey;
        }

        boolean isEnchants() {
            return this == ENCHANTS;
        }

        String label() {
            return Component.translatable(this.langKey).getString();
        }
    }

    public static class Serializer extends UiWidgetSerializer<ResearchTablePanelUiComponent> {
        @Override
        public MapCodec<ResearchTablePanelUiComponent> codec() {
            return CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiWidget, ResearchTablePanelUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("Research Table Panel")
                    .setDescription("Lists Creation recipes the player can learn and sacrifice items to unlock.");
        }
    }
}
