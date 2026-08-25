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

    public static final Supplier<EntityType<RgbaDisplayEntity>> RGBA_DISPLAY = ENTITY_TYPES.register(
            "rgba_display",
            () -> EntityType.Builder.of(
                            RgbaDisplayEntity::new,
                            MobCategory.MISC
                    )
                    .noSave()
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build(ResourceKey.create(
                            Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "rgba_display")
                    ))
    );

    public static final Supplier<EntityType<BlackwhipEntity>> BLACKWHIP = ENTITY_TYPES.register(
            "blackwhip",
            () -> EntityType.Builder.of(
                            BlackwhipEntity::new,
                            MobCategory.MISC
                    )
                    .noSave()
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(96)
                    .updateInterval(1)
                    .build(ResourceKey.create(
                            Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip")
                    ))
    );

    public static final Supplier<EntityType<BlackwhipChainEntity>> BLACKWHIP_CHAIN = ENTITY_TYPES.register(
            "blackwhip_chain",
            () -> EntityType.Builder.of(
                            BlackwhipChainEntity::new,
                            MobCategory.MISC
                    )
                    .noSave()
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(96)
                    .updateInterval(1)
                    .build(ResourceKey.create(
                            Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip_chain")
                    ))
    );

    public static final Supplier<EntityType<BlackwhipSegmentEntity>> BLACKWHIP_SEGMENT = ENTITY_TYPES.register(
            "blackwhip_segment",
            () -> EntityType.Builder.of(
                            BlackwhipSegmentEntity::new,
                            MobCategory.MISC
                    )
                    .noSave()
                    .sized(0.35f, 0.35f)
                    .clientTrackingRange(96)
                    .updateInterval(1)
                    .build(ResourceKey.create(
                            Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip_segment")
                    ))
    );

    public static final Supplier<EntityType<SmokeCanisterProjectileEntity>> SMOKE_CANISTER_PROJECTILE = ENTITY_TYPES.register(
            "smoke_canister_projectile",
            () -> EntityType.Builder.<SmokeCanisterProjectileEntity>of(
                            SmokeCanisterProjectileEntity::new,
                            MobCategory.MISC
                    )
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .build(ResourceKey.create(
                            Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "smoke_canister_projectile")
                    ))
    );

    public static final Supplier<EntityType<BlackwhipTossedBlockEntity>> BLACKWHIP_TOSSED_BLOCK = ENTITY_TYPES.register(
            "blackwhip_tossed_block",
            () -> EntityType.Builder.<BlackwhipTossedBlockEntity>of(
                            BlackwhipTossedBlockEntity::new,
                            MobCategory.MISC
                    )
                    .sized(0.98f, 0.98f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(ResourceKey.create(
                            Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip_tossed_block")
                    ))
    );

    public static final Supplier<EntityType<CreationProductEntity>> CREATION_PRODUCT = ENTITY_TYPES.register(
            "creation_product",
            () -> EntityType.Builder.<CreationProductEntity>of(
                            CreationProductEntity::new,
                            MobCategory.MISC
                    )
                    .noSave()
                    .sized(0.35f, 0.35f)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build(ResourceKey.create(
                            Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "creation_product")
                    ))
    );

}
