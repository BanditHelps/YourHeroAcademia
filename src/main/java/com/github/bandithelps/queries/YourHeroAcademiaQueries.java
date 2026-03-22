package com.github.bandithelps.queries;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.attributes.QuirkAttributes;
import com.github.bandithelps.utils.quirk.QuirkFactorUtil;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.threetag.palladium.event.RegisterMoLangQueriesEvent;
import net.threetag.palladium.logic.molang.EntityContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import team.unnamed.mocha.runtime.binding.Binding;
import team.unnamed.mocha.runtime.value.ObjectProperty;
import team.unnamed.mocha.runtime.value.ObjectValue;
import team.unnamed.mocha.runtime.value.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public class YourHeroAcademiaQueries implements ObjectValue {
    private final EntityContext context;
    private final Map<String, Supplier<Object>> functions = new HashMap();


    @SubscribeEvent
    static void registerMoLang(RegisterMoLangQueriesEvent e) {
        e.register(YourHeroAcademia.MODID, YourHeroAcademiaQueries.class, YourHeroAcademiaQueries::new);
    }

    public YourHeroAcademiaQueries(EntityContext context) {
        this.context = context;
        this.functions.put("quirk_factor", this::quirk_factor);
    }

    public @NotNull Value get(@NonNull String name) {
        return this.functions.containsKey(name) ? Value.of(((Supplier)this.functions.get(name)).get()) : Value.nil();
    }

    @Binding({"quirk_factor"})
    public double quirk_factor() {
        if (context.entity() instanceof Player player) {
            return QuirkFactorUtil.getQuirkFactor(player);
        }
        return QuirkAttributes.QUIRK_FACTOR_DEFAULT;
    }

    @Override
    public @Nullable ObjectProperty getProperty(@NotNull String name) {
        return null;
    }
}
