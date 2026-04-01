package com.github.bandithelps.entities;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEntities {

    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(YourHeroAcademia.MODID);

    public static final Supplier<EntityType<PotionEffectGeneratorEntity>> POTION_GENERATOR = ENTITY_TYPES.register(
            "potion_generator",
            () -> EntityType.Builder.of(
                    PotionEffectGeneratorEntity::new,
                    MobCategory.MISC
            )
                    .noSave()
                    .immuneTo(Blocks.POWDER_SNOW, Blocks.WITHER_ROSE, Blocks.SWEET_BERRY_BUSH, Blocks.CACTUS)
                    .clientTrackingRange(16)
                    .updateInterval(3)
            .build(ResourceKey.create(
                    Registries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "potion_generator")
            ))
    );

}
