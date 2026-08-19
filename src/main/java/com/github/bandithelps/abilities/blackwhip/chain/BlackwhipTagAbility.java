package com.github.bandithelps.abilities.blackwhip.chain;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainHelper;
import com.github.bandithelps.utils.blackwhip.BlackwhipChainTagStore;
import com.github.bandithelps.utils.blackwhip.BlackwhipTargeting;
import com.github.bandithelps.utils.quirk.QuirkFactorUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Chain-Blackwhip tendril grab: shoots an IK chain tip along the look direction; latches on tip contact.
 * With {@code aoe=true}, unused tethers shoot at every nearby living entity in range instead.
 */
public class BlackwhipTagAbility extends Ability {

    public static final String MAX_TETHERS_KEY = "bw_max_tethers";
    public static final String CHAIN_HP_KEY = "bw_hp";

    public static final MapCodec<BlackwhipTagAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(18.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("ttl_ticks", new StaticValue(0.0f)).forGetter((ab) -> ab.ttlTicks),
                    Value.CODEC.optionalFieldOf("max_distance", new StaticValue(32.0f)).forGetter((ab) -> ab.maxDistance),
                    Value.CODEC.optionalFieldOf("segment_count", new StaticValue(10.0f)).forGetter((ab) -> ab.segmentCount),
                    Value.CODEC.optionalFieldOf("link_length", new StaticValue(1.1f)).forGetter((ab) -> ab.linkLength),
                    Value.CODEC.optionalFieldOf("thickness", new StaticValue(1.0f)).forGetter((ab) -> ab.thickness),
                    Value.CODEC.optionalFieldOf("travel_ticks", new StaticValue(12.0f)).forGetter((ab) -> ab.travelTicks),
                    Codec.BOOL.optionalFieldOf("aoe", false).forGetter((ab) -> ab.aoe),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipTagAbility::new));

    public final Value range;
    public final Value ttlTicks;
    public final Value maxDistance;
    public final Value segmentCount;
    public final Value linkLength;
    public final Value thickness;
    public final Value travelTicks;
    public final boolean aoe;

    public BlackwhipTagAbility(Value range, Value ttlTicks, Value maxDistance,
                               Value segmentCount, Value linkLength, Value thickness,
                               Value travelTicks, boolean aoe, AbilityProperties properties,
                               AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.ttlTicks = ttlTicks;
        this.maxDistance = maxDistance;
        this.segmentCount = segmentCount;
        this.linkLength = linkLength;
        this.thickness = thickness;
        this.travelTicks = travelTicks;
        this.aoe = aoe;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        DataContext context = DataContext.forEntity(entity);
        double qf = QuirkFactorUtil.getQuirkFactor(player);

        int maxTethers = (int) ((int) BodyAttachments.get(player).getCustomFloat(player, BodyPart.CHEST, MAX_TETHERS_KEY, 1) + (qf * 2));
        int freeSlots = maxTethers - BlackwhipChainEntity.countOwnedActive(player.getId());
        if (freeSlots <= 0) {
            return;
        }

        double range = this.range.getAsFloat(context);
        int ttl = Math.max(0, this.ttlTicks.getAsInt(context));
        double maxDist = this.maxDistance.getAsFloat(context);
        int segments = Math.max(2, this.segmentCount.getAsInt(context));
        float link = this.linkLength.getAsFloat(context);
        float hp = Math.max(1.0f, BodyAttachments.get(player).getCustomFloat(player, BodyPart.CHEST, CHAIN_HP_KEY, 1.0f));
        float thickness = this.thickness.getAsFloat(context);
        int travel = Math.max(1, this.travelTicks.getAsInt(context));

        if (this.aoe) {
            shootAoe(player, level, range, segments, link, hp, thickness, travel, ttl, maxDist, maxTethers, freeSlots);
            return;
        }

        BlackwhipChainEntity chain = BlackwhipChainHelper.spawnFlyingChain(
                player, player.getLookAngle(), range, segments, link, hp, thickness, travel, ttl, maxDist, maxTethers);

        if (chain != null) {
            level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.55f, 1.4f);
        }
    }

    private static void shootAoe(ServerPlayer player, ServerLevel level, double range, int segments,
                                 float link, float hp, float thickness, int travel, int ttl,
                                 double maxDist, int maxTethers, int freeSlots) {
        Vec3 origin = player.getEyePosition();
        List<LivingEntity> candidates = BlackwhipTargeting.entitiesInRange(player, range, 0);
        int spawned = 0;
        for (LivingEntity target : candidates) {
            if (spawned >= freeSlots) {
                break;
            }
            if (BlackwhipChainTagStore.isTagged(player, target.getId())) {
                continue;
            }
            Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
            Vec3 dir = aim.subtract(origin);
            if (dir.lengthSqr() < 1.0e-6) {
                dir = player.getLookAngle();
            }
            BlackwhipChainEntity chain = BlackwhipChainHelper.spawnFlyingChain(
                    player, dir, range, segments, link, hp, thickness, travel, ttl, maxDist, maxTethers);
            if (chain == null) {
                break;
            }
            spawned++;
            level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_THROW,
                    SoundSource.PLAYERS, 0.45f, 1.35f + spawned * 0.04f);
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_TAG.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipTagAbility> {
        public MapCodec<BlackwhipTagAbility> codec() {
            return BlackwhipTagAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipTagAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Shoots an IK chain Blackwhip tip along the look direction. Latches on tip contact. Tip can be damaged or knocked off course while deploying. With aoe=true, unused tethers shoot at every nearby living entity in range instead.")
                    .add("range", TYPE_VALUE, "Maximum tip travel distance before the whip retracts on a miss. AOE search radius uses the same value.")
                    .add("ttl_ticks", TYPE_VALUE, "Ticks before a latched tag auto-expires (0 = never by time).")
                    .add("max_distance", TYPE_VALUE, "If a tagged entity gets farther than this from the owner, the tag breaks.")
                    .add("segment_count", TYPE_VALUE, "Number of IK joints / hit-proxy segments (2-16).")
                    .add("link_length", TYPE_VALUE, "World-space length of each IK link.")
                    .add("thickness", TYPE_VALUE, "Visual whip thickness.")
                    .add("travel_ticks", TYPE_VALUE, "Ticks for the tip to reach max range (tip speed = range / travel_ticks).")
                    .add("aoe", TYPE_BOOLEAN, "If true, shoot one tip at each nearby living entity (LOS, nearest first) up to free tether slots. Tips do not home.")
                    .addExampleObject(new BlackwhipTagAbility(
                            new StaticValue(18.0f), new StaticValue(0.0f), new StaticValue(32.0f),
                            new StaticValue(10.0f), new StaticValue(1.1f),
                            new StaticValue(20.0f), new StaticValue(12.0f), false,
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
