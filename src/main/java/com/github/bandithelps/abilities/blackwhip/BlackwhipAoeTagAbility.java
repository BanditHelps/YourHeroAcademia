package com.github.bandithelps.abilities.blackwhip;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.utils.blackwhip.BlackwhipTagStore;
import com.github.bandithelps.utils.blackwhip.BlackwhipTargeting;
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
 * "Multi-Grab": sweeps a forward cone and tags every living entity in it (up to a quirk-factor-scaled
 * limit), spawning a tendril to each.
 */
public class BlackwhipAoeTagAbility extends Ability {

    public static final MapCodec<BlackwhipAoeTagAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(14.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("cone_half_angle", new StaticValue(35.0f)).forGetter((ab) -> ab.coneHalfAngle),
                    Value.CODEC.optionalFieldOf("ttl_ticks", new StaticValue(0.0f)).forGetter((ab) -> ab.ttlTicks),
                    Value.CODEC.optionalFieldOf("max_distance", new StaticValue(32.0f)).forGetter((ab) -> ab.maxDistance),
                    Value.CODEC.optionalFieldOf("base_max_targets", new StaticValue(3.0f)).forGetter((ab) -> ab.baseMaxTargets),
                    Value.CODEC.optionalFieldOf("thickness", new StaticValue(0.9f)).forGetter((ab) -> ab.thickness),
                    Value.CODEC.optionalFieldOf("curve", new StaticValue(0.7f)).forGetter((ab) -> ab.curve),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipAoeTagAbility::new));

    public final Value range;
    public final Value coneHalfAngle;
    public final Value ttlTicks;
    public final Value maxDistance;
    public final Value baseMaxTargets;
    public final Value thickness;
    public final Value curve;

    public BlackwhipAoeTagAbility(Value range, Value coneHalfAngle, Value ttlTicks, Value maxDistance, Value baseMaxTargets,
                                 Value thickness, Value curve, AbilityProperties properties, AbilityStateManager conditions,
                                 List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.coneHalfAngle = coneHalfAngle;
        this.ttlTicks = ttlTicks;
        this.maxDistance = maxDistance;
        this.baseMaxTargets = baseMaxTargets;
        this.thickness = thickness;
        this.curve = curve;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        DataContext context = DataContext.forEntity(entity);
        double range = this.range.getAsFloat(context);
        double halfAngle = this.coneHalfAngle.getAsFloat(context);
        double qf = QuirkFactorUtil.getQuirkFactor(player);
        int maxTargets = Math.max(1, this.baseMaxTargets.getAsInt(context) + (int) Math.floor(qf));

        List<LivingEntity> targets = BlackwhipTargeting.entitiesInCone(player, range, halfAngle, maxTargets);
        if (targets.isEmpty()) {
            return;
        }

        int ttl = Math.max(0, this.ttlTicks.getAsInt(context));
        double maxDist = this.maxDistance.getAsFloat(context);
        float thickness = this.thickness.getAsFloat(context);
        float curve = this.curve.getAsFloat(context);

        boolean any = false;
        for (LivingEntity target : targets) {
            // maxKeep here is the cone target cap; allow up to that many tethers.
            any |= BlackwhipTagStore.addTag(player, target, ttl, maxDist, maxTargets, thickness, curve, 5);
        }
        if (any) {
            level.playSound(null, player.blockPosition(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.9f, 0.9f);
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_AOE_TAG.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipAoeTagAbility> {
        public MapCodec<BlackwhipAoeTagAbility> codec() {
            return BlackwhipAoeTagAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipAoeTagAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Tags every living entity in a forward cone, up to base_max_targets + floor(quirk factor).")
                    .add("range", TYPE_VALUE, "Maximum reach of the cone.")
                    .add("cone_half_angle", TYPE_VALUE, "Half-angle of the targeting cone, in degrees.")
                    .add("ttl_ticks", TYPE_VALUE, "Ticks before a tag auto-expires (0 = never by time).")
                    .add("max_distance", TYPE_VALUE, "Distance at which tags break.")
                    .add("base_max_targets", TYPE_VALUE, "Base target cap before quirk-factor scaling.")
                    .add("thickness", TYPE_VALUE, "Visual whip thickness.")
                    .add("curve", TYPE_VALUE, "Visual whip curve amount.")
                    .addExampleObject(new BlackwhipAoeTagAbility(new StaticValue(14.0f), new StaticValue(35.0f), new StaticValue(0.0f),
                            new StaticValue(32.0f), new StaticValue(3.0f), new StaticValue(0.9f), new StaticValue(0.7f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
