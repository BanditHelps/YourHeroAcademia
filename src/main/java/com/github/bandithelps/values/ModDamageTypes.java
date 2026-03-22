package com.github.bandithelps.values;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

public class ModDamageTypes {

    public static final ResourceKey<DamageType> EXHAUSTION_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "exhaustion"));

    public static void applyExhaustionDamage(ServerPlayer player, float damage) {
        DamageSource exhaustionDamage = new DamageSource(player.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageTypes.EXHAUSTION_DAMAGE));
        player.hurt(exhaustionDamage, damage);
    }

}
