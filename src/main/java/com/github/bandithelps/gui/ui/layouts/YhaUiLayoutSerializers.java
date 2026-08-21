package com.github.bandithelps.gui.ui.layouts;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.resources.Identifier;
import net.threetag.palladium.client.gui.ui.layout.UiLayout;
import net.threetag.palladium.client.gui.ui.layout.UiLayoutSerializer;

public class YhaUiLayoutSerializers {

    public static final UiLayoutSerializer<AnchoredMultiColumnLayout> ANCHORED_MULTI_COLUMN =
            register("anchored_multi_column", new AnchoredMultiColumnLayout.Serializer());

    private static <T extends UiLayout> UiLayoutSerializer<T> register(String id, UiLayoutSerializer<T> serializer) {
        UiLayoutSerializer.register(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, id), serializer);
        return serializer;
    }

    public static void init() {
        YourHeroAcademia.LOGGER.info("Registered YHA UI layouts: yha:anchored_multi_column");
    }
}
