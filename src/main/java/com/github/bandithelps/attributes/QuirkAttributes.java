package com.github.bandithelps.attributes;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class QuirkAttributes {

    public static final double QUIRK_FACTOR_DEFAULT = 1.0D;
    public static final double QUIRK_FACTOR_MIN = 0.0D;
    public static final double QUIRK_FACTOR_MAX = 2048.0D;

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, YourHeroAcademia.MODID);

    public static final DeferredHolder<Attribute, Attribute> QUIRK_FACTOR = ATTRIBUTES.register(
            "quirk_factor",
            () -> new RangedAttribute(
                    "attribute.yha.quirk_factor",
                    QUIRK_FACTOR_DEFAULT,
                    QUIRK_FACTOR_MIN,
                    QUIRK_FACTOR_MAX
            ).setSyncable(true)
    );

    private QuirkAttributes() {
    }

    @EventBusSubscriber(modid = YourHeroAcademia.MODID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
            event.add(EntityType.PLAYER, QUIRK_FACTOR);
        }
    }
}
