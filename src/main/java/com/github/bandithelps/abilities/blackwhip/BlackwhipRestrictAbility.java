package com.github.bandithelps.abilities.blackwhip;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.utils.blackwhip.BlackwhipTagStore;
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
 * "Leash": while held, keeps tagged entities within a soft spring radius of the owner. Entities that
 * stray past the leash length are gently reeled back, without the rigid snapping of the legacy clamp.
 */
public class BlackwhipRestrictAbility extends Ability {

    public static final MapCodec<BlackwhipRestrictAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("leash_length", new StaticValue(6.0f)).forGetter((ab) -> ab.leashLength),
                    Value.CODEC.optionalFieldOf("strength", new StaticValue(1.0f)).forGetter((ab) -> ab.strength),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipRestrictAbility::new));

    public final Value leashLength;
    public final Value strength;

    public BlackwhipRestrictAbility(Value leashLength, Value strength, AbilityProperties properties,
                                   AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.leashLength = leashLength;
        this.strength = strength;
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player) {
            DataContext context = DataContext.forEntity(entity);
            double leash = this.leashLength.getAsFloat(context);
            double strength = Math.max(0.0, this.strength.getAsFloat(context));

            for (LivingEntity target : BlackwhipTagStore.getTaggedEntities(player)) {
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
        return AbilityRegister.BLACKWHIP_RESTRICT.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipRestrictAbility> {
        public MapCodec<BlackwhipRestrictAbility> codec() {
            return BlackwhipRestrictAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipRestrictAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("While held, softly reels tagged entities back whenever they move past the leash length from the owner.")
                    .add("leash_length", TYPE_VALUE, "Maximum slack distance before a tagged entity is reeled in.")
                    .add("strength", TYPE_VALUE, "How strongly the leash pulls entities back.")
                    .addExampleObject(new BlackwhipRestrictAbility(new StaticValue(6.0f), new StaticValue(1.0f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
