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

public final class IntelligenceAttributes {

    public static final double INTELLIGENCE_DEFAULT = 0.0D;
    public static final double INTELLIGENCE_MIN = 0.0D;
    public static final double INTELLIGENCE_MAX = 100.0D;

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, YourHeroAcademia.MODID);

    public static final DeferredHolder<Attribute, Attribute> INTELLIGENCE = ATTRIBUTES.register(
            "intelligence",
            () -> new RangedAttribute(
                    "attribute.yha.intelligence",
                    INTELLIGENCE_DEFAULT,
                    INTELLIGENCE_MIN,
                    INTELLIGENCE_MAX
            ).setSyncable(true)
    );

    private IntelligenceAttributes() {
    }

    @EventBusSubscriber(modid = YourHeroAcademia.MODID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
            event.add(EntityType.PLAYER, INTELLIGENCE);
        }
    }
}