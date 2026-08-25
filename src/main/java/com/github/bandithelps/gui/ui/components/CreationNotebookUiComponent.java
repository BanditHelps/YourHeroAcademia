package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.client.body.ClientBodyState;
import com.github.bandithelps.client.creation.ClientCreationState;
import com.github.bandithelps.creation.CreationTab;
import com.github.bandithelps.creation.CreationUtil;
import com.github.bandithelps.network.CreationAssignSlotPayload;
import com.github.bandithelps.network.CreationCreatePayload;
import com.github.bandithelps.network.CreationSyncPayload;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
        private int gearPage;
        private final Map<String, String> selectedForms = new HashMap<>();
        private String formMenuParent;
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
                blit(gui, TEX_LOCKED_TAB, x + 205, y + 1, 0, 0, 16, 16, 16, 16);
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
                drawGrid(gui, x, y, ClientCreationState.entriesForTab(CreationTab.GEAR, true), GEAR_X, GEAR_Y, GEAR_COLS, GEAR_ROWS, GEAR_SLOT, this.gearPage);
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
            int selectedCost = selected == null ? 0 : selected.formCost(displayedFormId(this.selectedId));
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
            CreationSyncPayload.ClientEntry entry = formMenuEntry();
            if (entry == null) {
                return;
            }
            List<String> choices = entry.formChoices();
            int width = formMenuWidth(choices.size());
            int height = formMenuHeight();
            gui.fill(this.formMenuX, this.formMenuY, this.formMenuX + width, this.formMenuY + height, 0xF21A1410);
            drawFrame(gui, this.formMenuX, this.formMenuY, width, height, 0xFFFFD27A);
            String current = displayedFormId(this.formMenuParent);
            for (int i = 0; i < choices.size(); i++) {
                int slotX = this.formMenuX + FORM_PAD + i * FORM_SLOT;
                int slotY = this.formMenuY + FORM_PAD;
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

            if (contains(mouseX, mouseY, x + 164, y, 18, 16) || contains(mouseX, mouseY, x + 184, y, 18, 16)) {
                closeFormMenu();
                minecraft.gui.setOverlayMessage(Component.translatable("gui.yha.creation.tab_locked"), false);
                clickSound();
                return true;
            }
            if (contains(mouseX, mouseY, x + 204, y, 18, 16)) {
                closeFormMenu();
                if (!ClientCreationState.get().gearTabUnlocked()) {
                    minecraft.gui.setOverlayMessage(Component.translatable("gui.yha.creation.tab_locked"), false);
                } else {
                    this.page = Page.GEAR;
                }
                clickSound();
                return true;
            }
            if (contains(mouseX, mouseY, x + 202, y + 1, 24, 20) || contains(mouseX, mouseY, x + 220, y, 16, 16)) {
                closeFormMenu();
                this.page = Page.BLOCKS;
                clickSound();
                return true;
            }

            if (contains(mouseX, mouseY, x + CREATE_X, y + CREATE_Y, CREATE_W, CREATE_H)) {
                closeFormMenu();
                String createId = displayedFormId(this.selectedId);
                if (createId != null) {
                    ClientPacketDistributor.sendToServer(new CreationCreatePayload(createId));
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
                this.selectedId = clicked.itemId();
                if (event.button() == 1 && entry != null && entry.hasForms()) {
                    if (!entry.itemId().equals(openFormParent)) {
                        toggleFormMenu(entry, clicked);
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

        private boolean handleFormMenuClick(int mouseX, int mouseY) {
            CreationSyncPayload.ClientEntry entry = formMenuEntry();
            if (entry == null) {
                return false;
            }
            List<String> choices = entry.formChoices();
            int width = formMenuWidth(choices.size());
            int height = formMenuHeight();
            if (!contains(mouseX, mouseY, this.formMenuX, this.formMenuY, width, height)) {
                closeFormMenu();
                return false;
            }
            for (int i = 0; i < choices.size(); i++) {
                int slotX = this.formMenuX + FORM_PAD + i * FORM_SLOT;
                int slotY = this.formMenuY + FORM_PAD;
                if (contains(mouseX, mouseY, slotX, slotY, FORM_SLOT, FORM_SLOT)) {
                    this.selectedForms.put(this.formMenuParent, choices.get(i));
                    this.selectedId = this.formMenuParent;
                    closeFormMenu();
                    return true;
                }
            }
            return true;
        }

        private void toggleFormMenu(CreationSyncPayload.ClientEntry entry, SlotHit hit) {
            if (entry.itemId().equals(this.formMenuParent)) {
                closeFormMenu();
                return;
            }
            List<String> choices = entry.formChoices();
            int width = formMenuWidth(choices.size());
            int height = formMenuHeight();
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
            this.formMenuParent = entry.itemId();
            this.formMenuX = menuX;
            this.formMenuY = menuY;
            Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("gui.yha.creation.choose_form"), false);
        }

        private void closeFormMenu() {
            this.formMenuParent = null;
        }

        private CreationSyncPayload.ClientEntry formMenuEntry() {
            if (this.formMenuParent == null) {
                return null;
            }
            CreationSyncPayload.ClientEntry entry = ClientCreationState.find(this.formMenuParent);
            if (entry == null || !entry.hasForms()) {
                closeFormMenu();
                return null;
            }
            return entry;
        }

        private void selectAssigned(String assignedId) {
            CreationSyncPayload.ClientEntry parent = ClientCreationState.findParent(assignedId);
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
                int size = ClientCreationState.entriesForTab(CreationTab.GEAR, true).size();
                int perPage = GEAR_COLS * GEAR_ROWS;
                int pages = Math.max(1, ceilDiv(size, perPage));
                this.gearPage = (this.gearPage + 1) % pages;
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
                return hitGrid(mouseX, mouseY, x, y, ClientCreationState.entriesForTab(CreationTab.GEAR, true), GEAR_X, GEAR_Y, GEAR_COLS, GEAR_ROWS, GEAR_SLOT, this.gearPage, GEAR_COLS);
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

        private static int formMenuWidth(int choiceCount) {
            return Math.max(1, choiceCount) * FORM_SLOT + FORM_PAD * 2;
        }

        private static int formMenuHeight() {
            return FORM_SLOT + FORM_PAD * 2;
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
