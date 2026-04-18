package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.resources.Identifier;
import net.threetag.palladium.client.gui.ui.component.UiComponent;
import net.threetag.palladium.client.gui.ui.component.UiComponentSerializer;

public class YhaUiComponentSerializers {

    public static final UiComponentSerializer<UpgradePointUiComponent> UPGRADE_POINTS = register("upgrade_points", new UpgradePointUiComponent.Serializer());
    public static final UiComponentSerializer<BodyDisplayBarUiComponent> BODY_DISPLAY_BAR = register("body_display_bar", new BodyDisplayBarUiComponent.Serializer());
    public static final UiComponentSerializer<PlayerAttributeValueUiComponent> PLAYER_ATTRIBUTE_VALUE = register("player_attribute_value", new PlayerAttributeValueUiComponent.Serializer());
    public static final UiComponentSerializer<StaminaBarUiComponent> STAMINA_BAR = register("stamina_bar", new StaminaBarUiComponent.Serializer());
    public static final UiComponentSerializer<VerticalSegmentBarUiComponent> VERTICAL_SEGMENT_BAR = register("vertical_segment_bar", new VerticalSegmentBarUiComponent.Serializer());
    public static final UiComponentSerializer<AnchoredPowerTreeUiComponent> ANCHORED_POWER_TREE = register("anchored_power_tree", new AnchoredPowerTreeUiComponent.Serializer());

    private static <T extends UiComponent> UiComponentSerializer<T> register(String id, UiComponentSerializer<T> serializer) {
        UiComponentSerializer.register(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, id), serializer);
        return serializer;
    }

    public static void init() {
    }
}
