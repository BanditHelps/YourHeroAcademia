package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.client.dna_analyzer.ClientDNAAnalyzerState;
import com.github.bandithelps.client.dna_analyzer.ClientDNAAnalyzerToolState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.threetag.palladium.client.gui.ui.widget.UiWidget;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetProperties;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetSerializer;
import net.threetag.palladium.client.gui.ui.screen.UiScreen;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;

import java.util.ArrayList;
import java.util.List;

public class DnaAnalyzerToolsUiComponent extends UiWidget {
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_GAP = 5;
    private static final int INNER_PADDING = 4;

    public static final MapCodec<DnaAnalyzerToolsUiComponent> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
            Codec.STRING.optionalFieldOf("title", "").forGetter(DnaAnalyzerToolsUiComponent::getTitle),
            Codec.INT.optionalFieldOf("frame_color", 0xFF79B8FF).forGetter(DnaAnalyzerToolsUiComponent::getFrameColor),
            Codec.INT.optionalFieldOf("slot_color", 0xFF1A2532).forGetter(DnaAnalyzerToolsUiComponent::getSlotColor),
            Codec.INT.optionalFieldOf("slot_active_color", 0xFF9AD1FF).forGetter(DnaAnalyzerToolsUiComponent::getSlotActiveColor),
            Codec.INT.optionalFieldOf("text_color", 0xFFE6F2FF).forGetter(DnaAnalyzerToolsUiComponent::getTextColor),
            ToolDefinition.CODEC.listOf().optionalFieldOf("tools", List.of(defaultRenameTool())).forGetter(DnaAnalyzerToolsUiComponent::getTools),
            propertiesCodec(40, 165)
    ).apply(instance, DnaAnalyzerToolsUiComponent::new));

    private final String title;
    private final int frameColor;
    private final int slotColor;
    private final int slotActiveColor;
    private final int textColor;
    private final List<ToolDefinition> tools;

    public DnaAnalyzerToolsUiComponent(
            String title,
            int frameColor,
            int slotColor,
            int slotActiveColor,
            int textColor,
            List<ToolDefinition> tools,
            UiWidgetProperties properties
    ) {
        super(properties);
        this.title = title == null ? "" : title;
        this.frameColor = withOpaqueAlpha(frameColor);
        this.slotColor = withOpaqueAlpha(slotColor);
        this.slotActiveColor = withOpaqueAlpha(slotActiveColor);
        this.textColor = withOpaqueAlpha(textColor);
        this.tools = sanitizeTools(tools);
    }

    @Override
    public UiWidgetSerializer<?> getSerializer() {
        return YhaUiComponentSerializers.DNA_ANALYZER_TOOLS;
    }

    @Override
    public AbstractWidget buildWidget(UiScreen screen, ScreenRectangle rectangle, DataContext context) {
        return new DnaAnalyzerToolsWidget(this, this.getX(rectangle, context), this.getY(rectangle, context), this.getWidth(context), this.getHeight(context));
    }

    public String getTitle() {
        return this.title;
    }

    public int getFrameColor() {
        return this.frameColor;
    }

    public int getSlotColor() {
        return this.slotColor;
    }

    public int getSlotActiveColor() {
        return this.slotActiveColor;
    }

    public int getTextColor() {
        return this.textColor;
    }

    public List<ToolDefinition> getTools() {
        return this.tools;
    }

    private static List<ToolDefinition> sanitizeTools(List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return defaultTools();
        }
        List<ToolDefinition> result = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            if (tool == null) {
                continue;
            }
            String id = tool.id() == null ? "" : tool.id().trim();
            if (id.isEmpty()) {
                continue;
            }
            String label = tool.label() == null ? "" : tool.label();
            String icon = tool.icon() == null ? "" : tool.icon();
            String tooltip = tool.tooltip() == null || tool.tooltip().isBlank() ? label : tool.tooltip();
            result.add(new ToolDefinition(id, label, icon, tooltip));
        }
        if (result.isEmpty()) {
            result.addAll(defaultTools());
        }
        return List.copyOf(result);
    }

    private static List<ToolDefinition> defaultTools() {
        return List.of(defaultRenameTool(), defaultIsolateTool());
    }

    private static ToolDefinition defaultRenameTool() {
        return new ToolDefinition(ClientDNAAnalyzerToolState.TOOL_RENAME, "Rename", "minecraft:name_tag", "Rename a gene");
    }

    private static ToolDefinition defaultIsolateTool() {
        return new ToolDefinition(ClientDNAAnalyzerToolState.TOOL_ISOLATE, "Isolate", "minecraft:shears", "Extract selected gene sequence");
    }

    private static int withOpaqueAlpha(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    public record ToolDefinition(String id, String label, String icon, String tooltip) {
        public static final Codec<ToolDefinition> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(ToolDefinition::id),
                Codec.STRING.optionalFieldOf("label", "").forGetter(ToolDefinition::label),
                Codec.STRING.optionalFieldOf("icon", "").forGetter(ToolDefinition::icon),
                Codec.STRING.optionalFieldOf("tooltip", "").forGetter(ToolDefinition::tooltip)
        ).apply(instance, ToolDefinition::new));
    }

    private static final class DnaAnalyzerToolsWidget extends AbstractWidget {
        private final DnaAnalyzerToolsUiComponent owner;

        private DnaAnalyzerToolsWidget(DnaAnalyzerToolsUiComponent owner, int x, int y, int width, int height) {
            super(x, y, width, height, Component.literal(owner.title));
            this.owner = owner;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();

            gui.fill(x, y, x + width, y + height, 0xCC0E131B);
            gui.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xAA172231);
            gui.fill(x, y, x + width, y + 1, this.owner.frameColor);
            gui.fill(x, y + height - 1, x + width, y + height, this.owner.frameColor);
            gui.fill(x, y, x + 1, y + height, this.owner.frameColor);
            gui.fill(x + width - 1, y, x + width, y + height, this.owner.frameColor);

            ClientDNAAnalyzerState.ClientData analyzerState = ClientDNAAnalyzerState.getLatest();
            boolean locked = analyzerState != null && (analyzerState.processing() || analyzerState.awaitingVialCollection());
            int hoveredIndex = getToolIndexAt(mouseX, mouseY);
            for (int i = 0; i < this.owner.tools.size(); i++) {
                ToolDefinition tool = this.owner.tools.get(i);
                int buttonX = x + INNER_PADDING;
                int buttonY = y + INNER_PADDING + (i * (BUTTON_HEIGHT + BUTTON_GAP));
                int buttonW = width - (INNER_PADDING * 2);
                int buttonH = BUTTON_HEIGHT;
                boolean active = !locked && ClientDNAAnalyzerToolState.isActive(tool.id());
                boolean hovered = !locked && i == hoveredIndex;
                int borderColor = active ? 0xFFFFC857 : hovered ? this.owner.slotActiveColor : 0xFF41546B;
                int fillColor = locked ? 0xAA111820 : active ? 0xAA22415E : this.owner.slotColor;

                gui.fill(buttonX, buttonY, buttonX + buttonW, buttonY + buttonH, fillColor);
                gui.fill(buttonX, buttonY, buttonX + buttonW, buttonY + 1, borderColor);
                gui.fill(buttonX, buttonY + buttonH - 1, buttonX + buttonW, buttonY + buttonH, borderColor);
                gui.fill(buttonX, buttonY, buttonX + 1, buttonY + buttonH, borderColor);
                gui.fill(buttonX + buttonW - 1, buttonY, buttonX + buttonW, buttonY + buttonH, borderColor);

                int labelX = buttonX + 4;
                if (renderToolIcon(gui, tool, buttonX + 3, buttonY + 3)) {
                    labelX += 16;
                }

                String label = "";

                if (!tool.label().isEmpty()) {
                    label = trimToWidth(minecraft, tool.label(), buttonW - (labelX - buttonX) - 4);
                }

                gui.text(minecraft.font, label, labelX, buttonY + 7, this.owner.textColor, false);

                if (hovered) {
                    gui.setTooltipForNextFrame(minecraft.font, Component.literal(tool.tooltip()), mouseX, mouseY);
                }
            }

            String activeTool = ClientDNAAnalyzerToolState.getActiveToolId();
            if (activeTool.isBlank()) {
                this.setMessage(Component.literal("Tools: none"));
            } else {
                this.setMessage(Component.literal("Tool: " + activeTool));
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            ClientDNAAnalyzerState.ClientData analyzerState = ClientDNAAnalyzerState.getLatest();
            if (analyzerState != null && (analyzerState.processing() || analyzerState.awaitingVialCollection())) {
                return true;
            }

            int index = getToolIndexAt((int) event.x(), (int) event.y());
            if (index < 0 || index >= this.owner.tools.size()) {
                return false;
            }
            ToolDefinition tool = this.owner.tools.get(index);
            ClientDNAAnalyzerToolState.toggleTool(tool.id());
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            narrationElementOutput.add(NarratedElementType.TITLE, this.getMessage());
        }

        private int getToolIndexAt(int mouseX, int mouseY) {
            int x = this.getX() + INNER_PADDING;
            int y = this.getY() + INNER_PADDING;
            int width = this.getWidth() - (INNER_PADDING * 2);
            for (int i = 0; i < this.owner.tools.size(); i++) {
                int buttonY = y + (i * (BUTTON_HEIGHT + BUTTON_GAP));
                if (mouseX >= x && mouseX < x + width && mouseY >= buttonY && mouseY < buttonY + BUTTON_HEIGHT) {
                    return i;
                }
            }
            return -1;
        }

        private static boolean renderToolIcon(GuiGraphicsExtractor gui, ToolDefinition tool, int x, int y) {
            if (tool.icon() == null || tool.icon().isBlank()) {
                return false;
            }
            Identifier iconId;
            try {
                iconId = Identifier.parse(tool.icon());
            } catch (Exception ignored) {
                return false;
            }
            if (!BuiltInRegistries.ITEM.containsKey(iconId)) {
                return false;
            }
            var item = BuiltInRegistries.ITEM.get(iconId);
            if (item.isEmpty()) {
                return false;
            }
            gui.item(new ItemStack(item.get()), x, y);
            return true;
        }

        private static String trimToWidth(Minecraft minecraft, String value, int maxWidth) {
            if (value == null || value.isBlank()) {
                return "";
            }
            if (minecraft.font.width(value) <= maxWidth) {
                return value;
            }
            String suffix = "...";
            int suffixWidth = minecraft.font.width(suffix);
            int target = Math.max(0, maxWidth - suffixWidth);
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
    }

    public static class Serializer extends UiWidgetSerializer<DnaAnalyzerToolsUiComponent> {
        @Override
        public MapCodec<DnaAnalyzerToolsUiComponent> codec() {
            return DnaAnalyzerToolsUiComponent.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<UiWidget, DnaAnalyzerToolsUiComponent> builder, HolderLookup.Provider provider) {
            builder.setName("DNA Analyzer Tools")
                    .setDescription("Renders toggleable DNA analyzer tool buttons with optional item icons.");
        }
    }
}
