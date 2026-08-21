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

public final class StrengthAttributes {

    public static final double STRENGTH_DEFAULT = 1.0D;
    public static final double STRENGTH_MIN = 0.0D;
    public static final double STRENGTH_MAX = 1024.0D;

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, YourHeroAcademia.MODID);

    public static final DeferredHolder<Attribute, Attribute> STRENGTH = ATTRIBUTES.register(
            "strength",
            () -> new RangedAttribute(
                    "attribute.yha.strength",
                    STRENGTH_DEFAULT,
                    STRENGTH_MIN,
                    STRENGTH_MAX
            ).setSyncable(true)
    );

    private StrengthAttributes() {
    }

    @EventBusSubscriber(modid = YourHeroAcademia.MODID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
            event.add(EntityType.PLAYER, STRENGTH);
        }
    }
}
