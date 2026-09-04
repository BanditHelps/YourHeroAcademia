package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.creation.CreationCatalog;
import com.github.bandithelps.creation.CreationEnchantments;
import com.github.bandithelps.creation.CreationKnowledgeRecipe;
import com.github.bandithelps.creation.CreationPotions;
import com.github.bandithelps.creation.CreationUtil;
import com.github.bandithelps.items.BookOfKnowledgeItem;
import com.github.bandithelps.network.BookOfKnowledgeSelectPayload;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.threetag.palladium.client.gui.ui.screen.UiScreen;
import net.threetag.palladium.client.gui.ui.widget.UiWidget;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetProperties;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetSerializer;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;

public class BookOfKnowledgeUiComponent extends UiWidget {
    private static final int SLOT = 22;
    private static final int ICON = 16;
    private static final int GAP = 8;

    public static final MapCodec<BookOfKnowledgeUiComponent> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("frame_color", 0xFF79B8FF).forGetter(BookOfKnowledgeUiComponent::getFrameColor),
            Codec.INT.optionalFieldOf("panel_color", 0xCC172231).forGetter(BookOfKnowledgeUiComponent::getPanelColor),
            Codec.INT.optionalFieldOf("text_color", 0xFFE6F2FF).forGetter(BookOfKnowledgeUiComponent::getTextColor),
            propertiesCodec(168, 72)
    ).apply(instance, BookOfKnowledgeUiComponent::new));

    private final int frameColor;
    private final int panelColor;
    private final int textColor;

    public BookOfKnowledgeUiComponent(int frameColor, int panelColor, int textColor, UiWidgetProperties properties) {
        super(properties);
        this.frameColor = withOpaqueAlpha(frameColor);
        this.panelColor = withOpaqueAlpha(panelColor);
        this.textColor = withOpaqueAlpha(textColor);
    }

    @Override
    public UiWidgetSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.BOOK_OF_KNOWLEDGE;
    }

    @Override
    public AbstractWidget buildWidget(UiScreen screen, ScreenRectangle rectangle, DataContext context) {
        return new BookWidget(this, this.getX(rectangle, context), this.getY(rectangle, context), this.getWidth(context), this.getHeight(context));
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

    private static final class BookWidget extends AbstractWidget {
        private final BookOfKnowledgeUiComponent owner;

        private BookWidget(BookOfKnowledgeUiComponent owner, int x, int y, int width, int height) {
            super(x, y, width, height, Component.translatable("gui.yha.creation.book.title"));
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
            gui.text(minecraft.font, Component.translatable("gui.yha.creation.book.title"), x + 8, y + 8, this.owner.textColor, false);
            gui.text(minecraft.font, Component.translatable("gui.yha.creation.book.hint"), x + 8, y + 20, 0xFFB8C8D8, false);

            List<CreationKnowledgeRecipe> choices = heldChoices(minecraft);
            int slotsX = slotsX(choices.size());
            int slotsY = y + 36;
            HolderLookup.Provider access = minecraft.level == null ? null : minecraft.level.registryAccess();
            for (int i = 0; i < choices.size(); i++) {
                int slotX = slotsX + i * (SLOT + GAP);
                boolean hovered = contains(mouseX, mouseY, slotX, slotsY, SLOT, SLOT);
                drawFrame(gui, slotX, slotsY, SLOT, SLOT, hovered ? 0xAA2A3F54 : 0xAA182533, 0xFF3B5A78);
                ItemStack stack = preview(choices.get(i), access);
                if (!stack.isEmpty()) {
                    gui.item(stack, slotX + (SLOT - ICON) / 2, slotsY + (SLOT - ICON) / 2);
                }
                if (hovered) {
                    gui.setTooltipForNextFrame(minecraft.font, displayName(choices.get(i), access, stack), mouseX, mouseY);
                }
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != 0) {
                return false;
            }
            Minecraft minecraft = Minecraft.getInstance();
            List<CreationKnowledgeRecipe> choices = heldChoices(minecraft);
            int slotsX = slotsX(choices.size());
            int slotsY = this.getY() + 36;
            int mouseX = (int) event.x();
            int mouseY = (int) event.y();
            for (int i = 0; i < choices.size(); i++) {
                int slotX = slotsX + i * (SLOT + GAP);
                if (!contains(mouseX, mouseY, slotX, slotsY, SLOT, SLOT)) {
                    continue;
                }
                ClientPacketDistributor.sendToServer(new BookOfKnowledgeSelectPayload(i));
                if (minecraft.getSoundManager() != null) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                }
                return true;
            }
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            narrationElementOutput.add(NarratedElementType.TITLE, this.getMessage());
        }

        private int slotsX(int count) {
            int totalW = Math.max(0, count) * SLOT + Math.max(0, count - 1) * GAP;
            return this.getX() + Math.max(8, (this.getWidth() - totalW) / 2);
        }

        private static List<CreationKnowledgeRecipe> heldChoices(Minecraft minecraft) {
            if (minecraft.player == null) {
                return List.of();
            }
            return BookOfKnowledgeItem.getChoices(BookOfKnowledgeItem.findHeld(minecraft.player));
        }

        private static ItemStack preview(CreationKnowledgeRecipe recipe, HolderLookup.Provider access) {
            if (recipe == null || recipe.id() == null) {
                return ItemStack.EMPTY;
            }
            return switch (recipe.kind()) {
                case ENCHANT -> CreationEnchantments.bookPreview(access, recipe.id(), 1);
                case POTION -> CreationPotions.previewStack(recipe.id());
                default -> CreationCatalog.stackOf(recipe.id());
            };
        }

        private static Component displayName(CreationKnowledgeRecipe recipe, HolderLookup.Provider access, ItemStack stack) {
            if (recipe == null) {
                return Component.empty();
            }
            return switch (recipe.kind()) {
                case ENCHANT -> CreationEnchantments.displayName(access, recipe.id());
                case POTION -> CreationUtil.potionDisplayName(recipe.id());
                default -> stack.isEmpty() ? Component.literal(recipe.id().toString()) : stack.getHoverName();
            };
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
    }

    public static class Serializer extends UiWidgetSerializer<BookOfKnowledgeUiComponent> {
        @Override
        public MapCodec<BookOfKnowledgeUiComponent> codec() {
            return CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiWidget, BookOfKnowledgeUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("Book of Knowledge").setDescription("Lets a Creation user pick one locked recipe to learn.");
        }
    }
}
