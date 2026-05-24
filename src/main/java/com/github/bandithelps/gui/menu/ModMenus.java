package com.github.bandithelps.gui.menu;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, YourHeroAcademia.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<GeneCombinerMenu>> GENE_COMBINER =
            MENUS.register("gene_combiner", () -> new MenuType<>(GeneCombinerMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {
    }
}
