package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.client.body.ClientBodyState;
import com.github.bandithelps.client.creation.ClientCreationState;
import com.github.bandithelps.creation.CreationEnchantments;
import com.github.bandithelps.creation.CreationGearKind;
import com.github.bandithelps.creation.CreationGearSlot;
import com.github.bandithelps.creation.CreationTab;
import com.github.bandithelps.creation.CreationUtil;
import com.github.bandithelps.network.CreationAssignSlotPayload;
import com.github.bandithelps.network.CreationCreatePayload;
import com.github.bandithelps.network.CreationSyncPayload;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Matrix3x2fStack;
import net.threetag.palladium.client.gui.ui.screen.UiScreen;
import net.threetag.palladium.client.gui.ui.widget.UiWidget;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetProperties;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetSerializer;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;

public class CreationNotebookUiComponent extends UiWidget {
    private static final Identifier TEX_BLOCKS = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_gui.png");
    private static final Identifier TEX_GEAR = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_tool_gui.png");
    private static final Identifier TEX_CREATE = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_gui_create_button.png");
    private static final Identifier TEX_CREATE_HOVER = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_gui_create_button_hover.png");
    private static final Identifier TEX_LIPID = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/lipid_icon.png");
    private static final Identifier TEX_LOCKED_TAB = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_gui_green_question_tab.png");
    private static final Identifier[] TEX_GEAR_SLOTS = {
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_tool_gui_slots_1.png"),
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_tool_gui_slots_2.png"),
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_tool_gui_slots_3.png")
    };
    private static final Identifier TEX_ENCHANT_ARMOR = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_gui_armor_enchant.png");
    private static final Identifier TEX_ENCHANT_SWORD = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_gui_sword_enchant.png");
    private static final Identifier TEX_ENCHANT_TOOLS = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_gui_tools_enchant.png");
    private static final Identifier TEX_ENCHANT_UTILITY = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_gui_utility_enchant.png");

    private static final int TEX_W = 240;
    private static final int TEX_H = 193;
    private static final int SLOT = 15;
    private static final int GRID_ICON = 13;
    private static final int GEAR_SLOT = 12;
    private static final int GEAR_ICON = 12;
    private static final int QUICK_ICON = 15;
    private static final int MAT_X = 15;
    private static final int MAT_Y1 = 39;
    private static final int MAT_Y2 = 99;
    private static final int BLOCKS_X = 78;
    private static final int BLOCKS_Y = 39;
    private static final int BLOCKS_COLS = 6;
    private static final int BLOCKS_ROWS = 6;
    private static final int BLOCKS_LAST_ROW = 3;
    private static final int GEAR_X = 15;
    private static final int GEAR_Y = 39;
    private static final int GEAR_COLS = 4;
    private static final int GEAR_ROWS = 3;
    private static final int QUICK_X = 171;
    private static final int[] QUICK_Y = {59, 77, 95};
    private static final int QUICK_SIZE = 18;
    private static final int CREATE_X = 171;
    private static final int CREATE_Y = 120;
    private static final int CREATE_W = 59;
    private static final int CREATE_H = 17;
    private static final int DOG_X = 218;
    private static final int DOG_Y = 178;
    private static final int DOG_W = 20;
    private static final int DOG_H = 14;
    private static final int FORM_SLOT = 16;
    private static final int FORM_PAD = 3;
    private static final int FORM_ICON = 14;
    private static final int FORM_MENU_COLS = 6;
    private static final int TAB_W = 16;
    private static final int TAB_H = 16;
    private static final int TAB_LOCKED_1_X = 142;
    private static final int TAB_LOCKED_2_X = 162;
    private static final int TAB_GEAR_X = 182;
    private static final int TAB_BLOCKS_X = 202;
    private static final int ENCHANT_ATLAS_W = 155;
    private static final int ENCHANT_SLOT_SIZE = 24;
    private static final int ENCHANT_ROW_U = 0;
    private static final int ENCHANT_ROW_V = 25;
    private static final int ENCHANT_ROW_W = 75;
    private static final int ENCHANT_ROW_H = 20;
    private static final int ENCHANT_ROW_STRIDE = 21;
    private static final int ENCHANT_PANEL_X = 66;
    private static final int ENCHANT_PANEL_Y = 39;
    private static final int ENCHANT_LIST_X = GEAR_X;
    private static final int ENCHANT_LIST_Y = GEAR_Y + GEAR_ROWS * GEAR_SLOT + 8;
    private static final int ENCHANT_COLS = 2;
    private static final int ENCHANT_COL_GAP = 4;
    private static final int ENCHANT_VISIBLE_ROWS = 5;
    private static final int ENCHANT_SCROLLBAR_W = 4;
    private static final int ENCHANT_SCROLLBAR_TRACK = 0xFF8A6A48;
    private static final int ENCHANT_SCROLLBAR_THUMB = 0xFFFFD27A;
    private static final int SLIDER_PAD_X = 4;
    private static final int SLIDER_Y = 12;
    private static final int SLIDER_H = 6;

    public static final MapCodec<CreationNotebookUiComponent> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(TEX_W, TEX_H)
    ).apply(instance, CreationNotebookUiComponent::new));

    public CreationNotebookUiComponent(UiWidgetProperties properties) {
        super(properties);
    }

    @Override
    public UiWidgetSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.CREATION_NOTEBOOK;
    }

    @Override
    public AbstractWidget buildWidget(UiScreen screen, ScreenRectangle rectangle, DataContext context) {
        return new NotebookWidget(this.getX(rectangle, context), this.getY(rectangle, context), this.getWidth(context), this.getHeight(context));
    }

    private static final class NotebookWidget extends AbstractWidget {
        private enum Page { BLOCKS, GEAR }

        private Page page = Page.BLOCKS;
        private String selectedId;
        private int materialsPage;
        private int blocksPage;
        private final Map<String, Integer> enchantLevels = new HashMap<>();
        private int enchantScroll;
        private String draggingEnchantId;
        private final Map<String, String> selectedForms = new HashMap<>();
        private final Map<CreationGearSlot, String> selectedGearVariants = new HashMap<>();
        private String formMenuParent;
        private List<String> formMenuChoices = List.of();
        private int formMenuX;
        private int formMenuY;

        private NotebookWidget(int x, int y, int width, int height) {
            super(x, y, width, height, Component.literal("Creation"));
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            int x = this.getX();
            int y = this.getY();
            Identifier background = this.page == Page.GEAR ? TEX_GEAR : TEX_BLOCKS;
            blit(gui, background, x, y, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);

            if (!ClientCreationState.get().gearTabUnlocked()) {
                blit(gui, TEX_LOCKED_TAB, x + TAB_GEAR_X + 1, y + 1, 0, 0, 16, 16, 16, 16);
            }

            int quickSlots = ClientCreationState.get().unlockedQuickSlots();
            if (this.page == Page.GEAR && quickSlots > 0) {
                Identifier overlay = TEX_GEAR_SLOTS[Mth.clamp(quickSlots, 1, 3) - 1];
                int overlayH = 26 * quickSlots;
                blit(gui, overlay, x + QUICK_X, y + QUICK_Y[0], 0, 0, 26, overlayH, 26, overlayH);
            }

            blit(gui, TEX_LIPID, x + 176, y + 42, 0, 0, 9, 11, 9, 11);
            int lipids = Mth.floor(ClientBodyState.getCustomFloat(BodyPart.CHEST, CreationUtil.LIPIDS_KEY, 0.0f));
            gui.text(minecraft.font, ": " + lipids, x + 186, y + 44, 0xFF3A2A18, false);

            if (this.page == Page.BLOCKS) {
                drawGrid(gui, x, y, ClientCreationState.entriesForTab(CreationTab.MATERIALS, true), MAT_X, MAT_Y1, 3, 3, SLOT, this.materialsPage);
                drawGrid(gui, x, y, ClientCreationState.entriesForTab(CreationTab.MATERIALS, true), MAT_X, MAT_Y2, 3, 3, SLOT, this.materialsPage + 1);
                drawGrid(gui, x, y, ClientCreationState.entriesForTab(CreationTab.BLOCKS, true), BLOCKS_X, BLOCKS_Y, BLOCKS_COLS, BLOCKS_ROWS, SLOT, this.blocksPage, BLOCKS_LAST_ROW);
            } else {
                drawGearSlots(gui, x, y);
                drawEnchantPanel(gui, minecraft, x, y, mouseX, mouseY);
            }

            for (int i = 0; i < 3; i++) {
                Identifier assigned = i < quickSlots ? ClientCreationState.quickSlot(i) : null;
                if (assigned != null) {
                    int iconX = x + QUICK_X + (QUICK_SIZE - QUICK_ICON) / 2;
                    int iconY = y + QUICK_Y[i] + (QUICK_SIZE - QUICK_ICON) / 2;
                    drawItem(gui, ClientCreationState.stackOf(assigned.toString()), iconX, iconY, QUICK_ICON);
                } else if (i >= quickSlots && this.page == Page.BLOCKS) {
                    gui.fill(x + QUICK_X + 1, y + QUICK_Y[i] + 1, x + QUICK_X + QUICK_SIZE - 1, y + QUICK_Y[i] + QUICK_SIZE - 1, 0x66000000);
                }
            }

            CreationSyncPayload.ClientEntry selected = ClientCreationState.find(this.selectedId);
            int selectedCost = selected == null ? 0 : selected.formCost(displayedFormId(this.selectedId)) + selectedEnchantCost();
            boolean canCreate = selected != null && selected.unlocked() && lipids >= selectedCost;
            boolean createHovered = contains(mouseX, mouseY, x + CREATE_X, y + CREATE_Y, CREATE_W, CREATE_H);
            blit(gui, createHovered && canCreate ? TEX_CREATE_HOVER : TEX_CREATE, x + CREATE_X, y + CREATE_Y, 0, 0, CREATE_W, CREATE_H, CREATE_W, CREATE_H);
            if (!canCreate) {
                gui.fill(x + CREATE_X, y + CREATE_Y, x + CREATE_X + CREATE_W, y + CREATE_Y + CREATE_H, 0x66000000);
            }

            if (selected != null) {
                gui.text(minecraft.font, String.valueOf(selectedCost), x + CREATE_X, y + CREATE_Y + CREATE_H + 2, 0xFF3A2A18, false);
            }

            drawFormMenu(gui, mouseX, mouseY);
        }

        private void drawGrid(
                GuiGraphicsExtractor gui,
                int originX,
                int originY,
                List<CreationSyncPayload.ClientEntry> entries,
                int gridX,
                int gridY,
                int cols,
                int rows,
                int slotSize,
                int page
        ) {
            drawGrid(gui, originX, originY, entries, gridX, gridY, cols, rows, slotSize, page, cols);
        }

        private void drawGrid(
                GuiGraphicsExtractor gui,
                int originX,
                int originY,
                List<CreationSyncPayload.ClientEntry> entries,
                int gridX,
                int gridY,
                int cols,
                int rows,
                int slotSize,
                int page,
                int lastRowCols
        ) {
            int perPage = cols * (rows - 1) + lastRowCols;
            int start = page * perPage;
            int index = 0;
            for (int row = 0; row < rows; row++) {
                int rowCols = row == rows - 1 ? lastRowCols : cols;
                for (int col = 0; col < rowCols; col++) {
                    int entryIndex = start + index;
                    index++;
                    if (entryIndex < 0 || entryIndex >= entries.size()) {
                        continue;
                    }
                    CreationSyncPayload.ClientEntry entry = entries.get(entryIndex);
                    int slotX = originX + gridX + col * slotSize;
                    int slotY = originY + gridY + row * slotSize;
                    int iconSize = iconSizeForSlot(slotSize);
                    int iconX = slotX + Math.max(0, (slotSize - iconSize) / 2);
                    int iconY = slotY + Math.max(0, (slotSize - iconSize) / 2);
                    ItemStack stack = ClientCreationState.stackOf(displayedFormId(entry.itemId()));
                    drawItem(gui, stack, iconX, iconY, iconSize);
                    if (entry.hasForms()) {
                        gui.fill(slotX + slotSize - 4, slotY + 1, slotX + slotSize - 1, slotY + 4, 0xFFFFD27A);
                    }
                    if (entry.itemId().equals(this.selectedId)) {
                        gui.fill(iconX - 1, iconY - 1, iconX + iconSize + 1, iconY, 0xFFFFD27A);
                        gui.fill(iconX - 1, iconY + iconSize, iconX + iconSize + 1, iconY + iconSize + 1, 0xFFFFD27A);
                        gui.fill(iconX - 1, iconY - 1, iconX, iconY + iconSize + 1, 0xFFFFD27A);
                        gui.fill(iconX + iconSize, iconY - 1, iconX + iconSize + 1, iconY + iconSize + 1, 0xFFFFD27A);
                    }
                }
            }
        }

        private void drawFormMenu(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
            List<String> choices = this.formMenuChoices;
            if (choices == null || choices.isEmpty()) {
                return;
            }
            int cols = formMenuCols(choices.size());
            int rows = formMenuRows(choices.size());
            int width = formMenuWidth(cols);
            int height = formMenuHeight(rows);
            gui.fill(this.formMenuX, this.formMenuY, this.formMenuX + width, this.formMenuY + height, 0xF21A1410);
            drawFrame(gui, this.formMenuX, this.formMenuY, width, height, 0xFFFFD27A);
            String current = displayedFormId(this.formMenuParent);
            if (this.formMenuParent != null && CreationGearSlot.of(this.formMenuParent) != null) {
                current = this.selectedId;
            }
            for (int i = 0; i < choices.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int slotX = this.formMenuX + FORM_PAD + col * FORM_SLOT;
                int slotY = this.formMenuY + FORM_PAD + row * FORM_SLOT;
                boolean hovered = contains(mouseX, mouseY, slotX, slotY, FORM_SLOT, FORM_SLOT);
                if (hovered) {
                    gui.fill(slotX, slotY, slotX + FORM_SLOT, slotY + FORM_SLOT, 0x44FFD27A);
                }
                int iconX = slotX + (FORM_SLOT - FORM_ICON) / 2;
                int iconY = slotY + (FORM_SLOT - FORM_ICON) / 2;
                drawItem(gui, ClientCreationState.stackOf(choices.get(i)), iconX, iconY, FORM_ICON);
                if (choices.get(i).equals(current)) {
                    drawFrame(gui, slotX, slotY, FORM_SLOT, FORM_SLOT, 0xFFFFD27A);
                }
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != 0 && event.button() != 1) {
                return false;
            }
            Minecraft minecraft = Minecraft.getInstance();
            int mouseX = (int) event.x();
            int mouseY = (int) event.y();
            int x = this.getX();
            int y = this.getY();
            boolean shift = event.hasShiftDown();

            String openFormParent = this.formMenuParent;
            if (handleFormMenuClick(mouseX, mouseY)) {
                clickSound();
                return true;
            }

            if (contains(mouseX, mouseY, x + TAB_LOCKED_1_X, y, TAB_W, TAB_H)
                    || contains(mouseX, mouseY, x + TAB_LOCKED_2_X, y, TAB_W, TAB_H)) {
                closeFormMenu();
                minecraft.gui.setOverlayMessage(Component.translatable("gui.yha.creation.tab_locked"), false);
                clickSound();
                return true;
            }
            if (contains(mouseX, mouseY, x + TAB_GEAR_X, y, TAB_W, TAB_H)) {
                closeFormMenu();
                if (!ClientCreationState.get().gearTabUnlocked()) {
                    minecraft.gui.setOverlayMessage(Component.translatable("gui.yha.creation.tab_locked"), false);
                } else {
                    this.page = Page.GEAR;
                }
                clickSound();
                return true;
            }
            if (contains(mouseX, mouseY, x + TAB_BLOCKS_X, y, TAB_W, TAB_H)) {
                closeFormMenu();
                this.page = Page.BLOCKS;
                clickSound();
                return true;
            }

            if (this.page == Page.GEAR && handleEnchantClick(mouseX, mouseY, x, y)) {
                return true;
            }

            if (contains(mouseX, mouseY, x + CREATE_X, y + CREATE_Y, CREATE_W, CREATE_H)) {
                closeFormMenu();
                String createId = displayedFormId(this.selectedId);
                if (createId != null) {
                    ClientPacketDistributor.sendToServer(new CreationCreatePayload(createId, selectedEnchantChoices()));
                    clickSound();
                }
                return true;
            }

            int quickSlots = ClientCreationState.get().unlockedQuickSlots();
            for (int i = 0; i < 3; i++) {
                if (!contains(mouseX, mouseY, x + QUICK_X, y + QUICK_Y[i], QUICK_SIZE, QUICK_SIZE)) {
                    continue;
                }
                closeFormMenu();
                if (i >= quickSlots) {
                    clickSound();
                    return true;
                }
                if (shift && this.selectedId != null) {
                    ClientPacketDistributor.sendToServer(new CreationAssignSlotPayload(i, displayedFormId(this.selectedId)));
                } else {
                    Identifier assigned = ClientCreationState.quickSlot(i);
                    if (assigned != null) {
                        selectAssigned(assigned.toString());
                        if (event.button() == 1) {
                            ClientPacketDistributor.sendToServer(new CreationAssignSlotPayload(i, ""));
                        }
                    }
                }
                clickSound();
                return true;
            }

            if (contains(mouseX, mouseY, x + DOG_X, y + DOG_Y, DOG_W, DOG_H)) {
                closeFormMenu();
                turnPage();
                clickSound();
                return true;
            }

            SlotHit clicked = findClickedSlot(mouseX, mouseY, x, y);
            if (clicked != null) {
                CreationSyncPayload.ClientEntry entry = ClientCreationState.find(clicked.itemId());
                CreationGearSlot gearSlot = this.page == Page.GEAR ? CreationGearSlot.of(clicked.itemId()) : null;
                List<String> gearVariants = gearSlot == null ? List.of() : unlockedGearVariants(gearSlot);
                if (!clicked.itemId().equals(this.selectedId)) {
                    CreationGearSlot previous = CreationGearSlot.of(this.selectedId);
                    if (previous != gearSlot) {
                        this.enchantLevels.clear();
                        this.enchantScroll = 0;
                        this.draggingEnchantId = null;
                    }
                }
                this.selectedId = clicked.itemId();
                if (event.button() == 1 && gearVariants.size() > 1) {
                    if (!clicked.itemId().equals(openFormParent)) {
                        openFormMenu(clicked.itemId(), gearVariants, clicked, "gui.yha.creation.choose_variant");
                    }
                } else if (event.button() == 1 && entry != null && entry.hasForms()) {
                    if (!entry.itemId().equals(openFormParent)) {
                        openFormMenu(entry.itemId(), entry.formChoices(), clicked, "gui.yha.creation.choose_form");
                    }
                } else {
                    closeFormMenu();
                }
                clickSound();
                return true;
            }
            closeFormMenu();
            return false;
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
            if (this.page != Page.GEAR || this.draggingEnchantId == null) {
                return super.mouseDragged(event, dragX, dragY);
            }
            CreationSyncPayload.ClientEnchantEntry entry = ClientCreationState.findEnchant(this.draggingEnchantId);
            if (entry == null) {
                return true;
            }
            setEnchantFromMouse(entry, (int) event.x(), this.getX());
            return true;
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            this.draggingEnchantId = null;
            return super.mouseReleased(event);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (this.page != Page.GEAR) {
                return false;
            }
            int x = this.getX();
            int y = this.getY();
            if (!contains((int) mouseX, (int) mouseY, x + ENCHANT_LIST_X, y + ENCHANT_LIST_Y, enchantListWidth(), enchantListHeight())) {
                return false;
            }
            int maxScroll = enchantMaxScroll(compatibleEnchants().size());
            if (scrollY > 0) {
                this.enchantScroll = Math.max(0, this.enchantScroll - 1);
            } else if (scrollY < 0) {
                this.enchantScroll = Math.min(maxScroll, this.enchantScroll + 1);
            }
            return true;
        }

        private boolean handleFormMenuClick(int mouseX, int mouseY) {
            List<String> choices = this.formMenuChoices;
            if (choices == null || choices.isEmpty()) {
                return false;
            }
            int cols = formMenuCols(choices.size());
            int rows = formMenuRows(choices.size());
            int width = formMenuWidth(cols);
            int height = formMenuHeight(rows);
            if (!contains(mouseX, mouseY, this.formMenuX, this.formMenuY, width, height)) {
                closeFormMenu();
                return false;
            }
            for (int i = 0; i < choices.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int slotX = this.formMenuX + FORM_PAD + col * FORM_SLOT;
                int slotY = this.formMenuY + FORM_PAD + row * FORM_SLOT;
                if (contains(mouseX, mouseY, slotX, slotY, FORM_SLOT, FORM_SLOT)) {
                    String chosen = choices.get(i);
                    CreationGearSlot gearSlot = CreationGearSlot.of(this.formMenuParent);
                    if (gearSlot != null) {
                        this.selectedGearVariants.put(gearSlot, chosen);
                        if (gearSlot != CreationGearSlot.of(this.selectedId)) {
                            this.enchantLevels.clear();
                            this.enchantScroll = 0;
                        }
                        this.selectedId = chosen;
                    } else if (this.formMenuParent != null) {
                        this.selectedForms.put(this.formMenuParent, chosen);
                        this.selectedId = this.formMenuParent;
                    } else {
                        this.selectedId = chosen;
                    }
                    closeFormMenu();
                    return true;
                }
            }
            return true;
        }

        private void openFormMenu(String parentId, List<String> choices, SlotHit hit, String overlayKey) {
            if (parentId != null && parentId.equals(this.formMenuParent)) {
                closeFormMenu();
                return;
            }
            int cols = formMenuCols(choices.size());
            int rows = formMenuRows(choices.size());
            int width = formMenuWidth(cols);
            int height = formMenuHeight(rows);
            int menuX = hit.slotX() + hit.slotSize() + 2;
            int menuY = hit.slotY() - FORM_PAD;
            if (menuX + width > this.getX() + this.getWidth()) {
                menuX = hit.slotX() - width - 2;
            }
            if (menuY < this.getY()) {
                menuY = this.getY();
            }
            if (menuY + height > this.getY() + this.getHeight()) {
                menuY = this.getY() + this.getHeight() - height;
            }
            this.formMenuParent = parentId;
            this.formMenuChoices = List.copyOf(choices);
            this.formMenuX = menuX;
            this.formMenuY = menuY;
            Minecraft.getInstance().gui.setOverlayMessage(Component.translatable(overlayKey), false);
        }

        private void closeFormMenu() {
            this.formMenuParent = null;
            this.formMenuChoices = List.of();
        }

        private void selectAssigned(String assignedId) {
            CreationSyncPayload.ClientEntry parent = ClientCreationState.findParent(assignedId);
            String nextId = parent != null ? parent.itemId() : assignedId;
            CreationGearSlot nextSlot = CreationGearSlot.of(nextId);
            if (nextSlot != CreationGearSlot.of(this.selectedId)) {
                this.enchantLevels.clear();
                this.enchantScroll = 0;
            }
            if (nextSlot != null) {
                this.selectedGearVariants.put(nextSlot, nextId);
            }
            if (parent != null) {
                this.selectedId = parent.itemId();
                this.selectedForms.put(parent.itemId(), assignedId);
            } else {
                this.selectedId = assignedId;
            }
        }

        private String displayedFormId(String parentId) {
            if (parentId == null) {
                return null;
            }
            return this.selectedForms.getOrDefault(parentId, parentId);
        }

        private void turnPage() {
            if (this.page == Page.GEAR) {
                return;
            }
            int materials = ClientCreationState.entriesForTab(CreationTab.MATERIALS, true).size();
            int blocks = ClientCreationState.entriesForTab(CreationTab.BLOCKS, true).size();
            int matPages = Math.max(1, ceilDiv(materials, 18));
            int blockPages = Math.max(1, ceilDiv(blocks, BLOCKS_COLS * (BLOCKS_ROWS - 1) + BLOCKS_LAST_ROW));
            this.materialsPage = (this.materialsPage + 2) % Math.max(2, matPages + (matPages % 2));
            this.blocksPage = (this.blocksPage + 1) % blockPages;
        }

        private SlotHit findClickedSlot(int mouseX, int mouseY, int x, int y) {
            if (this.page == Page.GEAR) {
                return hitGearSlot(mouseX, mouseY, x, y);
            }
            SlotHit materials = hitGrid(mouseX, mouseY, x, y, ClientCreationState.entriesForTab(CreationTab.MATERIALS, true), MAT_X, MAT_Y1, 3, 3, SLOT, this.materialsPage, 3);
            if (materials != null) {
                return materials;
            }
            materials = hitGrid(mouseX, mouseY, x, y, ClientCreationState.entriesForTab(CreationTab.MATERIALS, true), MAT_X, MAT_Y2, 3, 3, SLOT, this.materialsPage + 1, 3);
            if (materials != null) {
                return materials;
            }
            return hitGrid(mouseX, mouseY, x, y, ClientCreationState.entriesForTab(CreationTab.BLOCKS, true), BLOCKS_X, BLOCKS_Y, BLOCKS_COLS, BLOCKS_ROWS, SLOT, this.blocksPage, BLOCKS_LAST_ROW);
        }

        private SlotHit hitGrid(
                int mouseX,
                int mouseY,
                int originX,
                int originY,
                List<CreationSyncPayload.ClientEntry> entries,
                int gridX,
                int gridY,
                int cols,
                int rows,
                int slotSize,
                int page,
                int lastRowCols
        ) {
            int perPage = cols * (rows - 1) + lastRowCols;
            int start = page * perPage;
            int index = 0;
            for (int row = 0; row < rows; row++) {
                int rowCols = row == rows - 1 ? lastRowCols : cols;
                for (int col = 0; col < rowCols; col++) {
                    int entryIndex = start + index;
                    index++;
                    int slotX = originX + gridX + col * slotSize;
                    int slotY = originY + gridY + row * slotSize;
                    if (!contains(mouseX, mouseY, slotX, slotY, slotSize, slotSize)) {
                        continue;
                    }
                    if (entryIndex < 0 || entryIndex >= entries.size()) {
                        return null;
                    }
                    return new SlotHit(entries.get(entryIndex).itemId(), slotX, slotY, slotSize);
                }
            }
            return null;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            narrationElementOutput.add(NarratedElementType.TITLE, this.getMessage());
        }

        private static int formMenuCols(int choiceCount) {
            return Math.max(1, Math.min(FORM_MENU_COLS, choiceCount));
        }

        private static int formMenuRows(int choiceCount) {
            return Math.max(1, ceilDiv(Math.max(1, choiceCount), FORM_MENU_COLS));
        }

        private static int formMenuWidth(int cols) {
            return Math.max(1, cols) * FORM_SLOT + FORM_PAD * 2;
        }

        private static int formMenuHeight(int rows) {
            return Math.max(1, rows) * FORM_SLOT + FORM_PAD * 2;
        }

        private static void drawFrame(GuiGraphicsExtractor gui, int x, int y, int width, int height, int color) {
            gui.fill(x, y, x + width, y + 1, color);
            gui.fill(x, y + height - 1, x + width, y + height, color);
            gui.fill(x, y, x + 1, y + height, color);
            gui.fill(x + width - 1, y, x + width, y + height, color);
        }

        private static int iconSizeForSlot(int slotSize) {
            return slotSize <= GEAR_SLOT ? GEAR_ICON : GRID_ICON;
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

        private static void blit(
                GuiGraphicsExtractor gui,
                Identifier texture,
                int x,
                int y,
                float u,
                float v,
                int width,
                int height,
                int texW,
                int texH
        ) {
            gui.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, texW, texH);
        }

        private static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        private static int ceilDiv(int value, int divisor) {
            if (divisor <= 0) {
                return 1;
            }
            return Math.max(1, (value + divisor - 1) / divisor);
        }

        private static void clickSound() {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }

        private record SlotHit(String itemId, int slotX, int slotY, int slotSize) {
        }

        private void drawGearSlots(GuiGraphicsExtractor gui, int originX, int originY) {
            CreationGearSlot[] slots = CreationGearSlot.values();
            for (int i = 0; i < slots.length; i++) {
                int col = i % GEAR_COLS;
                int row = i / GEAR_COLS;
                int slotX = originX + GEAR_X + col * GEAR_SLOT;
                int slotY = originY + GEAR_Y + row * GEAR_SLOT;
                List<String> variants = unlockedGearVariants(slots[i]);
                if (variants.isEmpty()) {
                    continue;
                }
                String itemId = displayedGearItem(slots[i], variants);
                int iconSize = GEAR_ICON;
                int iconX = slotX + Math.max(0, (GEAR_SLOT - iconSize) / 2);
                int iconY = slotY + Math.max(0, (GEAR_SLOT - iconSize) / 2);
                drawItem(gui, ClientCreationState.stackOf(itemId), iconX, iconY, iconSize);
                if (variants.size() > 1) {
                    gui.fill(slotX + GEAR_SLOT - 4, slotY + 1, slotX + GEAR_SLOT - 1, slotY + 4, 0xFFFFD27A);
                }
                if (itemId.equals(this.selectedId)) {
                    gui.fill(iconX - 1, iconY - 1, iconX + iconSize + 1, iconY, 0xFFFFD27A);
                    gui.fill(iconX - 1, iconY + iconSize, iconX + iconSize + 1, iconY + iconSize + 1, 0xFFFFD27A);
                    gui.fill(iconX - 1, iconY - 1, iconX, iconY + iconSize + 1, 0xFFFFD27A);
                    gui.fill(iconX + iconSize, iconY - 1, iconX + iconSize + 1, iconY + iconSize + 1, 0xFFFFD27A);
                }
            }
        }

        private SlotHit hitGearSlot(int mouseX, int mouseY, int originX, int originY) {
            CreationGearSlot[] slots = CreationGearSlot.values();
            for (int i = 0; i < slots.length; i++) {
                int col = i % GEAR_COLS;
                int row = i / GEAR_COLS;
                int slotX = originX + GEAR_X + col * GEAR_SLOT;
                int slotY = originY + GEAR_Y + row * GEAR_SLOT;
                if (!contains(mouseX, mouseY, slotX, slotY, GEAR_SLOT, GEAR_SLOT)) {
                    continue;
                }
                List<String> variants = unlockedGearVariants(slots[i]);
                if (variants.isEmpty()) {
                    return null;
                }
                return new SlotHit(displayedGearItem(slots[i], variants), slotX, slotY, GEAR_SLOT);
            }
            return null;
        }

        private List<String> unlockedGearVariants(CreationGearSlot slot) {
            List<String> ids = new ArrayList<>();
            for (CreationSyncPayload.ClientEntry entry : ClientCreationState.entriesForTab(CreationTab.GEAR, true)) {
                if (slot.matches(entry.itemId())) {
                    ids.add(entry.itemId());
                }
            }
            ids.sort(CreationGearSlot.variantComparator());
            return ids;
        }

        private String displayedGearItem(CreationGearSlot slot, List<String> variants) {
            String chosen = this.selectedGearVariants.get(slot);
            if (chosen != null && variants.contains(chosen)) {
                return chosen;
            }
            return variants.getLast();
        }

        private List<CreationSyncPayload.ClientEnchantEntry> compatibleEnchants() {
            List<CreationSyncPayload.ClientEnchantEntry> result = new ArrayList<>();
            if (this.selectedId == null) {
                return result;
            }
            ItemStack stack = previewStack(false);
            HolderLookup.Provider access = clientAccess();
            if (stack.isEmpty() || access == null) {
                return result;
            }
            for (CreationSyncPayload.ClientEnchantEntry entry : ClientCreationState.enchants()) {
                try {
                    Identifier enchantId = Identifier.parse(entry.enchantId());
                    if (CreationEnchantments.canEnchant(access, enchantId, stack)) {
                        result.add(entry);
                    }
                } catch (RuntimeException ignored) {
                }
            }
            result.sort((left, right) -> Boolean.compare(right.unlocked(), left.unlocked()));
            return result;
        }

        private CreationGearKind selectedGearKind() {
            if (this.selectedId != null) {
                return CreationGearKind.of(ClientCreationState.stackOf(displayedFormId(this.selectedId)));
            }
            return CreationGearKind.WEAPON;
        }

        private Identifier atlasFor(CreationGearKind kind) {
            return switch (kind) {
                case ARMOR -> TEX_ENCHANT_ARMOR;
                case TOOL -> TEX_ENCHANT_TOOLS;
                case WEAPON -> TEX_ENCHANT_SWORD;
                case UTILITY -> TEX_ENCHANT_UTILITY;
            };
        }

        private int atlasHeight(CreationGearKind kind) {
            return switch (kind) {
                case ARMOR -> 129;
                case TOOL -> 87;
                case WEAPON -> 108;
                case UTILITY -> 45;
            };
        }

        private int enchantIndex(int row, int col) {
            return (this.enchantScroll + row) * ENCHANT_COLS + col;
        }

        private int enchantColumnOf(CreationSyncPayload.ClientEnchantEntry entry) {
            List<CreationSyncPayload.ClientEnchantEntry> enchants = compatibleEnchants();
            for (int i = 0; i < enchants.size(); i++) {
                if (enchants.get(i).enchantId().equals(entry.enchantId())) {
                    return i % ENCHANT_COLS;
                }
            }
            return 0;
        }

        private static int enchantMaxScroll(int total) {
            return Math.max(0, ceilDiv(total, ENCHANT_COLS) - ENCHANT_VISIBLE_ROWS);
        }

        private static int enchantListWidth() {
            return ENCHANT_COLS * ENCHANT_ROW_W + (ENCHANT_COLS - 1) * ENCHANT_COL_GAP;
        }

        private static int enchantListHeight() {
            return ENCHANT_VISIBLE_ROWS * ENCHANT_ROW_STRIDE;
        }

        private static int enchantCellX(int originX, int col) {
            return originX + ENCHANT_LIST_X + col * (ENCHANT_ROW_W + ENCHANT_COL_GAP);
        }

        private static int enchantCellY(int originY, int row) {
            return originY + ENCHANT_LIST_Y + row * ENCHANT_ROW_STRIDE;
        }

        private void drawEnchantScrollbar(GuiGraphicsExtractor gui, int x, int y, int total, int maxScroll) {
            if (maxScroll <= 0) {
                return;
            }
            int barX = enchantCellX(x, ENCHANT_COLS - 1) + ENCHANT_ROW_W - ENCHANT_SCROLLBAR_W;
            int barY = y + ENCHANT_LIST_Y;
            int barH = enchantListHeight();
            int visibleCells = ENCHANT_VISIBLE_ROWS * ENCHANT_COLS;
            gui.fill(barX, barY, barX + ENCHANT_SCROLLBAR_W, barY + barH, ENCHANT_SCROLLBAR_TRACK);
            int thumbHeight = Math.max(10, (barH * visibleCells) / Math.max(1, total));
            int track = Math.max(1, barH - thumbHeight);
            int thumbY = barY + (track * this.enchantScroll / maxScroll);
            gui.fill(barX, thumbY, barX + ENCHANT_SCROLLBAR_W, thumbY + thumbHeight, ENCHANT_SCROLLBAR_THUMB);
        }

        private boolean handleEnchantClick(int mouseX, int mouseY, int x, int y) {
            CreationSyncPayload.ClientEnchantEntry entry = hitEnchantRow(mouseX, mouseY, x, y);
            if (entry == null || !entry.unlocked()) {
                this.draggingEnchantId = null;
                return entry != null;
            }
            setEnchantFromMouse(entry, mouseX, x);
            this.draggingEnchantId = entry.enchantId();
            clickSound();
            return true;
        }

        private CreationSyncPayload.ClientEnchantEntry hitEnchantRow(int mouseX, int mouseY, int x, int y) {
            List<CreationSyncPayload.ClientEnchantEntry> enchants = compatibleEnchants();
            for (int row = 0; row < ENCHANT_VISIBLE_ROWS; row++) {
                for (int col = 0; col < ENCHANT_COLS; col++) {
                    int index = enchantIndex(row, col);
                    if (index >= enchants.size()) {
                        return null;
                    }
                    int cellX = enchantCellX(x, col);
                    int cellY = enchantCellY(y, row);
                    if (contains(mouseX, mouseY, cellX, cellY, ENCHANT_ROW_W, ENCHANT_ROW_H)) {
                        return enchants.get(index);
                    }
                }
            }
            return null;
        }

        private void drawEnchantPanel(GuiGraphicsExtractor gui, Minecraft minecraft, int x, int y, int mouseX, int mouseY) {
            if (this.selectedId == null) {
                return;
            }
            CreationGearKind kind = selectedGearKind();
            Identifier atlas = atlasFor(kind);
            int texH = atlasHeight(kind);
            int previewX = x + ENCHANT_PANEL_X;
            int previewY = y + ENCHANT_PANEL_Y;
            blit(gui, atlas, previewX, previewY, 0, 0, ENCHANT_SLOT_SIZE, ENCHANT_SLOT_SIZE, ENCHANT_ATLAS_W, texH);
            ItemStack preview = previewStack(true);
            drawItem(gui, preview, previewX + 4, previewY + 4, 16);
            boolean hoveringPreview = contains(mouseX, mouseY, previewX, previewY, ENCHANT_SLOT_SIZE, ENCHANT_SLOT_SIZE);
            if (hoveringPreview && !preview.isEmpty()) {
                gui.setTooltipForNextFrame(minecraft.font, preview, mouseX, mouseY);
            }

            List<CreationSyncPayload.ClientEnchantEntry> enchants = compatibleEnchants();
            int maxScroll = enchantMaxScroll(enchants.size());
            this.enchantScroll = Mth.clamp(this.enchantScroll, 0, maxScroll);
            for (int row = 0; row < ENCHANT_VISIBLE_ROWS; row++) {
                for (int col = 0; col < ENCHANT_COLS; col++) {
                    int index = enchantIndex(row, col);
                    if (index >= enchants.size()) {
                        break;
                    }
                    CreationSyncPayload.ClientEnchantEntry entry = enchants.get(index);
                    int cellX = enchantCellX(x, col);
                    int cellY = enchantCellY(y, row);
                    if (entry.unlocked()) {
                        int level = this.enchantLevels.getOrDefault(entry.enchantId(), 0);
                        String name = trim(minecraft, enchantName(entry), ENCHANT_ROW_W - 8);
                        gui.text(minecraft.font, name, cellX + 4, cellY + 2, 0xFF3A2A18, false);
                        drawEnchantSlider(gui, cellX, cellY, entry.maxLevel(), level);
                        if (!hoveringPreview && contains(mouseX, mouseY, cellX, cellY, ENCHANT_ROW_W, ENCHANT_ROW_H)) {
                            gui.setTooltipForNextFrame(minecraft.font, enchantTooltip(entry, level), mouseX, mouseY);
                        }
                    } else {
                        blit(gui, atlas, cellX, cellY, ENCHANT_ROW_U, ENCHANT_ROW_V, ENCHANT_ROW_W, ENCHANT_ROW_H, ENCHANT_ATLAS_W, texH);
                    }
                }
            }
            drawEnchantScrollbar(gui, x, y, enchants.size(), maxScroll);
        }

        private void drawEnchantSlider(GuiGraphicsExtractor gui, int rowX, int rowY, int maxLevel, int level) {
            int trackX = rowX + SLIDER_PAD_X;
            int trackY = rowY + SLIDER_Y;
            int trackW = ENCHANT_ROW_W - SLIDER_PAD_X * 2;
            gui.fill(trackX, trackY + 2, trackX + trackW, trackY + 4, 0xFF8A6A48);
            float ratio = maxLevel <= 0 ? 0.0f : level / (float) maxLevel;
            int knobX = trackX + Math.round(ratio * (trackW - 4));
            gui.fill(knobX, trackY, knobX + 4, trackY + SLIDER_H, 0xFFFFD27A);
            if (level > 0) {
                gui.fill(trackX, trackY + 2, knobX + 2, trackY + 4, 0xFFFFD27A);
            }
        }

        private void setEnchantFromMouse(CreationSyncPayload.ClientEnchantEntry entry, int mouseX, int originX) {
            int col = enchantColumnOf(entry);
            int trackX = enchantCellX(originX, col) + SLIDER_PAD_X;
            int trackW = ENCHANT_ROW_W - SLIDER_PAD_X * 2;
            float ratio = trackW <= 0 ? 0.0f : (mouseX - trackX) / (float) trackW;
            int level = Mth.clamp(Math.round(ratio * entry.maxLevel()), 0, entry.maxLevel());
            setEnchantLevel(entry.enchantId(), level);
        }

        private void setEnchantLevel(String enchantId, int level) {
            if (level <= 0) {
                this.enchantLevels.remove(enchantId);
                return;
            }
            HolderLookup.Provider access = clientAccess();
            Identifier candidate;
            try {
                candidate = Identifier.parse(enchantId);
            } catch (RuntimeException ignored) {
                return;
            }
            if (access != null) {
                List<String> toClear = new ArrayList<>();
                for (Map.Entry<String, Integer> current : this.enchantLevels.entrySet()) {
                    if (current.getValue() == null || current.getValue() <= 0 || current.getKey().equals(enchantId)) {
                        continue;
                    }
                    try {
                        if (!CreationEnchantments.compatible(access, candidate, Identifier.parse(current.getKey()))) {
                            toClear.add(current.getKey());
                        }
                    } catch (RuntimeException ignored) {
                    }
                }
                for (String id : toClear) {
                    this.enchantLevels.remove(id);
                }
            }
            this.enchantLevels.put(enchantId, level);
        }

        private int selectedEnchantCost() {
            int total = 0;
            for (Map.Entry<String, Integer> entry : this.enchantLevels.entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0) {
                    continue;
                }
                CreationSyncPayload.ClientEnchantEntry catalog = ClientCreationState.findEnchant(entry.getKey());
                if (catalog != null) {
                    total += catalog.costForLevel(entry.getValue());
                }
            }
            return total;
        }

        private List<CreationCreatePayload.EnchantChoice> selectedEnchantChoices() {
            if (this.page != Page.GEAR) {
                return List.of();
            }
            List<CreationCreatePayload.EnchantChoice> choices = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : this.enchantLevels.entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0) {
                    continue;
                }
                choices.add(new CreationCreatePayload.EnchantChoice(entry.getKey(), entry.getValue()));
            }
            return choices;
        }

        private ItemStack previewStack(boolean applyEnchants) {
            String formId = displayedFormId(this.selectedId);
            ItemStack stack = ClientCreationState.stackOf(formId);
            if (!applyEnchants || stack.isEmpty()) {
                return stack;
            }
            stack = stack.copy();
            HolderLookup.Provider access = clientAccess();
            Map<Identifier, Integer> levels = new HashMap<>();
            for (Map.Entry<String, Integer> entry : this.enchantLevels.entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0) {
                    continue;
                }
                try {
                    levels.put(Identifier.parse(entry.getKey()), entry.getValue());
                } catch (RuntimeException ignored) {
                }
            }
            CreationEnchantments.apply(stack, access, levels);
            return stack;
        }

        private static String enchantName(CreationSyncPayload.ClientEnchantEntry entry) {
            try {
                return CreationEnchantments.displayName(clientAccess(), Identifier.parse(entry.enchantId())).getString();
            } catch (RuntimeException ignored) {
                return entry.enchantId();
            }
        }

        private static Component enchantTooltip(CreationSyncPayload.ClientEnchantEntry entry, int level) {
            String name = enchantName(entry);
            String levelText = level <= 0 ? "0" : String.valueOf(level);
            return Component.literal(name + " " + levelText + " - " + Component.translatable("gui.yha.creation.enchant_cost", entry.costForLevel(level)).getString());
        }

        private static HolderLookup.Provider clientAccess() {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.level == null ? null : minecraft.level.registryAccess();
        }

        private static String trim(Minecraft minecraft, String text, int maxWidth) {
            if (minecraft.font.width(text) <= maxWidth) {
                return text;
            }
            String ellipsis = "..";
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
    }

    public static class Serializer extends UiWidgetSerializer<CreationNotebookUiComponent> {
        @Override
        public MapCodec<CreationNotebookUiComponent> codec() {
            return CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiWidget, CreationNotebookUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("Creation Notebook")
                    .setDescription("Notebook GUI for creating researched items with lipids.");
        }
    }
}
