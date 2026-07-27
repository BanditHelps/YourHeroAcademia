package com.github.bandithelps.abilities.blackwhip.chain;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainHelper;
import com.github.bandithelps.utils.quirk.QuirkFactorUtil;
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
 * Chain-Blackwhip tendril grab: shoots an IK chain tip along the look direction; latches on tip contact.
 */
public class BlackwhipChainTagAbility extends Ability {

    public static final MapCodec<BlackwhipChainTagAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(18.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("ttl_ticks", new StaticValue(0.0f)).forGetter((ab) -> ab.ttlTicks),
                    Value.CODEC.optionalFieldOf("max_distance", new StaticValue(32.0f)).forGetter((ab) -> ab.maxDistance),
                    Value.CODEC.optionalFieldOf("base_max_tethers", new StaticValue(2.0f)).forGetter((ab) -> ab.baseMaxTethers),
                    Value.CODEC.optionalFieldOf("segment_count", new StaticValue(10.0f)).forGetter((ab) -> ab.segmentCount),
                    Value.CODEC.optionalFieldOf("link_length", new StaticValue(1.1f)).forGetter((ab) -> ab.linkLength),
                    Value.CODEC.optionalFieldOf("chain_hp", new StaticValue(20.0f)).forGetter((ab) -> ab.chainHp),
                    Value.CODEC.optionalFieldOf("thickness", new StaticValue(1.0f)).forGetter((ab) -> ab.thickness),
                    Value.CODEC.optionalFieldOf("travel_ticks", new StaticValue(12.0f)).forGetter((ab) -> ab.travelTicks),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipChainTagAbility::new));

    public final Value range;
    public final Value ttlTicks;
    public final Value maxDistance;
    public final Value baseMaxTethers;
    public final Value segmentCount;
    public final Value linkLength;
    public final Value chainHp;
    public final Value thickness;
    public final Value travelTicks;

    public BlackwhipChainTagAbility(Value range, Value ttlTicks, Value maxDistance, Value baseMaxTethers,
                                    Value segmentCount, Value linkLength, Value chainHp, Value thickness,
                                    Value travelTicks, AbilityProperties properties, AbilityStateManager conditions,
                                    List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.ttlTicks = ttlTicks;
        this.maxDistance = maxDistance;
        this.baseMaxTethers = baseMaxTethers;
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

        double qf = QuirkFactorUtil.getQuirkFactor(player);
        int maxKeep = Math.max(1, this.baseMaxTethers.getAsInt(context) + (int) Math.floor(qf));
        if (BlackwhipChainEntity.countOwnedActive(player.getId()) >= maxKeep) {
            return;
        }

        int ttl = Math.max(0, this.ttlTicks.getAsInt(context));
        double maxDist = this.maxDistance.getAsFloat(context);
        int segments = Math.max(2, this.segmentCount.getAsInt(context));
        float link = this.linkLength.getAsFloat(context);
        float hp = Math.max(1.0f, this.chainHp.getAsFloat(context));
        float thickness = this.thickness.getAsFloat(context);
        int travel = Math.max(1, this.travelTicks.getAsInt(context));

        BlackwhipChainEntity chain = BlackwhipChainHelper.spawnFlyingChain(
                player, player.getLookAngle(), range, segments, link, hp, thickness, travel, ttl, maxDist, maxKeep);
        if (chain != null) {
            level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.55f, 1.4f);
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_CHAIN_TAG.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipChainTagAbility> {
        public MapCodec<BlackwhipChainTagAbility> codec() {
            return BlackwhipChainTagAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipChainTagAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Shoots an IK chain Blackwhip tip along the look direction. Latches on tip contact. Tip can be damaged or knocked off course while deploying.")
                    .add("range", TYPE_VALUE, "Maximum tip travel distance before the whip retracts on a miss.")
                    .add("ttl_ticks", TYPE_VALUE, "Ticks before a latched tag auto-expires (0 = never by time).")
                    .add("max_distance", TYPE_VALUE, "If a tagged entity gets farther than this from the owner, the tag breaks.")
                    .add("base_max_tethers", TYPE_VALUE, "Base number of simultaneous deploying/latched chains before quirk-factor scaling.")
                    .add("segment_count", TYPE_VALUE, "Number of IK joints / hit-proxy segments (2-16).")
                    .add("link_length", TYPE_VALUE, "World-space length of each IK link.")
                    .add("chain_hp", TYPE_VALUE, "Shared hit points for the whole chain.")
                    .add("thickness", TYPE_VALUE, "Visual whip thickness.")
                    .add("travel_ticks", TYPE_VALUE, "Ticks for the tip to reach max range (tip speed = range / travel_ticks).")
                    .addExampleObject(new BlackwhipChainTagAbility(
                            new StaticValue(18.0f), new StaticValue(0.0f), new StaticValue(32.0f),
                            new StaticValue(2.0f), new StaticValue(10.0f), new StaticValue(1.1f),
                            new StaticValue(20.0f), new StaticValue(1.0f), new StaticValue(12.0f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
