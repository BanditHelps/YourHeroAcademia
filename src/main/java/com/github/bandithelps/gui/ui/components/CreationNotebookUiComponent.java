package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.client.body.ClientBodyState;
import com.github.bandithelps.client.creation.ClientCreationState;
import com.github.bandithelps.creation.CreationEnchantments;
import com.github.bandithelps.creation.CreationGearKind;
import com.github.bandithelps.creation.CreationGearSlot;
import com.github.bandithelps.creation.CreationPotionForm;
import com.github.bandithelps.creation.CreationPotions;
import com.github.bandithelps.creation.CreationQuickSlot;
import com.github.bandithelps.creation.CreationTab;
import com.github.bandithelps.creation.CreationUtil;
import com.github.bandithelps.network.CreationAssignSlotPayload;
import com.github.bandithelps.network.CreationCreatePayload;
import com.github.bandithelps.network.CreationCreatePotionPayload;
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
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;
import net.threetag.palladium.client.gui.ui.screen.UiScreen;
import net.threetag.palladium.client.gui.ui.widget.UiWidget;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetProperties;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetSerializer;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;

public class CreationNotebookUiComponent extends UiWidget {
    private static final Identifier TEX_BLOCKS = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_gui.png");
    private static final Identifier TEX_GEAR = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_tool_gui.png");
    private static final Identifier TEX_ALCHEMY = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_alchemy_gui.png");
    private static final Identifier TEX_DARKENED = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_gui_darkened.png");
    private static final Identifier TEX_CREATE = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_gui_create_button.png");
    private static final Identifier TEX_CREATE_HOVER = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_gui_create_button_hover.png");
    private static final Identifier TEX_LIPID = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/lipid_icon.png");
    private static final Identifier TEX_LOCKED_TAB = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_gui_green_question_tab.png");
    private static final Identifier TEX_LOCK = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/icons/blackwhip/lock.png");
    private static final Identifier TEX_ENCHANT_ARMOR = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_gui_armor_enchant.png");
    private static final Identifier TEX_ENCHANT_SWORD = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_gui_sword_enchant.png");
    private static final Identifier TEX_ENCHANT_TOOLS = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_gui_tools_enchant.png");
    private static final Identifier TEX_ENCHANT_UTILITY = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/creation_gui_utility_enchant.png");
    private static final Identifier TEX_FORM_MENU = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "creation/form_menu");
    private static final Identifier TEX_SUBMENU_BADGE = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "textures/gui/creation/submenu_badge.png");

    private static final int TEX_W = 240;
    private static final int TEX_H = 193;
    private static final int SUBMENU_BADGE = 5;
    private static final int GRID_SLOT = 18;
    private static final int GRID_ICON = 16;
    private static final int GEAR_SLOT = 12;
    private static final int GEAR_ICON = 12;
    private static final int QUICK_ICON = 16;
    private static final int MAT_X = 15;
    private static final int MAT_Y = 39;
    private static final int MAT_COLS = 3;
    private static final int MAT_ROWS = 8;
    private static final int MAT_PER_PAGE = MAT_COLS * MAT_ROWS;
    private static final int BLOCKS_X = 77;
    private static final int BLOCKS_Y = 39;
    private static final int BLOCKS_COLS = 5;
    private static final int BLOCKS_ROWS = 8;
    private static final int GEAR_X = 15;
    private static final int GEAR_Y = 39;
    private static final int GEAR_COLS = 4;
    private static final int GEAR_ROWS = 3;
    private static final int QUICK_X = 175;
    private static final int QUICK_Y = 61;
    private static final int QUICK_SIZE = 18;
    private static final int QUICK_COLS = 3;
    private static final int QUICK_ROWS = 2;
    private static final int QUICK_COUNT = QUICK_COLS * QUICK_ROWS;
    private static final int CREATE_X = 171;
    private static final int CREATE_Y = 118;
    private static final int CREATE_W = 59;
    private static final int CREATE_H = 17;
    private static final int LIPID_ICON_W = 9;
    private static final int LIPID_ICON_H = 11;
    private static final int DOG_X = 218;
    private static final int DOG_Y = 178;
    private static final int DOG_W = 20;
    private static final int DOG_H = 14;
    private static final int FORM_SLOT = 18;
    private static final int FORM_PAD = 3;
    private static final int FORM_ICON = 16;
    private static final int FORM_MENU_COLS = 6;
    private static final int TAB_W = 16;
    private static final int TAB_H = 16;
    private static final int TAB_LOCKED_1_X = 142;
    private static final int TAB_ALCHEMY_X = 162;
    private static final int TAB_GEAR_X = 182;
    private static final int TAB_BLOCKS_X = 202;
    private static final int TIME_X = 15;
    private static final int TIME_Y = 39;
    private static final int TIME_W = 60;
    private static final int TIME_H = 17;
    private static final int ALCHEMY_GRID_X = 18;
    private static final int ALCHEMY_GRID_Y = 59;
    private static final int ALCHEMY_COLS = 3;
    private static final int ALCHEMY_ROWS = 6;
    private static final int DIAMOND_SIZE = 17;
    private static final int DIAMOND_TOP_X = 112;
    private static final int DIAMOND_TOP_Y = 59;
    private static final int DIAMOND_LEFT_X = 83;
    private static final int DIAMOND_LEFT_Y = 95;
    private static final int DIAMOND_RIGHT_X = 141;
    private static final int DIAMOND_RIGHT_Y = 95;
    private static final int DIAMOND_BOTTOM_X = 112;
    private static final int DIAMOND_BOTTOM_Y = 113;
    private static final int AMP_BOX_X = 78;
    private static final int AMP_BOX_Y = 148;
    private static final int AMP_BOX_W = 94;
    private static final int AMP_BOX_H = 30;
    private static final int AMP_TRACK_W = 72;
    private static final int LOCK_SIZE = 16;
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
    private static final int NAME_X = 92;
    private static final int NAME_Y = 43;
    private static final int NAME_W = 70;
    private static final int NAME_H = 16;
    private static final int NAME_PAD = 4;
    private static final int LIPID_TEXT_X = 192;
    private static final int LIPID_TEXT_Y = 44;
    private static final int LIPID_POP_MS = 800;



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
        private enum Page { BLOCKS, GEAR, ALCHEMY }

        private Page page = Page.BLOCKS;
        private String selectedId;
        private int materialsPage;
        private int blocksPage;
        private final Map<String, Integer> enchantLevels = new HashMap<>();
        private int enchantScroll;
        private String draggingEnchantId;
        private final Map<String, String> selectedForms = new HashMap<>();
        private final Map<String, String> selectedGroupItems = new HashMap<>();
        private final Map<CreationGearSlot, String> selectedGearVariants = new HashMap<>();
        private String formMenuParent;
        private List<String> formMenuChoices = List.of();
        private int formMenuX;
        private int formMenuY;
        private boolean potionFormMenu;
        private boolean itemGroupFormMenu;
        private String selectedEffectId;
        private CreationPotionForm selectedPotionForm = CreationPotionForm.DRINKABLE;
        private int potionDurationSeconds = 15;
        private String timeInput = "";
        private boolean timeReplace;
        private int potionAmplifier;
        private boolean timeFocused;
        private boolean draggingAmplifier;
        private String itemName = "";
        private boolean nameFocused;
        private int lastDisplayedLipids = -1;
        private final List<LipidSpendPopup> lipidPops = new ArrayList<>();

        private NotebookWidget(int x, int y, int width, int height) {
            super(x, y, width, height, Component.literal("Creation"));
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            int x = this.getX();
            int y = this.getY();
            Identifier background = this.page == Page.GEAR ? TEX_GEAR : (this.page == Page.ALCHEMY ? TEX_ALCHEMY : TEX_BLOCKS);
            blit(gui, background, x, y, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);

            if (!ClientCreationState.get().gearTabUnlocked()) {
                blit(gui, TEX_LOCKED_TAB, x + TAB_GEAR_X + 1, y + 1, 0, 0, 16, 16, 16, 16);
            }
            if (!ClientCreationState.get().alchemyTabUnlocked()) {
                blit(gui, TEX_LOCKED_TAB, x + TAB_ALCHEMY_X + 1, y + 1, 0, 0, 16, 16, 16, 16);
            }

            int quickSlots = ClientCreationState.get().unlockedQuickSlots();
            boolean overMenu = formMenuContains(mouseX, mouseY);
            if (this.page != Page.BLOCKS) {
                blit(gui, TEX_BLOCKS, x + QUICK_X, y + QUICK_Y, QUICK_X, QUICK_Y, QUICK_COLS * QUICK_SIZE, QUICK_COUNT * QUICK_SIZE, TEX_W, TEX_H);
            }

            int lipids = Mth.floor(ClientBodyState.getCustomFloat(BodyPart.CHEST, CreationUtil.LIPIDS_KEY, 0.0f));
            if (this.lastDisplayedLipids >= 0 && lipids < this.lastDisplayedLipids) {
                this.lipidPops.add(new LipidSpendPopup(this.lastDisplayedLipids - lipids));
            }
            this.lastDisplayedLipids = lipids;
            gui.text(minecraft.font, String.valueOf(lipids), x + LIPID_TEXT_X, y + LIPID_TEXT_Y, 0xFF3A2A18, false);
            drawLipidSpendPops(gui, minecraft, x, y, lipids);

            if (this.page == Page.BLOCKS) {
                drawGrid(gui, x, y, mouseX, mouseY, ClientCreationState.unlockedItemGroups(CreationTab.MATERIALS), MAT_X, MAT_Y, MAT_COLS, MAT_ROWS, GRID_SLOT, this.materialsPage);
                drawGrid(gui, x, y, mouseX, mouseY, ClientCreationState.unlockedItemGroups(CreationTab.BLOCKS), BLOCKS_X, BLOCKS_Y, BLOCKS_COLS, BLOCKS_ROWS, GRID_SLOT, this.blocksPage);
            } else if (this.page == Page.GEAR) {
                drawGearSlots(gui, x, y);
                drawEnchantPanel(gui, minecraft, x, y, mouseX, mouseY);
                drawNameField(gui, minecraft, x, y);
            } else {
                drawAlchemyPage(gui, minecraft, x, y, mouseX, mouseY);
            }

            for (int i = 0; i < QUICK_COUNT; i++) {
                int slotX = quickSlotX(x, i);
                int slotY = quickSlotY(y, i);
                int iconX = slotX + Math.max(0, (QUICK_SIZE - QUICK_ICON) / 2);
                int iconY = slotY + Math.max(0, (QUICK_SIZE - QUICK_ICON) / 2);
                if (i >= quickSlots) {
                    blitLock(gui, iconX, iconY);
                    if (!overMenu && contains(mouseX, mouseY, slotX, slotY, QUICK_SIZE, QUICK_SIZE)) {
                        gui.setTooltipForNextFrame(minecraft.font, Component.translatable("gui.yha.creation.tab_locked"), mouseX, mouseY);
                    }
                    continue;
                }
                ItemStack assigned = ClientCreationState.quickSlotStack(i, clientAccess());
                if (!assigned.isEmpty()) {
                    drawItem(gui, assigned, iconX, iconY, QUICK_ICON);
                    if (!overMenu && contains(mouseX, mouseY, slotX, slotY, QUICK_SIZE, QUICK_SIZE)) {
                        gui.setTooltipForNextFrame(minecraft.font, assigned, mouseX, mouseY);
                    }
                }
            }

            String createId = displayedFormId(this.selectedId);
            CreationSyncPayload.ClientEntry selected = ClientCreationState.findParent(createId != null ? createId : this.selectedId);
            CreationSyncPayload.ClientPotionEntry selectedPotion = this.page == Page.ALCHEMY ? ClientCreationState.findPotion(this.selectedEffectId) : null;
            int selectedCost;
            boolean canCreate;
            if (this.page == Page.ALCHEMY) {
                selectedCost = selectedPotion == null ? 0 : selectedPotion.costFor(this.potionAmplifier, potionDurationForCost(selectedPotion), this.selectedPotionForm.factor());
                canCreate = selectedPotion != null && selectedPotion.unlocked() && this.selectedPotionForm != null && lipids >= selectedCost;
            } else {
                selectedCost = selected == null ? 0 : selected.formCost(createId) + selectedEnchantCost();
                canCreate = selected != null && selected.unlocked() && lipids >= selectedCost;
            }
            boolean createHovered = contains(mouseX, mouseY, x + CREATE_X, y + CREATE_Y, CREATE_W, CREATE_H);
            blit(gui, createHovered && canCreate ? TEX_CREATE_HOVER : TEX_CREATE, x + CREATE_X, y + CREATE_Y, 0, 0, CREATE_W, CREATE_H, CREATE_W, CREATE_H);
            if (!canCreate) {
                gui.fill(x + CREATE_X, y + CREATE_Y, x + CREATE_X + CREATE_W, y + CREATE_Y + CREATE_H, 0x66000000);
            }

            boolean showCost = this.page == Page.ALCHEMY ? selectedPotion != null : selected != null;
            if (showCost) {
                drawCreateCost(gui, minecraft, x, y, selectedCost, canCreate);
            }

            drawFormMenu(gui, mouseX, mouseY);
        }

        private void drawGrid(
                GuiGraphicsExtractor gui,
                int originX,
                int originY,
                int mouseX,
                int mouseY,
                List<ClientCreationState.ItemGroupView> groups,
                int gridX,
                int gridY,
                int cols,
                int rows,
                int slotSize,
                int page
        ) {
            drawGrid(gui, originX, originY, mouseX, mouseY, groups, gridX, gridY, cols, rows, slotSize, page, cols);
        }

        private void drawGrid(
                GuiGraphicsExtractor gui,
                int originX,
                int originY,
                int mouseX,
                int mouseY,
                List<ClientCreationState.ItemGroupView> groups,
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
            boolean overMenu = formMenuContains(mouseX, mouseY);
            for (int row = 0; row < rows; row++) {
                int rowCols = row == rows - 1 ? lastRowCols : cols;
                for (int col = 0; col < rowCols; col++) {
                    int entryIndex = start + index;
                    index++;
                    if (entryIndex < 0 || entryIndex >= groups.size()) {
                        continue;
                    }
                    ClientCreationState.ItemGroupView group = groups.get(entryIndex);
                    int slotX = originX + gridX + col * slotSize;
                    int slotY = originY + gridY + row * slotSize;
                    int iconSize = iconSizeForSlot(slotSize);
                    int iconX = slotX + Math.max(0, (slotSize - iconSize) / 2);
                    int iconY = slotY + Math.max(0, (slotSize - iconSize) / 2);
                    ItemStack stack = itemGroupIcon(group);
                    drawItem(gui, stack, iconX, iconY, iconSize);
                    CreationSyncPayload.ClientEntry first = ClientCreationState.find(group.firstItemId());
                    if (!group.isSingleton() || (first != null && first.hasForms())) {
                        blitSubmenuBadge(gui, slotX, slotY, slotSize);
                    }
                    if (group.contains(this.selectedId) || group.contains(displayedFormId(this.selectedId))) {
                        gui.fill(iconX - 1, iconY - 1, iconX + iconSize + 1, iconY, 0xFFFFD27A);
                        gui.fill(iconX - 1, iconY + iconSize, iconX + iconSize + 1, iconY + iconSize + 1, 0xFFFFD27A);
                        gui.fill(iconX - 1, iconY - 1, iconX, iconY + iconSize + 1, 0xFFFFD27A);
                        gui.fill(iconX + iconSize, iconY - 1, iconX + iconSize + 1, iconY + iconSize + 1, 0xFFFFD27A);
                    }
                    if (!overMenu && contains(mouseX, mouseY, slotX, slotY, slotSize, slotSize)) {
                        gui.setTooltipForNextFrame(Minecraft.getInstance().font, itemGroupTooltip(group), mouseX, mouseY);
                    }
                }
            }
        }

        private void drawFormMenu(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
            List<String> choices = this.formMenuChoices;
            if (choices == null || choices.isEmpty()) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            int cols = formMenuCols(choices.size());
            int rows = formMenuRows(choices.size());
            int width = formMenuWidth(cols);
            int height = formMenuHeight(rows);
            //gui.fill(this.formMenuX, this.formMenuY, this.formMenuX + width, this.formMenuY + height, 0xF21A1410);
            //drawFrame(gui, this.formMenuX, this.formMenuY, width, height, 0xFFFFD27A);
            blit(gui, TEX_DARKENED, this.getX(), this.getY(), 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
            gui.blitSprite(RenderPipelines.GUI_TEXTURED, TEX_FORM_MENU, this.formMenuX, this.formMenuY, width, height);
            String current = displayedFormId(this.formMenuParent);
            if (this.formMenuParent != null && CreationGearSlot.of(this.formMenuParent) != null) {
                current = this.selectedId;
            }
            if (this.potionFormMenu) {
                current = this.selectedEffectId;
            }
            if (this.itemGroupFormMenu) {
                ClientCreationState.ItemGroupView group = findItemGroup(this.formMenuParent);
                current = group != null ? displayedGroupItem(group) : this.selectedId;
            }
            Component hoveredName = null;
            ItemStack hoveredStack = ItemStack.EMPTY;
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
                ItemStack icon = this.potionFormMenu
                        ? alchemyPreviewStack(choices.get(i), CreationPotionForm.DRINKABLE)
                        : ClientCreationState.stackOf(choices.get(i));
                drawItem(gui, icon, iconX, iconY, FORM_ICON);
                if (choices.get(i).equals(current)) {
                    drawFrame(gui, slotX, slotY, FORM_SLOT, FORM_SLOT, 0xFFFFD27A);
                }
                if (hovered) {
                    hoveredStack = icon;
                    hoveredName = this.potionFormMenu
                            ? CreationPotions.itemName(safeId(choices.get(i)), CreationPotionForm.DRINKABLE)
                            : (icon.isEmpty() ? Component.literal(choices.get(i)) : icon.getHoverName());
                }
            }
            if (formMenuContains(mouseX, mouseY) && hoveredName != null) {
                if (!hoveredStack.isEmpty()) {
                    gui.setTooltipForNextFrame(minecraft.font, hoveredStack, mouseX, mouseY);
                } else {
                    gui.setTooltipForNextFrame(minecraft.font, hoveredName, mouseX, mouseY);
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

            String openFormParent = this.formMenuParent;
            if (handleFormMenuClick(mouseX, mouseY)) {
                clickSound();
                return true;
            }

            if (contains(mouseX, mouseY, x + TAB_LOCKED_1_X, y, TAB_W, TAB_H)) {
                closeFormMenu();
                unfocusTime();
                this.nameFocused = false;
                minecraft.gui.setOverlayMessage(Component.translatable("gui.yha.creation.tab_locked"), false);
                clickSound();
                return true;
            }
            if (contains(mouseX, mouseY, x + TAB_ALCHEMY_X, y, TAB_W, TAB_H)) {
                closeFormMenu();
                unfocusTime();
                this.nameFocused = false;
                if (!ClientCreationState.get().alchemyTabUnlocked()) {
                    minecraft.gui.setOverlayMessage(Component.translatable("gui.yha.creation.tab_locked"), false);
                } else {
                    this.page = Page.ALCHEMY;
                }
                clickSound();
                return true;
            }
            if (contains(mouseX, mouseY, x + TAB_GEAR_X, y, TAB_W, TAB_H)) {
                closeFormMenu();
                unfocusTime();
                this.nameFocused = false;
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
                unfocusTime();
                this.nameFocused = false;
                this.page = Page.BLOCKS;
                clickSound();
                return true;
            }

            if (this.page == Page.GEAR && hitNameField(mouseX, mouseY, x, y)) {
                closeFormMenu();
                unfocusTime();
                this.nameFocused = true;
                this.setFocused(true);
                clickSound();
                return true;
            }
            this.nameFocused = false;

            if (this.page == Page.GEAR && handleEnchantClick(mouseX, mouseY, x, y)) {
                return true;
            }
            if (this.page == Page.ALCHEMY && handleAlchemyClick(mouseX, mouseY, x, y, event)) {
                return true;
            }

            if (contains(mouseX, mouseY, x + CREATE_X, y + CREATE_Y, CREATE_W, CREATE_H)) {
                closeFormMenu();
                unfocusTime();
                this.nameFocused = false;
                if (this.page == Page.ALCHEMY) {
                    if (this.selectedEffectId != null) {
                        CreationSyncPayload.ClientPotionEntry potion = ClientCreationState.findPotion(this.selectedEffectId);
                        int ticks = potion != null && potion.instant() ? 1 : this.potionDurationSeconds * 20;
                        ClientPacketDistributor.sendToServer(new CreationCreatePotionPayload(
                                this.selectedEffectId,
                                this.selectedPotionForm.id(),
                                ticks,
                                this.potionAmplifier
                        ));
                        clickSound();
                    }
                } else {
                    String createId = displayedFormId(this.selectedId);
                    if (createId != null) {
                        String name = this.page == Page.GEAR ? this.itemName : "";
                        ClientPacketDistributor.sendToServer(new CreationCreatePayload(createId, selectedEnchantChoices(), name));
                        clickSound();
                    }
                }
                return true;
            }

            int quickSlots = ClientCreationState.get().unlockedQuickSlots();
            for (int i = 0; i < QUICK_COUNT; i++) {
                if (!contains(mouseX, mouseY, quickSlotX(x, i), quickSlotY(y, i), QUICK_SIZE, QUICK_SIZE)) {
                    continue;
                }
                closeFormMenu();
                unfocusTime();
                this.nameFocused = false;
                if (i >= quickSlots) {
                    minecraft.gui.setOverlayMessage(Component.translatable("gui.yha.creation.tab_locked"), false);
                    clickSound();
                    return true;
                }
                if (event.button() == 1) {
                    ClientPacketDistributor.sendToServer(new CreationAssignSlotPayload(i, ""));
                    clickSound();
                    return true;
                }
                CreationQuickSlot selection = currentQuickSlotSelection();
                if (selection != null) {
                    ClientPacketDistributor.sendToServer(new CreationAssignSlotPayload(i, selection.encode()));
                    minecraft.gui.setOverlayMessage(Component.translatable("gui.yha.creation.quick_slot_assigned"), false);
                } else {
                    CreationQuickSlot assigned = ClientCreationState.quickSlot(i);
                    if (assigned != null) {
                        selectAssigned(assigned);
                    }
                }
                clickSound();
                return true;
            }

            if (contains(mouseX, mouseY, x + DOG_X, y + DOG_Y, DOG_W, DOG_H)) {
                closeFormMenu();
                unfocusTime();
                this.nameFocused = false;
                turnPage();
                clickSound();
                return true;
            }

            SlotHit clicked = findClickedSlot(mouseX, mouseY, x, y);
            if (clicked != null) {
                ClientCreationState.ItemGroupView group = findItemGroup(clicked.itemId());
                if (group != null && !group.isSingleton()) {
                    String displayed = displayedGroupItem(group);
                    if (displayed != null) {
                        setSelectedItem(displayed);
                    }
                    //right click only
                    if (event.button() == 1) {
                        openItemGroupMenu(group.groupId(), group.itemIds(), clicked);
                    } else {
                        closeFormMenu();
                    }
                    clickSound();
                    return true;
                }
                String clickedId = group != null && group.firstItemId() != null ? group.firstItemId() : clicked.itemId();
                CreationSyncPayload.ClientEntry entry = ClientCreationState.findParent(clickedId);
                CreationGearSlot gearSlot = this.page == Page.GEAR ? CreationGearSlot.of(clickedId) : null;
                List<String> gearVariants = gearSlot == null ? List.of() : unlockedGearVariants(gearSlot);
                setSelectedItem(clickedId);
                if (event.button() == 1 && gearVariants.size() > 1) {
                    if (!clickedId.equals(openFormParent)) {
                        openFormMenu(clickedId, gearVariants, clicked, "gui.yha.creation.choose_variant");
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
            if (this.page == Page.ALCHEMY && this.draggingAmplifier) {
                setAmplifierFromMouse((int) event.x(), this.getX());
                return true;
            }
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
            this.draggingAmplifier = false;
            return super.mouseReleased(event);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (this.page == Page.ALCHEMY) {
                return handleAlchemyScroll((int) mouseX, (int) mouseY, scrollY);
            }
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

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (this.page == Page.GEAR && this.nameFocused) {
                int key = event.key();
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    this.nameFocused = false;
                    this.setFocused(false);
                    return true;
                }
                if (key == GLFW.GLFW_KEY_BACKSPACE) {
                    if (!this.itemName.isEmpty()) {
                        this.itemName = this.itemName.substring(0, this.itemName.length() - 1);
                    }
                    return true;
                }
                if (key == GLFW.GLFW_KEY_DELETE) {
                    this.itemName = "";
                    return true;
                }
                return true;
            }
            if (this.page != Page.ALCHEMY || !this.timeFocused) {
                return false;
            }
            int key = event.key();
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                cancelTimeInput();
                this.setFocused(false);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                unfocusTime();
                this.setFocused(false);
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (this.timeReplace) {
                    this.timeInput = "";
                    this.timeReplace = false;
                } else if (!this.timeInput.isEmpty()) {
                    this.timeInput = this.timeInput.substring(0, this.timeInput.length() - 1);
                }
                applyTimeInput(false);
                return true;
            }
            if (key == GLFW.GLFW_KEY_DELETE) {
                this.timeInput = "";
                this.timeReplace = false;
                return true;
            }
            return true;
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            if (this.page == Page.GEAR && this.nameFocused) {
                int code = event.codepoint();
                if (code < 32 || code == 127 || code == '§') {
                    return false;
                }
                if (this.itemName.length() >= CreationUtil.CUSTOM_NAME_MAX_LENGTH) {
                    return true;
                }
                this.itemName += Character.toString(code);
                return true;
            }
            if (this.page != Page.ALCHEMY || !this.timeFocused || !ClientCreationState.get().potionTiming()) {
                return false;
            }
            int code = event.codepoint();
            if (code != ':' && (code < '0' || code > '9')) {
                return false;
            }
            String next = this.timeReplace ? Character.toString(code) : this.timeInput + Character.toString(code);
            if (code == ':' && this.timeInput.indexOf(':') >= 0 && !this.timeReplace) {
                return true;
            }
            if (next.length() > 5) {
                return true;
            }
            this.timeInput = next;
            this.timeReplace = false;
            applyTimeInput(false);
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
                    if (this.potionFormMenu) {
                        this.selectedEffectId = chosen;
                        closeFormMenu();
                        return true;
                    }
                    if (this.itemGroupFormMenu) {
                        if (this.formMenuParent != null) {
                            this.selectedGroupItems.put(this.formMenuParent, chosen);
                        }
                        setSelectedItem(chosen);
                    } else {
                        CreationGearSlot gearSlot = CreationGearSlot.of(chosen);
                        if (gearSlot == null) {
                            gearSlot = CreationGearSlot.of(this.formMenuParent);
                        }
                        if (gearSlot != null) {
                            setSelectedItem(chosen);
                        } else if (this.formMenuParent != null) {
                            this.selectedForms.put(this.formMenuParent, chosen);
                            this.selectedId = this.formMenuParent;
                        } else {
                            setSelectedItem(chosen);
                        }
                    }
                    closeFormMenu();
                    return true;
                }
            }
            return true;
        }

        private void openFormMenu(String parentId, List<String> choices, SlotHit hit, String overlayKey) {
            this.potionFormMenu = false;
            this.itemGroupFormMenu = false;
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

        private void openPotionGroupMenu(String groupId, List<String> choices, SlotHit hit) {
            this.potionFormMenu = true;
            openFormMenu(groupId, choices, hit, "gui.yha.creation.choose_potion");
            this.potionFormMenu = true;
        }

        private void openItemGroupMenu(String groupId, List<String> choices, SlotHit hit) {
            this.itemGroupFormMenu = true;
            openFormMenu(groupId, choices, hit, "gui.yha.creation.choose_item");
            this.itemGroupFormMenu = true;
        }

        private void closeFormMenu() {
            this.formMenuParent = null;
            this.formMenuChoices = List.of();
            this.potionFormMenu = false;
            this.itemGroupFormMenu = false;
        }

        private void selectAssigned(CreationQuickSlot recipe) {
            if (recipe == null) {
                return;
            }
            if (recipe.isPotion()) {
                this.page = Page.ALCHEMY;
                this.selectedEffectId = recipe.id().toString();
                this.selectedPotionForm = recipe.form();
                this.potionDurationSeconds = Math.max(1, recipe.durationTicks() / 20);
                this.potionAmplifier = recipe.amplifier();
                return;
            }
            String assignedId = recipe.id().toString();
            selectAssignedItem(assignedId);
            this.enchantLevels.clear();
            this.enchantScroll = 0;
            for (CreationCreatePayload.EnchantChoice choice : recipe.enchants()) {
                if (choice != null && choice.enchantId() != null && !choice.enchantId().isBlank() && choice.level() > 0) {
                    this.enchantLevels.put(choice.enchantId(), choice.level());
                }
            }
            if (CreationGearSlot.of(assignedId) != null) {
                this.page = Page.GEAR;
            } else if (this.page == Page.ALCHEMY) {
                this.page = Page.BLOCKS;
            }
        }

        private CreationQuickSlot currentQuickSlotSelection() {
            if (this.page == Page.ALCHEMY) {
                if (this.selectedEffectId == null) {
                    return null;
                }
                Identifier effectId = safeId(this.selectedEffectId);
                if (effectId == null) {
                    return null;
                }
                CreationSyncPayload.ClientPotionEntry potion = ClientCreationState.findPotion(this.selectedEffectId);
                int ticks = potion != null && potion.instant() ? 1 : this.potionDurationSeconds * 20;
                return CreationQuickSlot.potion(effectId, this.selectedPotionForm, ticks, this.potionAmplifier);
            }
            String createId = displayedFormId(this.selectedId);
            if (createId == null) {
                return null;
            }
            Identifier itemId = safeId(createId);
            if (itemId == null) {
                return null;
            }
            return CreationQuickSlot.item(itemId, selectedEnchantChoices());
        }

        private void selectAssignedItem(String assignedId) {
            CreationSyncPayload.ClientEntry parent = ClientCreationState.findParent(assignedId);
            String nextId = parent != null ? parent.itemId() : assignedId;
            CreationGearSlot nextSlot = CreationGearSlot.of(nextId);
            if (nextSlot != CreationGearSlot.of(this.selectedId)) {
                this.enchantLevels.clear();
                this.enchantScroll = 0;
            }
            if (nextId != null && !nextId.equals(this.selectedId)) {
                this.itemName = "";
                this.nameFocused = false;
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
            ClientCreationState.ItemGroupView group = findItemGroup(assignedId);
            if (group != null) {
                this.selectedGroupItems.put(group.groupId(), assignedId);
                this.selectedId = assignedId;
            }
        }

        private String displayedFormId(String parentId) {
            if (parentId == null) {
                return null;
            }
            if (CreationGearSlot.of(parentId) != null) {
                return parentId;
            }
            return this.selectedForms.getOrDefault(parentId, parentId);
        }

        private void setSelectedItem(String itemId) {
            if (itemId == null) {
                this.selectedId = null;
                this.enchantLevels.clear();
                this.enchantScroll = 0;
                this.draggingEnchantId = null;
                return;
            }
            boolean changed = !itemId.equals(this.selectedId);
            CreationGearSlot slot = CreationGearSlot.of(itemId);
            if (slot != null) {
                this.selectedGearVariants.put(slot, itemId);
                this.selectedForms.remove(itemId);
            }
            if (changed) {
                this.enchantLevels.clear();
                this.enchantScroll = 0;
                this.draggingEnchantId = null;
                this.itemName = "";
                this.nameFocused = false;
            } else {
                pruneEnchantLevels();
            }
            this.selectedId = itemId;
        }

        private void turnPage() {
            if (this.page == Page.GEAR || this.page == Page.ALCHEMY) {
                return;
            }
            int materials = ClientCreationState.unlockedItemGroups(CreationTab.MATERIALS).size();
            int blocks = ClientCreationState.unlockedItemGroups(CreationTab.BLOCKS).size();
            int matPages = Math.max(1, ceilDiv(materials, MAT_PER_PAGE));
            int blockPages = Math.max(1, ceilDiv(blocks, BLOCKS_COLS * BLOCKS_ROWS));
            this.materialsPage = (this.materialsPage + 1) % matPages;
            this.blocksPage = (this.blocksPage + 1) % blockPages;
        }

        private SlotHit findClickedSlot(int mouseX, int mouseY, int x, int y) {
            if (this.page == Page.GEAR) {
                return hitGearSlot(mouseX, mouseY, x, y);
            }
            if (this.page == Page.ALCHEMY) {
                return null;
            }
            SlotHit materials = hitGrid(mouseX, mouseY, x, y, ClientCreationState.unlockedItemGroups(CreationTab.MATERIALS), MAT_X, MAT_Y, MAT_COLS, MAT_ROWS, GRID_SLOT, this.materialsPage, MAT_COLS);
            if (materials != null) {
                return materials;
            }
            return hitGrid(mouseX, mouseY, x, y, ClientCreationState.unlockedItemGroups(CreationTab.BLOCKS), BLOCKS_X, BLOCKS_Y, BLOCKS_COLS, BLOCKS_ROWS, GRID_SLOT, this.blocksPage, BLOCKS_COLS);
        }

        private SlotHit hitGrid(
                int mouseX,
                int mouseY,
                int originX,
                int originY,
                List<ClientCreationState.ItemGroupView> groups,
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
                    if (entryIndex < 0 || entryIndex >= groups.size()) {
                        return null;
                    }
                    ClientCreationState.ItemGroupView group = groups.get(entryIndex);
                    String clickId = group.isSingleton() ? group.firstItemId() : group.groupId();
                    if (clickId == null) {
                        return null;
                    }
                    return new SlotHit(clickId, slotX, slotY, slotSize);
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

        private boolean formMenuContains(int mouseX, int mouseY) {
            List<String> choices = this.formMenuChoices;
            if (choices == null || choices.isEmpty()) {
                return false;
            }
            int cols = formMenuCols(choices.size());
            int rows = formMenuRows(choices.size());
            return contains(mouseX, mouseY, this.formMenuX, this.formMenuY, formMenuWidth(cols), formMenuHeight(rows));
        }

        private static void blitLock(GuiGraphicsExtractor gui, int x, int y) {
            blit(gui, TEX_LOCK, x, y, 0, 0, LOCK_SIZE, LOCK_SIZE, 16, 16);
        }

        private static void blitSubmenuBadge(GuiGraphicsExtractor gui, int slotX, int slotY, int slotSize) {
            int x = slotX + slotSize - SUBMENU_BADGE - 1;
            int y = slotY + 1;
            blit(gui, TEX_SUBMENU_BADGE, x, y, 0, 0, SUBMENU_BADGE, SUBMENU_BADGE, SUBMENU_BADGE, SUBMENU_BADGE);
        }

        private static void drawLockedBox(GuiGraphicsExtractor gui, int x, int y, int width, int height) {
            gui.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xAA6A6A6A);
            blitLock(gui, x + (width - LOCK_SIZE) / 2, y + (height - LOCK_SIZE) / 2);
            gui.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x44000000);
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

        private static int quickSlotX(int originX, int index) {
            return originX + QUICK_X + (index % QUICK_COLS) * QUICK_SIZE;
        }

        private static int quickSlotY(int originY, int index) {
            return originY + QUICK_Y + (index / QUICK_COLS) * QUICK_SIZE;
        }

        private static void drawCreateCost(GuiGraphicsExtractor gui, Minecraft minecraft, int originX, int originY, int cost, boolean canCreate) {
            String text = String.valueOf(cost);
            int textW = minecraft.font.width(text);
            int totalW = LIPID_ICON_W + 2 + textW;
            int left = originX + CREATE_X + Math.max(0, (CREATE_W - totalW) / 2);
            int iconY = originY + CREATE_Y - LIPID_ICON_H - 3;
            int textY = iconY + Math.max(0, (LIPID_ICON_H - minecraft.font.lineHeight) / 2);
            blit(gui, TEX_LIPID, left, iconY, 0, 0, LIPID_ICON_W, LIPID_ICON_H, LIPID_ICON_W, LIPID_ICON_H);
            gui.text(minecraft.font, text, left + LIPID_ICON_W + 2, textY, canCreate ? 0xFF3A2A18 : 0xFFB03A2A, false);
        }

        private static void drawItem(GuiGraphicsExtractor gui, ItemStack stack, int x, int y, int size) {
            if (stack == null || stack.isEmpty() || size <= 0) {
                return;
            }
            if (size == 16) {
                gui.item(stack, x, y);
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

        private record LipidSpendPopup(int amount, long startMs) {
            private LipidSpendPopup(int amount) {
                this(amount, System.currentTimeMillis());
            }
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
                    blitSubmenuBadge(gui, slotX, slotY, GEAR_SLOT);
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
            boolean hoveringPreview = !formMenuContains(mouseX, mouseY)
                    && contains(mouseX, mouseY, previewX, previewY, ENCHANT_SLOT_SIZE, ENCHANT_SLOT_SIZE);
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
                        if (!hoveringPreview
                                && !formMenuContains(mouseX, mouseY)
                                && contains(mouseX, mouseY, cellX, cellY, ENCHANT_ROW_W, ENCHANT_ROW_H)) {
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
            if (access != null && !allowsConflictingEnchants()) {
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
            pruneEnchantLevels();
            Map<Identifier, Integer> selected = selectedEnchantLevels();
            boolean allowConflicts = allowsConflictingEnchants();
            HolderLookup.Provider access = clientAccess();
            int total = 0;
            for (Map.Entry<String, Integer> entry : this.enchantLevels.entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0) {
                    continue;
                }
                CreationSyncPayload.ClientEnchantEntry catalog = ClientCreationState.findEnchant(entry.getKey());
                if (catalog == null) {
                    continue;
                }
                int cost = catalog.costForLevel(entry.getValue());
                Identifier enchantId = parseEnchantId(entry.getKey());
                if (allowConflicts && enchantId != null && CreationEnchantments.conflictsWithAny(access, enchantId, selected)) {
                    cost *= CreationUtil.CONFLICTING_ENCHANT_COST_MULTIPLIER;
                }
                total += cost;
            }
            return total;
        }

        private Map<Identifier, Integer> selectedEnchantLevels() {
            Map<Identifier, Integer> selected = new HashMap<>();
            for (Map.Entry<String, Integer> entry : this.enchantLevels.entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0) {
                    continue;
                }
                Identifier enchantId = parseEnchantId(entry.getKey());
                if (enchantId != null) {
                    selected.put(enchantId, entry.getValue());
                }
            }
            return selected;
        }

        private static Identifier parseEnchantId(String enchantId) {
            if (enchantId == null || enchantId.isBlank()) {
                return null;
            }
            try {
                return Identifier.parse(enchantId);
            } catch (RuntimeException ignored) {
                return Identifier.tryParse(enchantId);
            }
        }

        private List<CreationCreatePayload.EnchantChoice> selectedEnchantChoices() {
            if (this.page != Page.GEAR) {
                return List.of();
            }
            pruneEnchantLevels();
            List<CreationCreatePayload.EnchantChoice> choices = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : this.enchantLevels.entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0) {
                    continue;
                }
                choices.add(new CreationCreatePayload.EnchantChoice(entry.getKey(), entry.getValue()));
            }
            return choices;
        }

        private void pruneEnchantLevels() {
            if (this.enchantLevels.isEmpty()) {
                return;
            }
            ItemStack stack = previewStack(false);
            HolderLookup.Provider access = clientAccess();
            if (stack.isEmpty() || access == null) {
                this.enchantLevels.clear();
                return;
            }
            this.enchantLevels.entrySet().removeIf(entry -> {
                if (entry.getValue() == null || entry.getValue() <= 0) {
                    return true;
                }
                Identifier enchantId = parseEnchantId(entry.getKey());
                return enchantId == null || !CreationEnchantments.canEnchant(access, enchantId, stack);
            });
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
            applyPreviewName(stack);
            return stack;
        }

        private void applyPreviewName(ItemStack stack) {
            String cleaned = CreationUtil.sanitizeCustomName(this.itemName);
            if (cleaned.isEmpty()) {
                return;
            }
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(cleaned));
        }

        private boolean hitNameField(int mouseX, int mouseY, int originX, int originY) {
            return this.selectedId != null && contains(mouseX, mouseY, originX + NAME_X, originY + NAME_Y, NAME_W, NAME_H);
        }

        private void drawNameField(GuiGraphicsExtractor gui, Minecraft minecraft, int originX, int originY) {
            if (this.selectedId == null) {
                return;
            }
            int x = originX + NAME_X;
            int y = originY + NAME_Y;
            int border = this.nameFocused ? 0xFFFFD27A : 0xFF8A6A48;
            gui.fill(x, y, x + NAME_W, y + NAME_H, 0x33000000);
            drawFrame(gui, x, y, NAME_W, NAME_H, border);
            int innerX = x + NAME_PAD;
            int innerW = NAME_W - NAME_PAD * 2;
            int textY = y + (NAME_H - minecraft.font.lineHeight) / 2;
            if (this.itemName.isBlank()) {
                gui.text(
                        minecraft.font,
                        Component.translatable("gui.yha.creation.item_name").getString(),
                        innerX,
                        textY,
                        0xFF9A8A78,
                        false
                );
                if (this.nameFocused && ((System.currentTimeMillis() / 400L) % 2L == 0L)) {
                    gui.fill(innerX, y + 3, innerX + 1, y + NAME_H - 3, 0xFF3A2A18);
                }
                return;
            }
            int textW = minecraft.font.width(this.itemName);
            int scroll = this.nameFocused ? Math.max(0, textW - innerW + 1) : 0;
            int drawX = innerX - scroll;
            gui.enableScissor(innerX, y + 1, innerX + innerW, y + NAME_H - 1);
            gui.text(minecraft.font, this.itemName, drawX, textY, 0xFF3A2A18, false);
            if (this.nameFocused && ((System.currentTimeMillis() / 400L) % 2L == 0L)) {
                int cursorX = drawX + textW;
                gui.fill(cursorX, y + 3, cursorX + 1, y + NAME_H - 3, 0xFF3A2A18);
            }
            gui.disableScissor();
        }

        private void drawLipidSpendPops(GuiGraphicsExtractor gui, Minecraft minecraft, int originX, int originY, int lipids) {
            long now = System.currentTimeMillis();
            this.lipidPops.removeIf(pop -> now - pop.startMs() >= LIPID_POP_MS);
            int baseX = originX + LIPID_TEXT_X + minecraft.font.width(String.valueOf(lipids)) + 3;
            for (LipidSpendPopup pop : this.lipidPops) {
                float t = (now - pop.startMs()) / (float) LIPID_POP_MS;
                int alpha = Mth.clamp(Math.round((1.0f - t) * 255.0f), 0, 255);
                if (alpha <= 0) {
                    continue;
                }
                int color = (alpha << 24) | 0x00B03A2A;
                int popY = originY + LIPID_TEXT_Y - Math.round(t * 12.0f);
                gui.text(minecraft.font, "-" + pop.amount(), baseX, popY, color, false);
            }
        }

        private static String enchantName(CreationSyncPayload.ClientEnchantEntry entry) {
            try {
                return CreationEnchantments.displayName(clientAccess(), Identifier.parse(entry.enchantId())).getString();
            } catch (RuntimeException ignored) {
                return entry.enchantId();
            }
        }

        private Component enchantTooltip(CreationSyncPayload.ClientEnchantEntry entry, int level) {
            int cost = entry.costForLevel(level);
            boolean conflicting = false;
            if (level > 0 && allowsConflictingEnchants()) {
                try {
                    Identifier enchantId = Identifier.parse(entry.enchantId());
                    conflicting = CreationEnchantments.conflictsWithAny(clientAccess(), enchantId, selectedEnchantLevels());
                } catch (RuntimeException ignored) {
                }
            }
            String name = enchantName(entry);
            String levelText = level <= 0 ? "0" : String.valueOf(level);
            if (conflicting) {
                cost *= CreationUtil.CONFLICTING_ENCHANT_COST_MULTIPLIER;
                return Component.literal(
                        name + " " + levelText + " - "
                                + Component.translatable("gui.yha.creation.enchant_conflict_cost", cost).getString()
                );
            }
            return Component.literal(
                    name + " " + levelText + " - "
                            + Component.translatable("gui.yha.creation.enchant_cost", cost).getString()
            );
        }

        private static boolean allowsConflictingEnchants() {
            return ClientCreationState.get().enchantConflicts();
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

        private void drawAlchemyPage(GuiGraphicsExtractor gui, Minecraft minecraft, int originX, int originY, int mouseX, int mouseY) {
            CreationSyncPayload state = ClientCreationState.get();
            CreationSyncPayload.ClientPotionEntry selected = ClientCreationState.findPotion(this.selectedEffectId);
            boolean instant = selected != null && selected.instant();
            boolean canTime = state.potionTiming() && selected != null && !instant;
            boolean timeLocked = !state.potionTiming() && (selected == null || !selected.instant());
            if (timeLocked) {
                drawLockedBox(gui, originX + TIME_X, originY + TIME_Y, TIME_W, TIME_H);
            } else {
                String timeText = instant
                        ? Component.translatable("gui.yha.creation.potion_instant").getString()
                        : (this.timeFocused ? this.timeInput : formatTime(this.potionDurationSeconds));
                int timeColor = canTime ? (this.timeFocused ? 0xFF3A2A18 : 0xFF5A4030) : 0xFF9A8A78;
                int timeX = originX + TIME_X + Math.max(0, (TIME_W - minecraft.font.width(timeText.isEmpty() ? "0:00" : timeText)) / 2);
                int timeY = originY + TIME_Y + (TIME_H - minecraft.font.lineHeight) / 2;
                gui.text(minecraft.font, timeText, timeX, timeY, timeColor, false);
                if (this.timeFocused && canTime && ((System.currentTimeMillis() / 400L) % 2L == 0L)) {
                    int cursorX = timeX + minecraft.font.width(timeText);
                    gui.fill(cursorX, originY + TIME_Y + 3, cursorX + 1, originY + TIME_Y + TIME_H - 3, 0xFF3A2A18);
                }
            }
            boolean overMenu = formMenuContains(mouseX, mouseY);
            if (!overMenu && contains(mouseX, mouseY, originX + TIME_X, originY + TIME_Y, TIME_W, TIME_H)) {
                if (selected != null && selected.instant()) {
                    gui.setTooltipForNextFrame(minecraft.font, Component.translatable("gui.yha.creation.potion_instant"), mouseX, mouseY);
                } else if (!state.potionTiming()) {
                    gui.setTooltipForNextFrame(minecraft.font, Component.translatable("gui.yha.creation.tab_locked"), mouseX, mouseY);
                }
            }

            List<ClientCreationState.PotionGroupView> groups = ClientCreationState.unlockedPotionGroups();
            for (int i = 0; i < groups.size() && i < ALCHEMY_COLS * ALCHEMY_ROWS; i++) {
                int col = i % ALCHEMY_COLS;
                int row = i / ALCHEMY_COLS;
                int slotX = alchemySlotX(originX, col);
                int slotY = alchemySlotY(originY, row);
                ClientCreationState.PotionGroupView group = groups.get(i);
                ItemStack icon = groupIcon(group);
                int iconSize = GRID_ICON;
                int iconX = slotX + 1;
                int iconY = slotY + 1;
                drawItem(gui, icon, iconX, iconY, iconSize);
                if (!group.isSingleton()) {
                    blitSubmenuBadge(gui, slotX, slotY, GRID_SLOT);
                }
                boolean selectedGroup = group.effects().stream().anyMatch(entry -> entry.effectId().equals(this.selectedEffectId));
                if (selectedGroup) {
                    gui.fill(iconX - 1, iconY - 1, iconX + iconSize + 1, iconY, 0xFFFFD27A);
                    gui.fill(iconX - 1, iconY + iconSize, iconX + iconSize + 1, iconY + iconSize + 1, 0xFFFFD27A);
                    gui.fill(iconX - 1, iconY - 1, iconX, iconY + iconSize + 1, 0xFFFFD27A);
                    gui.fill(iconX + iconSize, iconY - 1, iconX + iconSize + 1, iconY + iconSize + 1, 0xFFFFD27A);
                }
                if (!overMenu && contains(mouseX, mouseY, slotX, slotY, GRID_SLOT, GRID_SLOT)) {
                    gui.setTooltipForNextFrame(minecraft.font, groupTooltip(group), mouseX, mouseY);
                }
            }

            if (selected != null) {
                drawAlchemyForm(gui, minecraft, originX, originY, mouseX, mouseY, CreationPotionForm.DRINKABLE, DIAMOND_TOP_X, DIAMOND_TOP_Y, true);
                drawAlchemyForm(gui, minecraft, originX, originY, mouseX, mouseY, CreationPotionForm.LINGERING, DIAMOND_LEFT_X, DIAMOND_LEFT_Y, state.potionLinger());
                drawAlchemyForm(gui, minecraft, originX, originY, mouseX, mouseY, CreationPotionForm.ARROW, DIAMOND_RIGHT_X, DIAMOND_RIGHT_Y, state.potionArrow());
                drawAlchemyForm(gui, minecraft, originX, originY, mouseX, mouseY, CreationPotionForm.SPLASH, DIAMOND_BOTTOM_X, DIAMOND_BOTTOM_Y, state.potionSplash());
            }

            int maxAmp = maxClientAmplifier();
            int boxX = originX + AMP_BOX_X;
            int boxY = originY + AMP_BOX_Y;
            int trackX = ampTrackX(originX);
            int trackY = ampTrackY(originY);
            boolean ampLocked = !state.potionPotency() && !state.potionMaster();
            if (ampLocked) {
                drawLockedBox(gui, boxX, boxY, AMP_BOX_W, AMP_BOX_H);
            } else {
                String ampLabel = Component.translatable("gui.yha.creation.amplifier", roman(this.potionAmplifier + 1)).getString();
                int labelX = boxX + Math.max(0, (AMP_BOX_W - minecraft.font.width(ampLabel)) / 2);
                gui.text(minecraft.font, ampLabel, labelX, boxY + 4, 0xFF3A2A18, false);
                gui.fill(trackX, trackY + 2, trackX + AMP_TRACK_W, trackY + 4, 0xFF8A6A48);
                float ratio = maxAmp <= 0 ? 0.0f : this.potionAmplifier / (float) maxAmp;
                int knobX = trackX + Math.round(ratio * (AMP_TRACK_W - 4));
                gui.fill(knobX, trackY, knobX + 4, trackY + SLIDER_H, 0xFFFFD27A);
                if (this.potionAmplifier > 0) {
                    gui.fill(trackX, trackY + 2, knobX + 2, trackY + 4, 0xFFFFD27A);
                }
            }
            if (!overMenu && contains(mouseX, mouseY, boxX, boxY, AMP_BOX_W, AMP_BOX_H) && ampLocked) {
                gui.setTooltipForNextFrame(minecraft.font, Component.translatable("gui.yha.creation.tab_locked"), mouseX, mouseY);
            }
        }

        private void drawAlchemyForm(
                GuiGraphicsExtractor gui,
                Minecraft minecraft,
                int originX,
                int originY,
                int mouseX,
                int mouseY,
                CreationPotionForm form,
                int slotX,
                int slotY,
                boolean unlocked
        ) {
            int x = originX + slotX;
            int y = originY + slotY;
            if (!unlocked) {
                int lock = 16;
                blit(gui, TEX_LOCK, x + (DIAMOND_SIZE - lock) / 2, y + (DIAMOND_SIZE - lock) / 2, 0, 0, lock, lock, lock, lock);
                if (!formMenuContains(mouseX, mouseY) && contains(mouseX, mouseY, x, y, DIAMOND_SIZE, DIAMOND_SIZE)) {
                    gui.setTooltipForNextFrame(minecraft.font, Component.translatable("gui.yha.creation.tab_locked"), mouseX, mouseY);
                }
                return;
            }
            ItemStack stack = alchemyPreviewStack(this.selectedEffectId, form);
            int icon = 15;
            drawItem(gui, stack, x + 1, y + 1, icon);
            if (this.selectedPotionForm == form) {
                drawFrame(gui, x, y, DIAMOND_SIZE, DIAMOND_SIZE, 0xFFFFD27A);
            }
            if (!formMenuContains(mouseX, mouseY) && contains(mouseX, mouseY, x, y, DIAMOND_SIZE, DIAMOND_SIZE) && !stack.isEmpty()) {
                CreationSyncPayload.ClientPotionEntry potion = ClientCreationState.findPotion(this.selectedEffectId);
                int cost = potion == null ? 0 : potion.costFor(this.potionAmplifier, potionDurationForCost(potion), form.factor());
                gui.setTooltipForNextFrame(
                        minecraft.font,
                        Component.translatable(
                                "gui.yha.creation.potion_cost",
                                CreationPotions.itemName(safeId(this.selectedEffectId), form),
                                cost
                        ),
                        mouseX,
                        mouseY
                );
            }
        }

        private boolean handleAlchemyClick(int mouseX, int mouseY, int originX, int originY, MouseButtonEvent event) {
            Minecraft minecraft = Minecraft.getInstance();
            CreationSyncPayload state = ClientCreationState.get();
            if (contains(mouseX, mouseY, originX + TIME_X, originY + TIME_Y, TIME_W, TIME_H)) {
                CreationSyncPayload.ClientPotionEntry potion = ClientCreationState.findPotion(this.selectedEffectId);
                boolean canFocus = state.potionTiming() && (potion == null || !potion.instant());
                if (canFocus) {
                    if (!this.timeFocused) {
                        this.timeInput = formatTime(this.potionDurationSeconds);
                    }
                    this.timeReplace = true;
                    this.timeFocused = true;
                    this.setFocused(true);
                } else {
                    this.timeFocused = false;
                    this.setFocused(false);
                }
                clickSound();
                return true;
            }
            unfocusTime();

            if (contains(mouseX, mouseY, originX + AMP_BOX_X, originY + AMP_BOX_Y, AMP_BOX_W, AMP_BOX_H)) {
                if (!state.potionPotency() && !state.potionMaster()) {
                    minecraft.gui.setOverlayMessage(Component.translatable("gui.yha.creation.tab_locked"), false);
                    clickSound();
                    return true;
                }
                setAmplifierFromMouse(mouseX, originX);
                this.draggingAmplifier = true;
                clickSound();
                return true;
            }

            CreationPotionForm form = hitAlchemyForm(mouseX, mouseY, originX, originY);
            if (form != null) {
                if (!formUnlocked(form, state)) {
                    minecraft.gui.setOverlayMessage(Component.translatable("gui.yha.creation.tab_locked"), false);
                    clickSound();
                    return true;
                }
                if (this.selectedEffectId != null) {
                    this.selectedPotionForm = form;
                    clickSound();
                }
                return true;
            }

            List<ClientCreationState.PotionGroupView> groups = ClientCreationState.unlockedPotionGroups();
            for (int i = 0; i < groups.size() && i < ALCHEMY_COLS * ALCHEMY_ROWS; i++) {
                int col = i % ALCHEMY_COLS;
                int row = i / ALCHEMY_COLS;
                int slotX = alchemySlotX(originX, col);
                int slotY = alchemySlotY(originY, row);
                if (!contains(mouseX, mouseY, slotX, slotY, GRID_SLOT, GRID_SLOT)) {
                    continue;
                }
                ClientCreationState.PotionGroupView group = groups.get(i);
                List<String> ids = group.effects().stream().map(CreationSyncPayload.ClientPotionEntry::effectId).toList();
                if (!ids.isEmpty() && !ids.contains(this.selectedEffectId)) {
                    this.selectedEffectId = ids.getFirst();
                }
                if (ids.size() > 1 && event.button() == 1) {
                    openPotionGroupMenu(group.groupId(), ids, new SlotHit(group.groupId(), slotX, slotY, GRID_SLOT));
                } else {
                    closeFormMenu();
                }
                clickSound();
                return true;
            }
            return false;
        }

        private boolean handleAlchemyScroll(int mouseX, int mouseY, double scrollY) {
            int originX = this.getX();
            int originY = this.getY();
            CreationSyncPayload.ClientPotionEntry potion = ClientCreationState.findPotion(this.selectedEffectId);
            if (contains(mouseX, mouseY, originX + TIME_X, originY + TIME_Y, TIME_W, TIME_H)
                    && ClientCreationState.get().potionTiming()
                    && (potion == null || !potion.instant())) {
                int max = potion == null ? 480 : Math.max(1, potion.maxDurationSeconds());
                int left = originX + TIME_X + TIME_W / 2;
                int delta = scrollY > 0 ? 1 : -1;
                if (mouseX < left) {
                    delta *= 60;
                }
                this.potionDurationSeconds = Mth.clamp(this.potionDurationSeconds + delta, 1, max);
                if (this.timeFocused) {
                    this.timeInput = formatTime(this.potionDurationSeconds);
                    this.timeReplace = true;
                }
                return true;
            }
            if (contains(mouseX, mouseY, originX + AMP_BOX_X, originY + AMP_BOX_Y, AMP_BOX_W, AMP_BOX_H)
                    && (ClientCreationState.get().potionPotency() || ClientCreationState.get().potionMaster())) {
                int max = maxClientAmplifier();
                int delta = scrollY > 0 ? 1 : -1;
                this.potionAmplifier = Mth.clamp(this.potionAmplifier + delta, 0, max);
                return true;
            }
            return false;
        }

        private void setAmplifierFromMouse(int mouseX, int originX) {
            int max = maxClientAmplifier();
            int trackX = ampTrackX(originX);
            float ratio = AMP_TRACK_W <= 0 ? 0.0f : (mouseX - trackX) / (float) AMP_TRACK_W;
            this.potionAmplifier = Mth.clamp(Math.round(ratio * max), 0, max);
        }

        private static int alchemySlotX(int originX, int col) {
            return originX + ALCHEMY_GRID_X + col * GRID_SLOT;
        }

        private static int alchemySlotY(int originY, int row) {
            return originY + ALCHEMY_GRID_Y + row * GRID_SLOT;
        }

        private static int ampTrackX(int originX) {
            return originX + AMP_BOX_X + Math.max(0, (AMP_BOX_W - AMP_TRACK_W) / 2);
        }

        private static int ampTrackY(int originY) {
            return originY + AMP_BOX_Y + 16;
        }

        private static int maxClientAmplifier() {
            CreationSyncPayload state = ClientCreationState.get();
            if (state.potionMaster()) {
                return 2;
            }
            if (state.potionPotency()) {
                return 1;
            }
            return 0;
        }

        private CreationPotionForm hitAlchemyForm(int mouseX, int mouseY, int originX, int originY) {
            if (contains(mouseX, mouseY, originX + DIAMOND_TOP_X, originY + DIAMOND_TOP_Y, DIAMOND_SIZE, DIAMOND_SIZE)) {
                return CreationPotionForm.DRINKABLE;
            }
            if (contains(mouseX, mouseY, originX + DIAMOND_LEFT_X, originY + DIAMOND_LEFT_Y, DIAMOND_SIZE, DIAMOND_SIZE)) {
                return CreationPotionForm.LINGERING;
            }
            if (contains(mouseX, mouseY, originX + DIAMOND_RIGHT_X, originY + DIAMOND_RIGHT_Y, DIAMOND_SIZE, DIAMOND_SIZE)) {
                return CreationPotionForm.ARROW;
            }
            if (contains(mouseX, mouseY, originX + DIAMOND_BOTTOM_X, originY + DIAMOND_BOTTOM_Y, DIAMOND_SIZE, DIAMOND_SIZE)) {
                return CreationPotionForm.SPLASH;
            }
            return null;
        }

        private static boolean formUnlocked(CreationPotionForm form, CreationSyncPayload state) {
            return switch (form) {
                case DRINKABLE -> true;
                case SPLASH -> state.potionSplash();
                case LINGERING -> state.potionLinger();
                case ARROW -> state.potionArrow();
            };
        }

        private ItemStack alchemyPreviewStack(String effectId, CreationPotionForm form) {
            CreationSyncPayload.ClientPotionEntry potion = ClientCreationState.findPotion(effectId);
            int ticks = potion != null && potion.instant() ? 1 : this.potionDurationSeconds * 20;
            return ClientCreationState.potionStack(effectId, form, ticks, this.potionAmplifier);
        }

        private ClientCreationState.ItemGroupView findItemGroup(String id) {
            if (id == null) {
                return null;
            }
            for (CreationTab tab : List.of(CreationTab.MATERIALS, CreationTab.BLOCKS)) {
                for (ClientCreationState.ItemGroupView group : ClientCreationState.unlockedItemGroups(tab)) {
                    if (id.equals(group.groupId()) || group.contains(id)) {
                        return group;
                    }
                }
            }
            return null;
        }

        private String displayedGroupItem(ClientCreationState.ItemGroupView group) {
            String selected = displayedFormId(this.selectedId);
            if (group.contains(this.selectedId)) {
                return selected != null && group.contains(selected) ? selected : this.selectedId;
            }
            if (selected != null && group.contains(selected)) {
                return selected;
            }
            String remembered = this.selectedGroupItems.get(group.groupId());
            if (remembered != null && group.contains(remembered)) {
                return remembered;
            }
            if (group.isSingleton() && group.firstItemId() != null) {
                return displayedFormId(group.firstItemId());
            }
            if (group.iconId() != null && !group.iconId().isBlank()) {
                return group.iconId();
            }
            return group.firstItemId();
        }

        private ItemStack itemGroupIcon(ClientCreationState.ItemGroupView group) {
            return ClientCreationState.stackOf(displayedGroupItem(group));
        }

        private Component itemGroupTooltip(ClientCreationState.ItemGroupView group) {
            String displayed = displayedGroupItem(group);
            ItemStack stack = ClientCreationState.stackOf(displayed);
            if (!stack.isEmpty()) {
                return stack.getHoverName();
            }
            if (group.isSingleton() && displayed != null) {
                return Component.literal(displayed);
            }
            return groupDisplayName(group.groupId());
        }

        private static ItemStack groupIcon(ClientCreationState.PotionGroupView group) {
            if (group.isSingleton() && group.first() != null) {
                return CreationPotions.previewStack(safeId(group.first().effectId()));
            }
            ItemStack icon = ClientCreationState.stackOf(group.iconId());
            return icon.isEmpty() ? CreationPotions.previewStack(safeId(group.first() == null ? null : group.first().effectId())) : icon;
        }

        private static Identifier safeId(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return Identifier.parse(raw);
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        private static Component groupTooltip(ClientCreationState.PotionGroupView group) {
            if (group.isSingleton() && group.first() != null) {
                return CreationUtil.potionDisplayName(safeId(group.first().effectId()));
            }
            return groupDisplayName(group.groupId());
        }

        private static Component groupDisplayName(String groupId) {
            if (groupId == null || groupId.isBlank()) {
                return Component.translatable("gui.yha.creation.tab.alchemy");
            }
            String key = "gui.yha.creation.group." + groupId.replace(':', '.');
            if (Language.getInstance().has(key)) {
                return Component.translatable(key);
            }
            int slash = groupId.indexOf(':');
            String path = slash >= 0 ? groupId.substring(slash + 1) : groupId;
            String[] parts = path.split("_");
            StringBuilder pretty = new StringBuilder();
            for (String part : parts) {
                if (part.isEmpty()) {
                    continue;
                }
                if (!pretty.isEmpty()) {
                    pretty.append(' ');
                }
                pretty.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    pretty.append(part.substring(1));
                }
            }
            return Component.literal(pretty.isEmpty() ? groupId : pretty.toString());
        }

        private int potionDurationForCost(CreationSyncPayload.ClientPotionEntry potion) {
            if (potion != null && potion.instant()) {
                return 15;
            }
            return this.potionDurationSeconds;
        }

        private void unfocusTime() {
            if (this.timeFocused) {
                applyTimeInput(true);
            }
            this.timeFocused = false;
            this.timeReplace = false;
        }

        private void cancelTimeInput() {
            this.timeFocused = false;
            this.timeReplace = false;
            this.timeInput = formatTime(this.potionDurationSeconds);
        }

        private void applyTimeInput(boolean commitDisplay) {
            Integer parsed = parseTimeInput(this.timeInput);
            if (parsed == null) {
                if (commitDisplay) {
                    this.timeInput = formatTime(this.potionDurationSeconds);
                }
                return;
            }
            this.potionDurationSeconds = Mth.clamp(parsed, 1, maxPotionDurationSeconds());
            if (commitDisplay) {
                this.timeInput = formatTime(this.potionDurationSeconds);
            }
        }

        private int maxPotionDurationSeconds() {
            CreationSyncPayload.ClientPotionEntry potion = ClientCreationState.findPotion(this.selectedEffectId);
            return potion == null ? 480 : Math.max(1, potion.maxDurationSeconds());
        }

        private static Integer parseTimeInput(String raw) {
            if (raw == null) {
                return null;
            }
            String text = raw.trim();
            if (text.isEmpty()) {
                return null;
            }
            int colon = text.indexOf(':');
            if (colon >= 0) {
                if (text.indexOf(':', colon + 1) >= 0) {
                    return null;
                }
                String minPart = text.substring(0, colon);
                String secPart = text.substring(colon + 1);
                if (!isDigits(minPart) || !isDigits(secPart) || secPart.length() > 2) {
                    return null;
                }
                int minutes = minPart.isEmpty() ? 0 : Integer.parseInt(minPart);
                int seconds = secPart.isEmpty() ? 0 : Integer.parseInt(secPart);
                if (seconds > 59) {
                    return null;
                }
                return minutes * 60 + seconds;
            }
            if (!isDigits(text) || text.length() > 4) {
                return null;
            }
            if (text.length() <= 2) {
                return Integer.parseInt(text);
            }
            int value = Integer.parseInt(text);
            int seconds = value % 100;
            int minutes = value / 100;
            if (seconds > 59) {
                return null;
            }
            return minutes * 60 + seconds;
        }

        private static boolean isDigits(String text) {
            for (int i = 0; i < text.length(); i++) {
                char character = text.charAt(i);
                if (character < '0' || character > '9') {
                    return false;
                }
            }
            return true;
        }

        private static String formatTime(int totalSeconds) {
            int clamped = Math.max(0, totalSeconds);
            return String.format("%d:%02d", clamped / 60, clamped % 60);
        }

        private static String roman(int value) {
            return switch (value) {
                case 1 -> "I";
                case 2 -> "II";
                case 3 -> "III";
                case 4 -> "IV";
                case 5 -> "V";
                default -> String.valueOf(value);
            };
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
