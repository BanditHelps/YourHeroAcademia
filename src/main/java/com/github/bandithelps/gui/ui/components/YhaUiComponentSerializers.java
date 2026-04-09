package com.github.bandithelps.gui.ui.components;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.resources.Identifier;
import net.threetag.palladium.client.gui.ui.component.UiComponent;
import net.threetag.palladium.client.gui.ui.component.UiComponentSerializer;

public class YhaUiComponentSerializers {

    public static final UiComponentSerializer<UpgradePointUiComponent> UPGRADE_POINTS = register("upgrade_points", new UpgradePointUiComponent.Serializer());

    private static <T extends UiComponent> UiComponentSerializer<T> register(String id, UiComponentSerializer<T> serializer) {
        UiComponentSerializer.register(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, id), serializer);
        return serializer;
    }

    public static void init() {
    }
}
