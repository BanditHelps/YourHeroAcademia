package com.github.bandithelps.abilities.blackwhip;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.BlackwhipEntity;
import com.github.bandithelps.utils.blackwhip.BlackwhipHelper;
import com.github.bandithelps.utils.blackwhip.BlackwhipStruggle;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Restrain": wraps the looked-at entity in Blackwhip and roots it in place while held. Player victims
 * get a struggle minigame and must mash JUMP to break free; mobs are simply pinned. Releasing the
 * ability (or a successful struggle) frees the target.
 */
public class BlackwhipRestrainAbility extends Ability {

    private static final Map<UUID, Integer> RESTRAINED_TARGET = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> WRAP_ENTITY = new ConcurrentHashMap<>();

    public static final MapCodec<BlackwhipRestrainAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(14.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("max_distance", new StaticValue(24.0f)).forGetter((ab) -> ab.maxDistance),
                    Value.CODEC.optionalFieldOf("slowness_amplifier", new StaticValue(6.0f)).forGetter((ab) -> ab.slownessAmplifier),
                    Value.CODEC.optionalFieldOf("thickness", new StaticValue(1.1f)).forGetter((ab) -> ab.thickness),
                    Value.CODEC.optionalFieldOf("curve", new StaticValue(0.4f)).forGetter((ab) -> ab.curve),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipRestrainAbility::new));

    public final Value range;
    public final Value maxDistance;
    public final Value slownessAmplifier;
    public final Value thickness;
    public final Value curve;

    public BlackwhipRestrainAbility(Value range, Value maxDistance, Value slownessAmplifier, Value thickness, Value curve,
                                   AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.maxDistance = maxDistance;
        this.slownessAmplifier = slownessAmplifier;
        this.thickness = thickness;
        this.curve = curve;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        DataContext context = DataContext.forEntity(entity);
        LivingEntity target = BlackwhipTargeting.raycastLiving(player, this.range.getAsFloat(context));
        if (target == null) {
            return;
        }

        float thickness = this.thickness.getAsFloat(context);
        float curve = this.curve.getAsFloat(context);
        double maxDist = this.maxDistance.getAsFloat(context);

        // Tag (drives the tether visual + lets the struggle release it) and add the wrap rings.
        BlackwhipTagStore.addTag(player, target, 0, maxDist, 64, thickness, curve, 5);
        BlackwhipEntity wrap = BlackwhipHelper.spawnWrap(player, target, thickness);
        RESTRAINED_TARGET.put(player.getUUID(), target.getId());
        WRAP_ENTITY.put(player.getUUID(), wrap.getId());

        level.playSound(null, player.blockPosition(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0f, 0.7f);
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            Integer targetId = RESTRAINED_TARGET.get(player.getUUID());
            if (targetId == null) {
                return super.tick(entity, abilityInstance, enabled);
            }
            Entity te = level.getEntity(targetId);
            if (!(te instanceof LivingEntity target) || !target.isAlive() || !BlackwhipTagStore.isTagged(player, targetId)) {
                release(player, level);
                return super.tick(entity, abilityInstance, enabled);
            }

            DataContext context = DataContext.forEntity(entity);
            int slowness = Math.max(0, this.slownessAmplifier.getAsInt(context));

            target.setDeltaMovement(0.0, Math.min(0.0, target.getDeltaMovement().y), 0.0);
            target.hurtMarked = true;
            target.fallDistance = 0;
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, slowness, false, false));
            if (target instanceof Mob mob) {
                mob.getNavigation().stop();
                mob.setTarget(null);
            }
            if (target instanceof ServerPlayer victim) {
                double qf = QuirkFactorUtil.getQuirkFactor(player);
                BlackwhipStruggle.mark(victim, player, qf);
            }
        }
        return super.tick(entity, abilityInstance, enabled);
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            release(player, level);
        }
    }

    private void release(ServerPlayer player, ServerLevel level) {
        Integer targetId = RESTRAINED_TARGET.remove(player.getUUID());
        if (targetId != null) {
            BlackwhipTagStore.removeTag(player, targetId);
        }
        Integer wrapId = WRAP_ENTITY.remove(player.getUUID());
        if (wrapId != null && level.getEntity(wrapId) instanceof BlackwhipEntity wrap) {
            wrap.deactivate();
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_RESTRAIN.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipRestrainAbility> {
        public MapCodec<BlackwhipRestrainAbility> codec() {
            return BlackwhipRestrainAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipRestrainAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Wraps and roots the looked-at entity while held. Player victims must win a JUMP-mash struggle (scaled by the restrainer's quirk factor) to break free.")
                    .add("range", TYPE_VALUE, "Reach of the restrain raycast.")
                    .add("max_distance", TYPE_VALUE, "Distance at which the restraint breaks.")
                    .add("slowness_amplifier", TYPE_VALUE, "Amplifier of the slowness applied to the restrained target.")
                    .add("thickness", TYPE_VALUE, "Visual whip thickness.")
                    .add("curve", TYPE_VALUE, "Visual whip curve amount.")
                    .addExampleObject(new BlackwhipRestrainAbility(new StaticValue(14.0f), new StaticValue(24.0f), new StaticValue(6.0f),
                            new StaticValue(1.1f), new StaticValue(0.4f), AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
