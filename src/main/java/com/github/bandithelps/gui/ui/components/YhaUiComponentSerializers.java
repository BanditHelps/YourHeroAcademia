package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.resources.Identifier;
import net.threetag.palladium.client.gui.ui.widget.UiWidget;
import net.threetag.palladium.client.gui.ui.widget.UiWidgetSerializer;

public class YhaUiComponentSerializers {

    public static final UiWidgetSerializer<UpgradePointUiComponent> UPGRADE_POINTS = register("upgrade_points", new UpgradePointUiComponent.Serializer());
    public static final UiWidgetSerializer<BodyDisplayBarUiComponent> BODY_DISPLAY_BAR = register("body_display_bar", new BodyDisplayBarUiComponent.Serializer());
    public static final UiWidgetSerializer<PlayerAttributeValueUiComponent> PLAYER_ATTRIBUTE_VALUE = register("player_attribute_value", new PlayerAttributeValueUiComponent.Serializer());
    public static final UiWidgetSerializer<StaminaBarUiComponent> STAMINA_BAR = register("stamina_bar", new StaminaBarUiComponent.Serializer());
    public static final UiWidgetSerializer<VerticalSegmentBarUiComponent> VERTICAL_SEGMENT_BAR = register("vertical_segment_bar", new VerticalSegmentBarUiComponent.Serializer());
    public static final UiWidgetSerializer<AnchoredPowerTreeUiComponent> ANCHORED_POWER_TREE = register("anchored_power_tree", new AnchoredPowerTreeUiComponent.Serializer());
    public static final UiWidgetSerializer<DnaAnalyzerPanelUiComponent> DNA_ANALYZER_PANEL = register("dna_analyzer_panel", new DnaAnalyzerPanelUiComponent.Serializer());
    public static final UiWidgetSerializer<DnaAnalyzerInfoUiComponent> DNA_ANALYZER_INFO = register("dna_analyzer_info", new DnaAnalyzerInfoUiComponent.Serializer());
    public static final UiWidgetSerializer<DnaAnalyzerToolsUiComponent> DNA_ANALYZER_TOOLS = register("dna_analyzer_tools", new DnaAnalyzerToolsUiComponent.Serializer());
    public static final UiWidgetSerializer<GeneCombinerPanelUiComponent> GENE_COMBINER_PANEL = register("gene_combiner_panel", new GeneCombinerPanelUiComponent.Serializer());
    public static final UiWidgetSerializer<BioPrinterPanelUiComponent> BIO_PRINTER_PANEL = register("bio_printer_panel", new BioPrinterPanelUiComponent.Serializer());
    public static final UiWidgetSerializer<GeneCombinationBrowserPanelUiComponent> GENE_COMBINATION_BROWSER_PANEL =
            register("gene_combination_browser_panel", new GeneCombinationBrowserPanelUiComponent.Serializer());

    private static <T extends UiWidget> UiWidgetSerializer<T> register(String id, UiWidgetSerializer<T> serializer) {
        UiWidgetSerializer.register(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, id), serializer);
        return serializer;
    }

    public static void init() {
    }
}
