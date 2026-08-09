package com.github.bandithelps.abilities.blackwhip.chain;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainHelper;
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
 * Shoots a chain tip that rolls a strength contest to rip a held item into the owner's inventory.
 */
public class BlackwhipChainDisarmAbility extends Ability {

    public static final MapCodec<BlackwhipChainDisarmAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(14.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("segment_count", new StaticValue(8.0f)).forGetter((ab) -> ab.segmentCount),
                    Value.CODEC.optionalFieldOf("link_length", new StaticValue(0.85f)).forGetter((ab) -> ab.linkLength),
                    Value.CODEC.optionalFieldOf("chain_hp", new StaticValue(16.0f)).forGetter((ab) -> ab.chainHp),
                    Value.CODEC.optionalFieldOf("thickness", new StaticValue(0.9f)).forGetter((ab) -> ab.thickness),
                    Value.CODEC.optionalFieldOf("travel_ticks", new StaticValue(10.0f)).forGetter((ab) -> ab.travelTicks),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipChainDisarmAbility::new));

    public final Value range;
    public final Value segmentCount;
    public final Value linkLength;
    public final Value chainHp;
    public final Value thickness;
    public final Value travelTicks;

    public BlackwhipChainDisarmAbility(Value range, Value segmentCount, Value linkLength, Value chainHp,
                                       Value thickness, Value travelTicks, AbilityProperties properties,
                                       AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.segmentCount = segmentCount;
        this.linkLength = linkLength;
        this.chainHp = chainHp;
        this.thickness = thickness;
        this.travelTicks = travelTicks;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        DataContext context = DataContext.forEntity(entity);
        double range = this.range.getAsFloat(context);
        int segments = Math.max(2, this.segmentCount.getAsInt(context));
        float link = this.linkLength.getAsFloat(context);
        float hp = Math.max(1.0f, this.chainHp.getAsFloat(context));
        float thickness = this.thickness.getAsFloat(context);
        int travel = Math.max(1, this.travelTicks.getAsInt(context));

        BlackwhipChainEntity chain = BlackwhipChainHelper.spawnFlyingChain(
                player, player.getLookAngle(), range, segments, link, hp, thickness, travel,
                0, range, 1, BlackwhipChainEntity.PURPOSE_DISARM);
        if (chain != null) {
            level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_THROW,
                    SoundSource.PLAYERS, 0.55f, 1.55f);
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_CHAIN_DISARM.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipChainDisarmAbility> {
        public MapCodec<BlackwhipChainDisarmAbility> codec() {
            return BlackwhipChainDisarmAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipChainDisarmAbility> builder,
                                     HolderLookup.Provider provider) {
            builder.setDescription("Shoots a chain tip that rolls Strength vs the target to rip a held item "
                            + "(shields first; blocking raises defense). Stolen items go to the owner's inventory.")
                    .add("range", TYPE_VALUE, "Maximum tip travel distance before the whip retracts on a miss.")
                    .add("segment_count", TYPE_VALUE, "Number of IK joints / hit-proxy segments (2-16).")
                    .add("link_length", TYPE_VALUE, "World-space length of each IK link.")
                    .add("chain_hp", TYPE_VALUE, "Shared hit points for the whole chain.")
                    .add("thickness", TYPE_VALUE, "Visual whip thickness.")
                    .add("travel_ticks", TYPE_VALUE, "Ticks for the tip to reach max range.")
                    .addExampleObject(new BlackwhipChainDisarmAbility(
                            new StaticValue(14.0f), new StaticValue(8.0f), new StaticValue(0.85f),
                            new StaticValue(16.0f), new StaticValue(0.9f), new StaticValue(10.0f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
