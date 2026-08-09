package com.github.bandithelps.abilities.blackwhip.chain;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.network.BlackwhipChainLeadPayload;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainLeadPhysics;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainTagStore;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
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
 * "Lead": toggle that locks latched chain tether length at the current owner↔target distance
 * (including slack). Past that length, soft-springs the weaker side — oversized / stronger
 * targets can drag the owner along the lead.
 */
public class BlackwhipChainRestrictAbility extends Ability {

    public static final MapCodec<BlackwhipChainRestrictAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("strength", new StaticValue(1.0f)).forGetter((ab) -> ab.strength),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipChainRestrictAbility::new));

    public final Value strength;

    public BlackwhipChainRestrictAbility(Value strength, AbilityProperties properties,
                                         AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.strength = strength;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player) {
            BlackwhipChainTagStore.setLeadActive(player, true);
            PacketDistributor.sendToPlayer(player, new BlackwhipChainLeadPayload(true));
        }
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player) {
            BlackwhipChainTagStore.setLeadActive(player, false);
            PacketDistributor.sendToPlayer(player, new BlackwhipChainLeadPayload(false));
        }
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player) {
            DataContext context = DataContext.forEntity(entity);
            double springStrength = Math.max(0.0, this.strength.getAsFloat(context));

            for (LivingEntity target : BlackwhipChainTagStore.getTaggedEntities(player)) {
                if (BlackwhipChainTagStore.isPuppeted(player, target.getId())) {
                    continue;
                }
                BlackwhipChainEntity chain = BlackwhipChainTagStore.getChainForTarget(player, target.getId());
                if (chain == null || !chain.isLengthLocked()) {
                    continue;
                }
                BlackwhipChainLeadPhysics.applyTautContest(
                        player, target, chain.getLockedLeashLength(), springStrength,
                        BlackwhipChainLeadPhysics.DEFAULT_REFERENCE_VOLUME);
            }
        }
        return super.tick(entity, abilityInstance, enabled);
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_CHAIN_RESTRICT.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipChainRestrictAbility> {
        public MapCodec<BlackwhipChainRestrictAbility> codec() {
            return BlackwhipChainRestrictAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipChainRestrictAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Toggle that locks tether length. Soft-reels weaker tagged entities; stronger/heavier ones can drag the owner.")
                    .add("strength", TYPE_VALUE, "How strongly the lead reels entities when the owner wins the contest.")
                    .addExampleObject(new BlackwhipChainRestrictAbility(new StaticValue(1.0f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
