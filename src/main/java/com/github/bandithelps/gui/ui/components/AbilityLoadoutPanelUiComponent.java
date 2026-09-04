package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.client.loadout.ClientAbilityLoadoutState;
import com.github.bandithelps.capabilities.loadout.AbilityLoadoutData;
import com.github.bandithelps.network.AbilityLoadoutAssignPayload;
import com.github.bandithelps.utils.loadout.AbilityLoadoutUtil;
import com.github.bandithelps.utils.loadout.AbilityLoadoutUtil.CatalogEntry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.threetag.palladium.client.gui.ui.screen.UiScreen;
import net.threetag.palladium.client.gui.ui.widget.UiWidget;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetProperties;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetSerializer;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.power.ability.AbilityReference;

public class AbilityLoadoutPanelUiComponent extends UiWidget {
    public static final MapCodec<AbilityLoadoutPanelUiComponent> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("frame_color", 0xFF79B8FF).forGetter(AbilityLoadoutPanelUiComponent::getFrameColor),
            Codec.INT.optionalFieldOf("panel_color", 0xCC172231).forGetter(AbilityLoadoutPanelUiComponent::getPanelColor),
            Codec.INT.optionalFieldOf("text_color", 0xFFE6F2FF).forGetter(AbilityLoadoutPanelUiComponent::getTextColor),
            propertiesCodec(366, 190)
    ).apply(instance, AbilityLoadoutPanelUiComponent::new));

    private final int frameColor;
    private final int panelColor;
    private final int textColor;

    public AbilityLoadoutPanelUiComponent(int frameColor, int panelColor, int textColor, UiWidgetProperties properties) {
        super(properties);
        this.frameColor = withOpaqueAlpha(frameColor);
        this.panelColor = withOpaqueAlpha(panelColor);
        this.textColor = withOpaqueAlpha(textColor);
    }

    @Override
    public UiWidgetSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.ABILITY_LOADOUT_PANEL;
    }

    @Override
    public AbstractWidget buildWidget(UiScreen screen, ScreenRectangle rectangle, DataContext context) {
        return new LoadoutWidget(this, this.getX(rectangle, context), this.getY(rectangle, context), this.getWidth(context), this.getHeight(context));
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

    private static final class LoadoutWidget extends AbstractWidget {
        private static final int LIST_WIDTH = 210;
        private static final int ROW_HEIGHT = 14;
        private static final int SLOT_HEIGHT = 26;

        private final AbilityLoadoutPanelUiComponent owner;
        private int scrollOffset;
        private AbilityReference selected;

        private LoadoutWidget(AbilityLoadoutPanelUiComponent owner, int x, int y, int width, int height) {
            super(x, y, width, height, Component.literal("Ability Loadout"));
            this.owner = owner;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();

            drawFrame(gui, x, y, width, height, this.owner.panelColor, this.owner.frameColor);

            List<CatalogEntry> entries = AbilityLoadoutUtil.collectAssignableAbilities(minecraft.player);
            int listX = x + 6;
            int listY = y + 18;
            int listH = height - 28;
            drawFrame(gui, listX, listY, LIST_WIDTH, listH, 0xAA111A26, this.owner.frameColor);
            gui.text(minecraft.font, "Unlocked abilities", listX + 4, y + 6, this.owner.textColor, false);

            int visibleRows = Math.max(1, (listH - 4) / ROW_HEIGHT);
            int maxScroll = Math.max(0, entries.size() - visibleRows);
            this.scrollOffset = Math.min(this.scrollOffset, maxScroll);
            int start = this.scrollOffset;
            int end = Math.min(entries.size(), start + visibleRows);
            for (int i = start; i < end; i++) {
                CatalogEntry entry = entries.get(i);
                int rowY = listY + 2 + ((i - start) * ROW_HEIGHT);
                boolean hovered = contains(mouseX, mouseY, listX + 2, rowY, LIST_WIDTH - 4, ROW_HEIGHT - 1);
                boolean selectedRow = entry.reference().equals(this.selected);
                int fill = selectedRow ? 0xAA2A5A7A : (hovered ? 0xAA1C3D54 : 0x00000000);
                if (fill != 0) {
                    gui.fill(listX + 2, rowY, listX + LIST_WIDTH - 2, rowY + ROW_HEIGHT - 1, fill);
                }
                String label = entry.powerName().getString() + " / " + entry.abilityName().getString();
                gui.text(minecraft.font, trim(minecraft, label, LIST_WIDTH - 10), listX + 5, rowY + 2, this.owner.textColor, false);
            }

            int slotX = listX + LIST_WIDTH + 8;
            int slotW = width - (slotX - x) - 6;
            gui.text(minecraft.font, "Hotbar slots", slotX, y + 6, this.owner.textColor, false);
            for (int slot = 0; slot < AbilityLoadoutData.SLOT_COUNT; slot++) {
                int slotY = listY + (slot * (SLOT_HEIGHT + 4));
                AbilityReference assigned = ClientAbilityLoadoutState.getSlot(slot);
                boolean hovered = contains(mouseX, mouseY, slotX, slotY, slotW, SLOT_HEIGHT);
                int fill = hovered ? 0xAA1C3D54 : 0xAA111A26;
                drawFrame(gui, slotX, slotY, slotW, SLOT_HEIGHT, fill, this.owner.frameColor);
                gui.text(minecraft.font, String.valueOf(slot + 1), slotX + 4, slotY + 8, 0xFF79B8FF, false);
                gui.text(minecraft.font, trim(minecraft, slotLabel(minecraft.player, assigned), slotW - 18), slotX + 16, slotY + 8, this.owner.textColor, false);
            }

            if (contains(mouseX, mouseY, listX, listY, LIST_WIDTH, listH)) {
                int row = (mouseY - listY - 2) / ROW_HEIGHT;
                int index = this.scrollOffset + row;
                if (index >= 0 && index < entries.size()) {
                    CatalogEntry entry = entries.get(index);
                    gui.setTooltipForNextFrame(minecraft.font, Component.literal(
                            entry.powerName().getString() + " - " + entry.abilityName().getString()
                                    + " (index " + entry.listIndex() + ")"
                    ).withStyle(ChatFormatting.AQUA), mouseX, mouseY);
                }
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != 0) {
                return false;
            }
            Minecraft minecraft = Minecraft.getInstance();
            int mouseX = (int) event.x();
            int mouseY = (int) event.y();
            int x = this.getX();
            int y = this.getY();
            int listX = x + 6;
            int listY = y + 18;
            int listH = this.getHeight() - 28;

            List<CatalogEntry> entries = AbilityLoadoutUtil.collectAssignableAbilities(minecraft.player);
            if (contains(mouseX, mouseY, listX, listY, LIST_WIDTH, listH)) {
                int visibleRows = Math.max(1, (listH - 4) / ROW_HEIGHT);
                int row = (mouseY - listY - 2) / ROW_HEIGHT;
                int index = this.scrollOffset + row;
                if (row >= 0 && row < visibleRows && index >= 0 && index < entries.size()) {
                    this.selected = entries.get(index).reference();
                    clickSound();
                    return true;
                }
            }

            int slotX = listX + LIST_WIDTH + 8;
            int slotW = this.getWidth() - (slotX - x) - 6;
            for (int slot = 0; slot < AbilityLoadoutData.SLOT_COUNT; slot++) {
                int slotY = listY + (slot * (SLOT_HEIGHT + 4));
                if (!contains(mouseX, mouseY, slotX, slotY, slotW, SLOT_HEIGHT)) {
                    continue;
                }
                if (this.selected != null) {
                    ClientPacketDistributor.sendToServer(new AbilityLoadoutAssignPayload(
                            slot,
                            AbilityLoadoutData.encodeReference(this.selected)
                    ));
                    this.selected = null;
                } else {
                    ClientPacketDistributor.sendToServer(new AbilityLoadoutAssignPayload(slot, ""));
                }
                clickSound();
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            int listX = this.getX() + 6;
            int listY = this.getY() + 18;
            int listH = this.getHeight() - 28;
            if (!contains((int) mouseX, (int) mouseY, listX, listY, LIST_WIDTH, listH)) {
                return false;
            }
            Minecraft minecraft = Minecraft.getInstance();
            List<CatalogEntry> entries = AbilityLoadoutUtil.collectAssignableAbilities(minecraft.player);
            int visibleRows = Math.max(1, (listH - 4) / ROW_HEIGHT);
            int maxScroll = Math.max(0, entries.size() - visibleRows);
            if (scrollY > 0) {
                this.scrollOffset = Math.max(0, this.scrollOffset - 1);
            } else if (scrollY < 0) {
                this.scrollOffset = Math.min(maxScroll, this.scrollOffset + 1);
            }
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            narrationElementOutput.add(NarratedElementType.TITLE, this.getMessage());
        }

        private static String slotLabel(Player player, AbilityReference reference) {
            if (reference == null) {
                return "Empty";
            }
            var instance = AbilityLoadoutUtil.resolveInstance(player, reference);
            if (instance == null) {
                return reference.abilityKey();
            }
            return AbilityLoadoutUtil.displayName(player, instance).getString();
        }

        private static String trim(Minecraft minecraft, String text, int maxWidth) {
            if (minecraft.font.width(text) <= maxWidth) {
                return text;
            }
            String ellipsis = "...";
            int budget = maxWidth - minecraft.font.width(ellipsis);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                String next = builder.toString() + text.charAt(i);
                if (minecraft.font.width(next) > budget) {
                    break;
                }
                builder.append(text.charAt(i));
            }
            return builder + ellipsis;
        }

        private static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        private static void drawFrame(GuiGraphicsExtractor gui, int x, int y, int width, int height, int fill, int border) {
            gui.fill(x, y, x + width, y + height, fill);
            gui.fill(x, y, x + width, y + 1, border);
            gui.fill(x, y + height - 1, x + width, y + height, border);
            gui.fill(x, y, x + 1, y + height, border);
            gui.fill(x + width - 1, y, x + width, y + height, border);
        }

        private static void clickSound() {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    public static class Serializer extends UiWidgetSerializer<AbilityLoadoutPanelUiComponent> {
        @Override
        public MapCodec<AbilityLoadoutPanelUiComponent> codec() {
            return AbilityLoadoutPanelUiComponent.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiWidget, AbilityLoadoutPanelUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("Ability Loadout Panel")
                    .setDescription("Assigns unlocked bar abilities from any power into five custom hotbar slots.");
        }
    }
}
