package com.github.bandithelps.abilities.blackwhip.chain;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainTagStore;
import com.github.bandithelps.utils.blackwhip.BlackwhipTargeting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.logic.value.StaticValue;
import net.threetag.palladium.logic.value.Value;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.Collections;
import java.util.List;

/**
 * Releases chain-Blackwhip tethers.
 */
public class BlackwhipChainDetachAbility extends Ability {

    public static final MapCodec<BlackwhipChainDetachAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(20.0f)).forGetter((ab) -> ab.range),
                    Codec.BOOL.optionalFieldOf("all", false).forGetter((ab) -> ab.all),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipChainDetachAbility::new));

    public final Value range;
    public final boolean all;

    public BlackwhipChainDetachAbility(Value range, boolean all, AbilityProperties properties,
                                       AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.all = all;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        DataContext context = DataContext.forEntity(entity);

        if (!this.all) {
            LivingEntity target = BlackwhipTargeting.raycastLiving(player, this.range.getAsFloat(context));
            if (target != null && BlackwhipChainTagStore.isTagged(player, target.getId())) {
                BlackwhipChainTagStore.removeTag(player, target.getId());
                level.playSound(null, player.blockPosition(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 0.6f, 1.0f);
                return;
            }
        }

        boolean releasedTags = BlackwhipChainTagStore.getTagCount(player) > 0;
        if (releasedTags) {
            BlackwhipChainTagStore.clearTags(player);
        }

        // Also drop movement ropes (swing / zip) that are not living TagStore entries.
        BlackwhipChainSwingAbility.forceStop(player);
        BlackwhipChainZipAbility.forceStop(player);
        BlackwhipChainEntity.retractOwnedByPurpose(player.getId(),
                BlackwhipChainEntity.PURPOSE_SWING,
                BlackwhipChainEntity.PURPOSE_ZIP_SIMPLE,
                BlackwhipChainEntity.PURPOSE_ZIP_CHARGE);

        if (releasedTags || this.all) {
            level.playSound(null, player.blockPosition(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 0.8f, 0.8f);
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_CHAIN_DETACH.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipChainDetachAbility> {
        public MapCodec<BlackwhipChainDetachAbility> codec() {
            return BlackwhipChainDetachAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipChainDetachAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Releases chain-Blackwhip tethers. With all=true, always releases everything.")
                    .add("range", TYPE_VALUE, "Reach of the targeted-release raycast.")
                    .add("all", TYPE_BOOLEAN, "If true, always release every tether.")
                    .addExampleObject(new BlackwhipChainDetachAbility(new StaticValue(20.0f), false,
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
