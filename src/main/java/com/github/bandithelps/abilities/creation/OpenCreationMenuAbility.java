package com.github.bandithelps.abilities.creation;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.capabilities.body.BodySyncEvents;
import com.github.bandithelps.capabilities.creation.CreationSyncEvents;
import com.github.bandithelps.creation.CreationCatalog;
import com.github.bandithelps.creation.CreationUtil;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.network.OpenScreenPacket;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

public class OpenCreationMenuAbility extends Ability {
    public static final Identifier SCREEN_ID =
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "power/creation_notebook");

    public static final MapCodec<OpenCreationMenuAbility> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()
            ).apply(instance, OpenCreationMenuAbility::new));

    public OpenCreationMenuAbility(AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        if (!CreationUtil.hasCreation(player)) {
            return;
        }
        CreationCatalog.getInstance().rebuildResolved();
        CreationSyncEvents.syncNow(player);
        BodySyncEvents.syncNow(player);
        PacketDistributor.sendToPlayer(player, new OpenScreenPacket(SCREEN_ID));
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.OPEN_CREATION_MENU.get();
    }

    public static class Serializer extends AbilitySerializer<OpenCreationMenuAbility> {
        @Override
        public MapCodec<OpenCreationMenuAbility> codec() {
            return CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<Ability, OpenCreationMenuAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Opens the Creation notebook GUI.")
                    .addExampleObject(new OpenCreationMenuAbility(AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
