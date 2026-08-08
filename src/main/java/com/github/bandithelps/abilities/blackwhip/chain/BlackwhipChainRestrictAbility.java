package com.github.bandithelps.abilities.blackwhip.chain;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainTagStore;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
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
 * (including slack) and soft-springs tagged entities back when they stray past that length.
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
        }
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player) {
            BlackwhipChainTagStore.setLeadActive(player, false);
        }
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player) {
            DataContext context = DataContext.forEntity(entity);
            double strength = Math.max(0.0, this.strength.getAsFloat(context));

            for (LivingEntity target : BlackwhipChainTagStore.getTaggedEntities(player)) {
                BlackwhipChainEntity chain = BlackwhipChainTagStore.getChainForTarget(player, target.getId());
                if (chain == null || !chain.isLengthLocked()) {
                    continue;
                }
                double leash = chain.getLockedLeashLength();
                Vec3 toOwner = player.position().subtract(target.position());
                double dist = toOwner.length();
                if (dist > leash && dist > 1.0e-3) {
                    Vec3 dir = toOwner.scale(1.0 / dist);
                    double over = dist - leash;
                    Vec3 pull = dir.scale(Math.min(over * 0.2 * strength, 1.2));
                    target.setDeltaMovement(target.getDeltaMovement().scale(0.6).add(pull));
                    target.hurtMarked = true;
                    target.fallDistance = 0;
                }
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
            builder.setDescription("Toggle that locks latched chain tether length at the current slack and softly reels tagged entities back past that length.")
                    .add("strength", TYPE_VALUE, "How strongly the lead pulls entities back when past the locked length.")
                    .addExampleObject(new BlackwhipChainRestrictAbility(new StaticValue(1.0f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
