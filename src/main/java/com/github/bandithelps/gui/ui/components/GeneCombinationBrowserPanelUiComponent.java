package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.client.gene_combiner.ClientGeneCombinationBrowserState;
import com.github.bandithelps.client.gene_combiner.ClientGeneCombinationBrowserState.RecipeEntry;
import com.github.bandithelps.client.gene_combiner.ClientGeneCombinationBrowserState.RequirementEntry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.threetag.palladium.client.gui.ui.widget.UiWidget;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetProperties;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetSerializer;
import net.threetag.palladium.client.gui.ui.screen.UiScreen;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import org.lwjgl.glfw.GLFW;

public class GeneCombinationBrowserPanelUiComponent extends UiWidget {
    public static final MapCodec<GeneCombinationBrowserPanelUiComponent> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("frame_color", 0xFF79B8FF).forGetter(GeneCombinationBrowserPanelUiComponent::getFrameColor),
            Codec.INT.optionalFieldOf("panel_color", 0xCC172231).forGetter(GeneCombinationBrowserPanelUiComponent::getPanelColor),
            Codec.INT.optionalFieldOf("text_color", 0xFFE6F2FF).forGetter(GeneCombinationBrowserPanelUiComponent::getTextColor),
            propertiesCodec(386, 206)
    ).apply(instance, GeneCombinationBrowserPanelUiComponent::new));

    private final int frameColor;
    private final int panelColor;
    private final int textColor;

    public GeneCombinationBrowserPanelUiComponent(int frameColor, int panelColor, int textColor, UiWidgetProperties properties) {
        super(properties);
        this.frameColor = withOpaqueAlpha(frameColor);
        this.panelColor = withOpaqueAlpha(panelColor);
        this.textColor = withOpaqueAlpha(textColor);
    }

    @Override
    public UiWidgetSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.GENE_COMBINATION_BROWSER_PANEL;
    }

    @Override
    public AbstractWidget buildWidget(UiScreen screen, ScreenRectangle rectangle) {
        return new BrowserWidget(this, this.getX(rectangle), this.getY(rectangle), this.getWidth(), this.getHeight());
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

    private static final class BrowserWidget extends AbstractWidget {
        private static final int LEFT_PANEL_WIDTH = 132;
        private static final int SEARCH_HEIGHT = 14;
        private static final int LIST_ROW_HEIGHT = 16;

        private final GeneCombinationBrowserPanelUiComponent owner;
        private final ArrayDeque<String> history = new ArrayDeque<>();

        private List<RecipeEntry> lastRecipes = List.of();
        private Map<String, GeneInfo> geneInfoById = Map.of();
        private List<String> allGeneIds = List.of();
        private List<String> filteredGeneIds = List.of();

        private String selectedGeneId = "";
        private String searchQuery = "";
        private String lastFilterQuery = "";
        private int listScroll = 0;
        private int requirementScroll = 0;
        private boolean searchFocused;

        private BrowserWidget(GeneCombinationBrowserPanelUiComponent owner, int x, int y, int width, int height) {
            super(x, y, width, height, Component.literal("Gene Combination Browser"));
            this.owner = owner;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
            refreshData();
            applyFilterIfNeeded();

            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();

            drawFrame(gui, x, y, width, height, this.owner.panelColor, this.owner.frameColor);

            int leftX = x + 6;
            int leftY = y + 8;
            int leftH = height - 16;
            drawLeftPanel(gui, leftX, leftY, LEFT_PANEL_WIDTH, leftH, mouseX, mouseY);

            int mainX = leftX + LEFT_PANEL_WIDTH + 8;
            int mainY = leftY;
            int mainW = width - (mainX - x) - 6;
            int mainH = leftH;
            drawGraphPanel(gui, mainX, mainY, mainW, mainH, mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            int mouseX = (int) event.x();
            int mouseY = (int) event.y();

            int leftX = this.getX() + 6;
            int leftY = this.getY() + 8;
            int leftH = this.getHeight() - 16;
            int searchX = leftX + 4;
            int searchY = leftY + 34;
            int searchW = LEFT_PANEL_WIDTH - 8;
            this.searchFocused = contains(mouseX, mouseY, searchX, searchY, searchW, SEARCH_HEIGHT);
            this.setFocused(this.searchFocused);

            if (contains(mouseX, mouseY, leftX + 4, leftY + 18, 48, 12) && !this.history.isEmpty()) {
                String previous = this.history.pop();
                selectGene(previous, false);
                return true;
            }

            int listX = leftX + 4;
            int listY = searchY + SEARCH_HEIGHT + 4;
            int listH = leftH - (listY - leftY) - 4;
            if (contains(mouseX, mouseY, listX, listY, LEFT_PANEL_WIDTH - 8, listH) && event.button() == 0) {
                int visibleRows = Math.max(1, listH / LIST_ROW_HEIGHT);
                int rowIndex = (mouseY - listY) / LIST_ROW_HEIGHT;
                int index = this.listScroll + rowIndex;
                if (index >= 0 && index < this.filteredGeneIds.size()) {
                    selectGene(this.filteredGeneIds.get(index), true);
                }
                return true;
            }

            if (event.button() != 0) {
                return false;
            }
            ClickableNode clickedNode = findClickedGraphNode(mouseX, mouseY);
            if (clickedNode != null) {
                if (clickedNode.navigable()) {
                    selectGene(clickedNode.geneId(), true);
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            RecipeEntry selectedRecipe = getSelectedRecipe();
            if (selectedRecipe != null && selectedRecipe.valid()) {
                int x = this.getX() + 6 + LEFT_PANEL_WIDTH + 8;
                int y = this.getY() + 8;
                int width = this.getWidth() - (x - this.getX()) - 6;
                int height = this.getHeight() - 16;
                int graphX = x + 6;
                int graphY = y + 18;
                int graphW = width - 12;
                int graphH = height - 24;
                int infoY = graphY + 6;
                int infoH = 86;
                int reqBoxX = graphX + 6;
                int reqBoxY = infoY + infoH + 8;
                int reqBoxW = graphW - 12;
                int reqBoxH = graphY + graphH - 6 - reqBoxY;

                if (contains((int) mouseX, (int) mouseY, reqBoxX, reqBoxY, reqBoxW, reqBoxH)) {
                    List<RequirementEntry> requirements = selectedRecipe.requirements();
                    int rowH = 16;
                    int rowGap = 3;
                    int contentH = Math.max(1, reqBoxH - 22);
                    int visibleRows = Math.max(1, contentH / (rowH + rowGap));
                    int maxScroll = Math.max(0, requirements.size() - visibleRows);
                    if (scrollY > 0) {
                        this.requirementScroll = Math.max(0, this.requirementScroll - 1);
                    } else if (scrollY < 0) {
                        this.requirementScroll = Math.min(maxScroll, this.requirementScroll + 1);
                    }
                    return true;
                }
            }

            int leftX = this.getX() + 6;
            int leftY = this.getY() + 8;
            int leftH = this.getHeight() - 16;
            int listX = leftX + 4;
            int listY = leftY + 34 + SEARCH_HEIGHT + 4;
            int listH = leftH - (listY - leftY) - 4;
            int listW = LEFT_PANEL_WIDTH - 8;
            if (!contains((int) mouseX, (int) mouseY, listX, listY, listW, listH)) {
                return false;
            }
            int visibleRows = Math.max(1, listH / LIST_ROW_HEIGHT);
            int maxScroll = Math.max(0, this.filteredGeneIds.size() - visibleRows);
            if (scrollY > 0) {
                this.listScroll = Math.max(0, this.listScroll - 1);
            } else if (scrollY < 0) {
                this.listScroll = Math.min(maxScroll, this.listScroll + 1);
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
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_DELETE) {
                this.searchQuery = "";
                return true;
            }
            Character typed = keyToCharacter(key);
            if (typed != null) {
                this.searchQuery += typed;
                return true;
            }
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            narrationElementOutput.add(NarratedElementType.TITLE, this.getMessage());
        }

        private void drawLeftPanel(GuiGraphicsExtractor gui, int x, int y, int width, int height, int mouseX, int mouseY) {
            Minecraft minecraft = Minecraft.getInstance();
            drawFrame(gui, x, y, width, height, 0xAA111A26, this.owner.frameColor);
            String title = "Genes";
            gui.text(minecraft.font, title, x + (width - minecraft.font.width(title)) / 2, y + 5, this.owner.textColor, false);

            boolean hasHistory = !this.history.isEmpty();
            int backFill = hasHistory ? 0xAA1C3D54 : 0xAA1C222B;
            int backBorder = hasHistory ? 0xFF79B8FF : 0xFF44596E;
            drawFrame(gui, x + 4, y + 18, 48, 12, backFill, backBorder);
            String backText = "< Back";
            int backColor = hasHistory ? 0xFFE6F2FF : 0xFF7A8A9D;
            gui.text(minecraft.font, backText, x + 4 + (48 - minecraft.font.width(backText)) / 2, y + 20, backColor, false);

            int searchX = x + 4;
            int searchY = y + 34;
            int searchW = width - 8;
            int searchBorder = this.searchFocused ? 0xFF9AD1FF : 0xFF3E5A74;
            drawFrame(gui, searchX, searchY, searchW, SEARCH_HEIGHT, 0xAA0E1622, searchBorder);
            String shown = this.searchQuery.isBlank() ? "Search genes..." : this.searchQuery;
            int shownColor = this.searchQuery.isBlank() ? 0xFF7D8FA4 : 0xFFE6F2FF;
            gui.text(minecraft.font, trimToWidth(minecraft, shown, searchW - 8), searchX + 3, searchY + 3, shownColor, false);
            if (this.searchFocused && ((System.currentTimeMillis() / 400L) % 2L == 0L)) {
                int cursorX = searchX + 3 + minecraft.font.width(trimToWidth(minecraft, this.searchQuery, searchW - 10));
                gui.fill(cursorX, searchY + 2, cursorX + 1, searchY + SEARCH_HEIGHT - 2, 0xFFD2E9FF);
            }

            int listX = x + 4;
            int listY = searchY + SEARCH_HEIGHT + 4;
            int listW = width - 8;
            int listH = height - (listY - y) - 4;
            drawFrame(gui, listX, listY, listW, listH, 0xAA0E1622, 0xFF36526B);
            drawGeneList(gui, listX + 1, listY + 1, listW - 2, listH - 2, mouseX, mouseY);
        }

        private void drawGeneList(GuiGraphicsExtractor gui, int x, int y, int width, int height, int mouseX, int mouseY) {
            Minecraft minecraft = Minecraft.getInstance();
            int visibleRows = Math.max(1, height / LIST_ROW_HEIGHT);
            int maxScroll = Math.max(0, this.filteredGeneIds.size() - visibleRows);
            this.listScroll = clamp(this.listScroll, 0, maxScroll);
            int scrollbarWidth = maxScroll > 0 ? 4 : 0;
            int contentWidth = width - scrollbarWidth;

            int end = Math.min(this.filteredGeneIds.size(), this.listScroll + visibleRows);
            for (int i = this.listScroll; i < end; i++) {
                int row = i - this.listScroll;
                int rowY = y + row * LIST_ROW_HEIGHT;
                String geneId = this.filteredGeneIds.get(i);
                GeneInfo info = this.geneInfoById.get(geneId);
                boolean selected = geneId.equalsIgnoreCase(this.selectedGeneId);
                boolean hovered = contains(mouseX, mouseY, x, rowY, contentWidth, LIST_ROW_HEIGHT);

                int fill = selected ? 0xAA26557A : hovered ? 0xAA1B334A : 0xAA152435;
                int border = selected ? 0xFF9AD1FF : hovered ? 0xFF689BC4 : 0xFF2F4A63;
                drawFrame(gui, x, rowY, contentWidth, LIST_ROW_HEIGHT - 1, fill, border);

                String label = info == null || info.displayName().isBlank() ? geneId : info.displayName();
                String text = trimToWidth(minecraft, label, contentWidth - 8);
                gui.text(minecraft.font, text, x + 4, rowY + 4, 0xFFE1F0FF, false);
            }

            if (this.filteredGeneIds.isEmpty()) {
                gui.text(minecraft.font, "No matches", x + 4, y + 4, 0xFF7A8EA5, false);
            }

            if (maxScroll > 0) {
                int barX = x + contentWidth + 1;
                gui.fill(barX, y, barX + 2, y + height, 0xFF1E3348);
                int thumbHeight = Math.max(10, (height * visibleRows) / this.filteredGeneIds.size());
                int track = Math.max(1, height - thumbHeight);
                int thumbY = y + (track * this.listScroll / maxScroll);
                gui.fill(barX, thumbY, barX + 2, thumbY + thumbHeight, 0xFF84C0EE);
            }
        }

        private void drawGraphPanel(GuiGraphicsExtractor gui, int x, int y, int width, int height, int mouseX, int mouseY) {
            Minecraft minecraft = Minecraft.getInstance();
            drawFrame(gui, x, y, width, height, 0xAA111A26, this.owner.frameColor);

            RecipeEntry selectedRecipe = getSelectedRecipe();
            String title = selectedRecipe == null
                    ? "Gene Combination Details"
                    : displayNameFor(selectedRecipe.outputGeneId()) + " Details";
            gui.text(minecraft.font, trimToWidth(minecraft, title, width - 10), x + 4, y + 4, this.owner.textColor, false);

            int graphX = x + 6;
            int graphY = y + 18;
            int graphW = width - 12;
            int graphH = height - 24;
            drawFrame(gui, graphX, graphY, graphW, graphH, 0xAA0E1622, 0xFF36526B);

            if (this.selectedGeneId.isBlank()) {
                gui.text(minecraft.font, "Select a gene from the list.", graphX + 8, graphY + 10, 0xFF97A8BA, false);
                return;
            }

            String geneId = this.selectedGeneId;
            GeneInfo selectedInfo = this.geneInfoById.get(normalize(geneId));

            int infoX = graphX + 6;
            int infoY = graphY + 6;
            int infoW = graphW - 12;
            int infoH = 86;
            int rarityBorder = rarityColor(selectedInfo == null ? "" : selectedInfo.rarity());
            drawFrame(gui, infoX, infoY, infoW, infoH, 0xAA132538, rarityBorder);

            List<String> idLines = wrapText(minecraft, "ID: " + geneId, infoW - 10);
            int textY = infoY + 5;
            for (String line : idLines) {
                gui.text(minecraft.font, line, infoX + 5, textY, 0xFFAFCBDF, false);
                textY += 9;
            }

            String category = selectedInfo == null || selectedInfo.category().isBlank() ? "Unknown" : selectedInfo.category();
            String quality = selectedInfo == null
                    ? "1-100"
                    : selectedInfo.qualityMin() + "-" + selectedInfo.qualityMax();
            List<String> categoryLines = wrapText(minecraft, "Category: " + category, infoW - 10);
            for (String line : categoryLines) {
                gui.text(minecraft.font, line, infoX + 5, textY, 0xFFC5DCF0, false);
                textY += 9;
            }
            gui.text(minecraft.font, "Quality Range: " + quality, infoX + 5, textY, 0xFFC5DCF0, false);
            textY += 11;

            String statusLine;
            if (selectedRecipe == null) {
                statusLine = "No resolved recipe";
            } else if (!selectedRecipe.valid()) {
                statusLine = "Recipe invalid";
            } else {
                statusLine = "Success Rate: " + selectedRecipe.successRate() + "%";
            }
            int statusColor = selectedRecipe == null ? 0xFF9AAFC5 : (selectedRecipe.valid() ? 0xFFA4E4B4 : 0xFFFF9E9E);
            gui.text(minecraft.font, statusLine, infoX + 5, textY, statusColor, false);
            textY += 11;

            String description = selectedInfo == null ? "" : selectedInfo.description();
            if (description.isBlank()) {
                description = "No description available.";
            }
            List<String> wrapped = wrapText(minecraft, description, infoW - 10);
            int maxDescriptionLines = Math.max(1, (infoY + infoH - 4 - textY) / 9);
            for (int i = 0; i < wrapped.size() && i < maxDescriptionLines; i++) {
                gui.text(minecraft.font, wrapped.get(i), infoX + 5, textY + (i * 9), 0xFFD4E7F8, false);
            }

            int reqBoxX = graphX + 6;
            int reqBoxY = infoY + infoH + 3;
            int reqBoxW = graphW - 12;
            int reqBoxH = graphY + graphH - 6 - reqBoxY;
            drawFrame(gui, reqBoxX, reqBoxY, reqBoxW, reqBoxH, 0xAA0F1C2B, 0xFF3E5F80);
            gui.text(minecraft.font, "Combination Requirements", reqBoxX + 5, reqBoxY + 4, 0xFFE6F2FF, false);

            if (selectedRecipe == null) {
                gui.text(minecraft.font, "This gene cannot be crafted by", reqBoxX + 5, reqBoxY + 18, 0xFF9AAFC5, false);
                gui.text(minecraft.font, "combination.", reqBoxX + 5, reqBoxY + 27, 0xFF9AAFC5, false);
                return;
            }

            if (!selectedRecipe.valid()) {
                String reason = selectedRecipe.invalidReason().isBlank() ? "Unknown invalid reason." : selectedRecipe.invalidReason();
                gui.text(minecraft.font, trimToWidth(minecraft, reason, reqBoxW - 10), reqBoxX + 5, reqBoxY + 18, 0xFFFFB2B2, false);
                return;
            }

            List<RequirementEntry> requirements = selectedRecipe.requirements();
            if (requirements.isEmpty()) {
                gui.text(minecraft.font, "No requirements.", reqBoxX + 5, reqBoxY + 18, 0xFF9AAFC5, false);
                return;
            }

            int rowX = reqBoxX + 4;
            int rowY = reqBoxY + 16;
            int rowW = reqBoxW - 8;
            int rowH = 16;
            int rowGap = 3;
            int contentH = Math.max(1, reqBoxH - 16);
            int visibleRows = Math.max(1, contentH / (rowH + rowGap));
            int maxScroll = Math.max(0, requirements.size() - visibleRows);
            this.requirementScroll = clamp(this.requirementScroll, 0, maxScroll);
            int scrollbarWidth = maxScroll > 0 ? 4 : 0;
            int contentWidth = rowW - scrollbarWidth;
            int end = Math.min(requirements.size(), this.requirementScroll + visibleRows);
            for (int i = this.requirementScroll; i < end; i++) {
                RequirementEntry requirement = requirements.get(i);
                int itemY = rowY + (i - this.requirementScroll) * (rowH + rowGap);
                boolean hovered = contains(mouseX, mouseY, rowX, itemY, contentWidth, rowH);
                boolean hasRecipe = ClientGeneCombinationBrowserState.getRecipe(requirement.geneId()) != null;
                int fill = hovered ? 0xAA274664 : hasRecipe ? 0xAA1C374F : 0xAA172A3D;
                int border = hovered ? 0xFF9AD1FF : hasRecipe ? 0xFF77B3E2 : 0xFF4E6D89;
                if (requirement.builderResolved()) {
                    fill = hovered ? 0xAA503F2B : 0xAA3B2F22;
                    border = hovered ? 0xFFF3C082 : 0xFFBB8E57;
                }
                drawFrame(gui, rowX, itemY, contentWidth, rowH, fill, border);

                String requirementName = displayNameFor(requirement.geneId());
                String qualityText = "q>=" + requirement.minQuality();
                int qualityWidth = minecraft.font.width(qualityText);
                String clickableHint = hasRecipe ? " *" : "";
                int hintWidth = minecraft.font.width(clickableHint);
                int leftTextMax = contentWidth - 8 - qualityWidth - hintWidth - 4;
                String leftText = trimToWidth(minecraft, requirementName, leftTextMax);
                gui.text(minecraft.font, leftText, rowX + 4, itemY + 4, 0xFFE6F2FF, false);
                if (!clickableHint.isBlank()) {
                    gui.text(minecraft.font, clickableHint, rowX + 4 + minecraft.font.width(leftText), itemY + 4, 0xFF8BD9A7, false);
                }
                gui.text(minecraft.font, qualityText, rowX + contentWidth - qualityWidth - 4, itemY + 4, 0xFFC5DCF0, false);
            }

            if (maxScroll > 0) {
                int barX = rowX + contentWidth + 1;
                gui.fill(barX, rowY, barX + 2, rowY + contentH - 5, 0xFF1E3348);
                int thumbHeight = Math.max(10, (contentH - 2 * visibleRows) / requirements.size());
                int track = Math.max(1, contentH - 5 - thumbHeight);
                int thumbY = rowY + (track * this.requirementScroll / maxScroll);
                gui.fill(barX, thumbY, barX + 2, thumbY + thumbHeight, 0xFF84C0EE);
            }
        }

        private ClickableNode findClickedGraphNode(int mouseX, int mouseY) {
            RecipeEntry selectedRecipe = getSelectedRecipe();
            if (selectedRecipe == null || !selectedRecipe.valid()) {
                return null;
            }
            int x = this.getX() + 6 + LEFT_PANEL_WIDTH + 8;
            int y = this.getY() + 8;
            int width = this.getWidth() - (x - this.getX()) - 6;
            int height = this.getHeight() - 16;
            int graphX = x + 6;
            int graphY = y + 18;
            int graphW = width - 12;
            int graphH = height - 24;

            List<RequirementEntry> requirements = selectedRecipe.requirements();
            if (requirements.isEmpty()) {
                return null;
            }

            int infoY = graphY + 6;
            int infoH = 78;
            int reqBoxX = graphX + 6;
            int reqBoxY = infoY + infoH + 8;
            int reqBoxW = graphW - 12;
            int reqBoxH = graphY + graphH - 6 - reqBoxY;
            int rowX = reqBoxX + 4;
            int rowY = reqBoxY + 18;
            int rowW = reqBoxW - 8;
            int rowH = 16;
            int rowGap = 3;
            int visibleRows = Math.max(1, (Math.max(1, reqBoxH - 22)) / (rowH + rowGap));
            int maxScroll = Math.max(0, requirements.size() - visibleRows);
            int scrollbarWidth = maxScroll > 0 ? 4 : 0;
            int contentWidth = rowW - scrollbarWidth;
            int start = clamp(this.requirementScroll, 0, maxScroll);
            int end = Math.min(requirements.size(), start + visibleRows);
            for (int i = start; i < end; i++) {
                int itemY = rowY + (i - start) * (rowH + rowGap);
                if (contains(mouseX, mouseY, rowX, itemY, contentWidth, rowH)) {
                    String geneId = requirements.get(i).geneId();
                    boolean hasRecipe = ClientGeneCombinationBrowserState.getRecipe(geneId) != null;
                    return new ClickableNode(geneId, hasRecipe);
                }
            }
            return null;
        }

        private void selectGene(String geneId, boolean pushHistory) {
            if (geneId == null || geneId.isBlank()) {
                return;
            }
            if (pushHistory && !this.selectedGeneId.isBlank() && !this.selectedGeneId.equalsIgnoreCase(geneId)) {
                this.history.push(this.selectedGeneId);
            }
            this.selectedGeneId = geneId.toLowerCase(Locale.ROOT);
            this.requirementScroll = 0;
            ensureSelectedVisible();
        }

        private void ensureSelectedVisible() {
            int selectedIndex = this.filteredGeneIds.indexOf(this.selectedGeneId);
            if (selectedIndex < 0) {
                return;
            }
            int leftY = this.getY() + 8;
            int leftH = this.getHeight() - 16;
            int listY = leftY + 34 + SEARCH_HEIGHT + 4;
            int listH = leftH - (listY - leftY) - 4;
            int visibleRows = Math.max(1, listH / LIST_ROW_HEIGHT);
            if (selectedIndex < this.listScroll) {
                this.listScroll = selectedIndex;
            } else if (selectedIndex >= this.listScroll + visibleRows) {
                this.listScroll = selectedIndex - visibleRows + 1;
            }
        }

        private RecipeEntry getSelectedRecipe() {
            if (this.selectedGeneId.isBlank()) {
                return null;
            }
            return ClientGeneCombinationBrowserState.getRecipe(this.selectedGeneId);
        }

        private String displayNameFor(String geneId) {
            if (geneId == null || geneId.isBlank()) {
                return "";
            }
            GeneInfo info = this.geneInfoById.get(geneId.toLowerCase(Locale.ROOT));
            if (info != null && !info.displayName().isBlank()) {
                return info.displayName();
            }
            return geneId;
        }

        private void refreshData() {
            List<RecipeEntry> recipes = ClientGeneCombinationBrowserState.getRecipes();
            if (recipes == this.lastRecipes) {
                return;
            }
            this.lastRecipes = recipes;

            Map<String, GeneInfo> infoMap = new HashMap<>();
            LinkedHashSet<String> genes = new LinkedHashSet<>();
            for (RecipeEntry recipe : recipes) {
                String outputId = normalize(recipe.outputGeneId());
                if (outputId.isBlank()) {
                    continue;
                }
                genes.add(outputId);
                infoMap.putIfAbsent(outputId, new GeneInfo(
                        recipe.displayName(),
                        recipe.category(),
                        recipe.rarity(),
                        recipe.qualityMin(),
                        recipe.qualityMax(),
                        recipe.description(),
                        recipe.mobs()
                ));
                for (RequirementEntry req : recipe.requirements()) {
                    String reqId = normalize(req.geneId());
                    if (reqId.isBlank()) {
                        continue;
                    }
                    genes.add(reqId);
                    infoMap.putIfAbsent(reqId, new GeneInfo(
                            req.displayName(),
                            req.category(),
                            req.rarity(),
                            req.qualityMin(),
                            req.qualityMax(),
                            req.description(),
                            req.mobs()
                    ));
                }
            }

            List<String> sorted = new ArrayList<>(genes);
            sorted.sort(Comparator.comparing(this::sortableLabel, String.CASE_INSENSITIVE_ORDER));
            this.geneInfoById = Map.copyOf(infoMap);
            this.allGeneIds = List.copyOf(sorted);
            this.lastFilterQuery = "";
            applyFilterIfNeeded();

            if (this.selectedGeneId.isBlank() && !this.filteredGeneIds.isEmpty()) {
                this.selectedGeneId = this.filteredGeneIds.get(0);
            } else if (!this.selectedGeneId.isBlank() && !this.allGeneIds.contains(this.selectedGeneId)) {
                this.selectedGeneId = this.filteredGeneIds.isEmpty() ? "" : this.filteredGeneIds.get(0);
            }
        }

        private void applyFilterIfNeeded() {
            if (this.lastFilterQuery.equalsIgnoreCase(this.searchQuery) && !this.filteredGeneIds.isEmpty()) {
                return;
            }
            String normalizedQuery = normalize(this.searchQuery);
            this.lastFilterQuery = this.searchQuery;
            if (normalizedQuery.isBlank()) {
                this.filteredGeneIds = this.allGeneIds;
                this.listScroll = 0;
                return;
            }
            List<String> matches = this.allGeneIds.stream()
                    .filter(id -> {
                        GeneInfo info = this.geneInfoById.get(id);
                        String label = info == null ? "" : normalize(info.displayName());
                        return id.contains(normalizedQuery) || label.contains(normalizedQuery);
                    })
                    .toList();
            this.filteredGeneIds = matches;
            this.listScroll = 0;
            if (!this.selectedGeneId.isBlank() && !this.filteredGeneIds.contains(this.selectedGeneId)) {
                this.selectedGeneId = this.filteredGeneIds.isEmpty() ? "" : this.filteredGeneIds.get(0);
            }
        }

        private String sortableLabel(String geneId) {
            GeneInfo info = this.geneInfoById.get(geneId);
            if (info == null || info.displayName().isBlank()) {
                return geneId;
            }
            return info.displayName() + "|" + geneId;
        }

        private static Character keyToCharacter(int key) {
            if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
                return (char) ('a' + (key - GLFW.GLFW_KEY_A));
            }
            if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
                return (char) ('0' + (key - GLFW.GLFW_KEY_0));
            }
            return switch (key) {
                case GLFW.GLFW_KEY_SPACE -> ' ';
                case GLFW.GLFW_KEY_MINUS -> '-';
                case GLFW.GLFW_KEY_PERIOD -> '.';
                case GLFW.GLFW_KEY_SLASH -> '/';
                case GLFW.GLFW_KEY_SEMICOLON -> ':';
                case GLFW.GLFW_KEY_APOSTROPHE -> '\'';
                case GLFW.GLFW_KEY_COMMA -> ',';
                case GLFW.GLFW_KEY_EQUAL -> '=';
                case GLFW.GLFW_KEY_LEFT_BRACKET -> '[';
                case GLFW.GLFW_KEY_RIGHT_BRACKET -> ']';
                case GLFW.GLFW_KEY_BACKSLASH -> '\\';
                default -> null;
            };
        }

        private static List<String> wrapText(Minecraft minecraft, String text, int maxWidth) {
            List<String> lines = new ArrayList<>();
            if (text == null || text.isBlank()) {
                lines.add("");
                return lines;
            }
            String[] words = text.replace('\n', ' ').trim().split("\\s+");
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (minecraft.font.width(candidate) <= maxWidth) {
                    current.setLength(0);
                    current.append(candidate);
                    continue;
                }
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                if (minecraft.font.width(word) <= maxWidth) {
                    current.append(word);
                } else {
                    lines.add(trimToWidth(minecraft, word, maxWidth));
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            if (lines.isEmpty()) {
                lines.add("");
            }
            return lines;
        }

        private static int rarityColor(String rarity) {
            if (rarity == null) {
                return 0xFF4E7AA0;
            }
            return switch (rarity.toUpperCase(Locale.ROOT)) {
                case "COMMON" -> 0xFF7F8C98;
                case "UNCOMMON" -> 0xFF5FA86B;
                case "RARE" -> 0xFF5E8FE0;
                case "EPIC" -> 0xFF9A63E6;
                case "LEGENDARY" -> 0xFFF0A84E;
                default -> 0xFF4E7AA0;
            };
        }

        private static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        private static String normalize(String value) {
            return value == null ? "" : value.toLowerCase(Locale.ROOT);
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
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

        private static void drawFrame(GuiGraphicsExtractor gui, int x, int y, int width, int height, int fill, int border) {
            gui.fill(x, y, x + width, y + height, fill);
            gui.fill(x, y, x + width, y + 1, border);
            gui.fill(x, y + height - 1, x + width, y + height, border);
            gui.fill(x, y, x + 1, y + height, border);
            gui.fill(x + width - 1, y, x + width, y + height, border);
        }

        private record ClickableNode(String geneId, boolean navigable) {
        }

        private record GeneInfo(
                String displayName,
                String category,
                String rarity,
                int qualityMin,
                int qualityMax,
                String description,
                List<String> mobs
        ) {
        }
    }

    public static class Serializer extends UiWidgetSerializer<GeneCombinationBrowserPanelUiComponent> {
        @Override
        public MapCodec<GeneCombinationBrowserPanelUiComponent> codec() {
            return GeneCombinationBrowserPanelUiComponent.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiWidget, GeneCombinationBrowserPanelUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("Gene Combination Browser Panel")
                    .setDescription("Visual recipe browser with searchable gene list, clickable graph nodes, and back navigation.");
        }
    }
}
