package com.github.bandithelps.abilities.common;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.values.ModDamageTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
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
import net.threetag.palladium.util.PalladiumCodecs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SprayAttackAbility extends Ability {

    private static final ParticleConfig DEFAULT_PARTICLE_CONFIG = new ParticleConfig(
            Collections.emptyList(),
            new StaticValue(2.0f),
            new StaticValue(0.05f),
            new StaticValue(0.9f),
            new StaticValue(0.0f),
            new StaticValue(-0.2f),
            new StaticValue(0.6f),
            Optional.empty()
    );

    public static final MapCodec<SprayAttackAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(8.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("hit_radius", new StaticValue(1.0f)).forGetter((ab) -> ab.hitRadius),
                    Value.CODEC.optionalFieldOf("damage", new StaticValue(0.0f)).forGetter((ab) -> ab.damage),
                    Value.CODEC.optionalFieldOf("fire_seconds", new StaticValue(0.0f)).forGetter((ab) -> ab.fireSeconds),
                    ExtraCodecs.POSITIVE_INT.optionalFieldOf("tick_rate", 1).forGetter((ab) -> ab.tickRate),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("max_targets_per_tick", 0).forGetter((ab) -> ab.maxTargetsPerTick),
                    ParticleConfig.CODEC.codec().optionalFieldOf("particle_config", DEFAULT_PARTICLE_CONFIG).forGetter((ab) -> ab.particleConfig),
                    PalladiumCodecs.listOrPrimitive(Identifier.CODEC).optionalFieldOf("effects", Collections.emptyList()).forGetter((ab) -> ab.effects),
                    Value.CODEC.optionalFieldOf("effect_duration", new StaticValue(40)).forGetter((ab) -> ab.effectDuration),
                    Value.CODEC.optionalFieldOf("effect_amplifier", new StaticValue(0)).forGetter((ab) -> ab.effectAmplifier),
                    Codec.BOOL.optionalFieldOf("show_effect_particles", true).forGetter((ab) -> ab.showEffectParticles),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()
            ).apply(instance, SprayAttackAbility::new));

    public final Value range;
    public final Value hitRadius;
    public final Value damage;
    public final Value fireSeconds;
    public final int tickRate;
    public final int maxTargetsPerTick;
    public final ParticleConfig particleConfig;
    public final List<Identifier> effects;
    public final Value effectDuration;
    public final Value effectAmplifier;
    public final boolean showEffectParticles;
    private final Map<UUID, Integer> holdTicksByOwner = new HashMap<>();

    public SprayAttackAbility(
            Value range,
            Value hitRadius,
            Value damage,
            Value fireSeconds,
            int tickRate,
            int maxTargetsPerTick,
            ParticleConfig particleConfig,
            List<Identifier> effects,
            Value effectDuration,
            Value effectAmplifier,
            boolean showEffectParticles,
            AbilityProperties properties,
            AbilityStateManager conditions,
            List<EnergyBarUsage> energyBarUsages
    ) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.hitRadius = hitRadius;
        this.damage = damage;
        this.fireSeconds = fireSeconds;
        this.tickRate = tickRate;
        this.maxTargetsPerTick = maxTargetsPerTick;
        this.particleConfig = particleConfig;
        this.effects = effects;
        this.effectDuration = effectDuration;
        this.effectAmplifier = effectAmplifier;
        this.showEffectParticles = showEffectParticles;
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (!enabled || !(entity.level() instanceof ServerLevel serverLevel)) {
            return super.tick(entity, abilityInstance, enabled);
        }

        UUID ownerId = entity.getUUID();
        int heldTicks = this.holdTicksByOwner.merge(ownerId, 1, Integer::sum);

        if (entity.tickCount % this.tickRate != 0) {
            return super.tick(entity, abilityInstance, enabled);
        }

        DataContext context = DataContext.forEntity(entity);
        float range = this.range.getAsFloat(context);
        float hitRadius = this.hitRadius.getAsFloat(context);
        float flatDamage = this.damage.getAsFloat(context);
        int fireTicks = Math.max(0, Math.round(this.fireSeconds.getAsFloat(context) * 20.0f));
        int duration = Math.max(0, this.effectDuration.getAsInt(context));
        int amplifier = Math.max(0, this.effectAmplifier.getAsInt(context));
        float particleDensity = Math.max(0.0f, this.particleConfig.density.getAsFloat(context));
        float particleSpread = Math.max(0.0f, this.particleConfig.spread.getAsFloat(context));
        float originForward = this.particleConfig.originForward.getAsFloat(context);
        float originRight = this.particleConfig.originRight.getAsFloat(context);
        float originUp = this.particleConfig.originUp.getAsFloat(context);
        float growthPerTick = Math.max(0.0f, this.particleConfig.growthPerTick.getAsFloat(context));
        float maxDistance = this.particleConfig.maxDistance.map(value -> value.getAsFloat(context)).orElse(range);
        maxDistance = Math.max(0.0f, Math.min(maxDistance, range));
        float activeDistance = growthPerTick <= 0.0f ? maxDistance : Math.min(maxDistance, heldTicks * growthPerTick);

        if (range <= 0.0f || hitRadius <= 0.0f || activeDistance <= 0.0f) {
            return super.tick(entity, abilityInstance, enabled);
        }

        Vec3 eyePos = entity.getEyePosition();
        Vec3 look = entity.getLookAngle().normalize();
        Vec3 start = this.resolveOrigin(eyePos, look, entity, originForward, originRight, originUp);
        Vec3 end = start.add(look.scale(activeDistance));

        this.spawnSprayParticles(serverLevel, start, end, hitRadius, particleDensity, particleSpread, this.resolveParticles());

        List<Holder<MobEffect>> effects = this.resolveEffects();
        List<LivingEntity> targets = this.findTargets(serverLevel, entity, start, look, end, activeDistance, hitRadius);

        int affected = 0;
        for (LivingEntity target : targets) {
            if (this.maxTargetsPerTick > 0 && affected >= this.maxTargetsPerTick) {
                break;
            }

            if (flatDamage > 0.0f) {
                target.hurt(ModDamageTypes.sprayDamageSource(serverLevel, entity), flatDamage);
            }

            if (fireTicks > 0) {
                target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), fireTicks));
            }

            if (!effects.isEmpty() && duration > 0) {
                for (Holder<MobEffect> effect : effects) {
                    target.addEffect(new MobEffectInstance(effect, duration, amplifier, true, this.showEffectParticles));
                }
            }

            affected++;
        }

        return super.tick(entity, abilityInstance, enabled);
    }

    private Vec3 resolveOrigin(Vec3 eyePos, Vec3 look, LivingEntity entity, float forwardOffset, float rightOffset, float upOffset) {
        Vec3 worldUp = new Vec3(0.0d, 1.0d, 0.0d);
        Vec3 right = worldUp.cross(look);
        if (right.lengthSqr() < 1.0E-6d) {
            float yawRadians = (float) Math.toRadians(entity.getYRot());
            right = new Vec3(Math.cos(yawRadians), 0.0d, Math.sin(yawRadians));
        }
        right = right.normalize();
        Vec3 up = look.cross(right).normalize();

        return eyePos
                .add(look.scale(forwardOffset))
                .add(right.scale(rightOffset))
                .add(up.scale(upOffset));
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        this.holdTicksByOwner.remove(entity.getUUID());
    }

    private List<LivingEntity> findTargets(ServerLevel level, LivingEntity owner, Vec3 start, Vec3 look, Vec3 end, float range, float hitRadius) {
        AABB searchBox = new AABB(
                Math.min(start.x, end.x) - hitRadius,
                Math.min(start.y, end.y) - hitRadius,
                Math.min(start.z, end.z) - hitRadius,
                Math.max(start.x, end.x) + hitRadius,
                Math.max(start.y, end.y) + hitRadius,
                Math.max(start.z, end.z) + hitRadius
        );

        List<LivingEntity> targets = new ArrayList<>();
        for (Entity nearbyEntity : level.getEntities(owner, searchBox)) {
            if (!(nearbyEntity instanceof LivingEntity target) || !target.isAlive() || target == owner) {
                continue;
            }

            Vec3 targetCenter = target.getBoundingBox().getCenter();
            Vec3 fromStart = targetCenter.subtract(start);
            double projection = fromStart.dot(look);
            if (projection < 0.0d || projection > range) {
                continue;
            }

            Vec3 closestPoint = start.add(look.scale(projection));
            double allowedRadius = hitRadius + (target.getBbWidth() * 0.5d);
            if (targetCenter.distanceToSqr(closestPoint) <= (allowedRadius * allowedRadius)) {
                targets.add(target);
            }
        }
        return targets;
    }

    private List<Holder<MobEffect>> resolveEffects() {
        List<Holder<MobEffect>> resolved = new ArrayList<>();
        for (Identifier id : this.effects) {
            BuiltInRegistries.MOB_EFFECT.get(id)
                    .map(holder -> (Holder<MobEffect>) holder)
                    .ifPresentOrElse(resolved::add, () ->
                            YourHeroAcademia.LOGGER.warn("Unknown mob effect '{}' in spray_attack ability config", id));
        }
        return resolved;
    }

    private void spawnSprayParticles(ServerLevel level, Vec3 start, Vec3 end, float hitRadius, float density, float spread, List<SimpleParticleType> particles) {
        if (particles.isEmpty() || density <= 0.0f) {
            return;
        }

        double length = start.distanceTo(end);
        int points = Math.max(1, Math.round((float) length * density));

        for (int i = 0; i <= points; i++) {
            double progress = i / (double) points;
            Vec3 point = start.lerp(end, progress);

            SimpleParticleType particle = particles.get(level.getRandom().nextInt(particles.size()));
            double offsetX = (level.getRandom().nextDouble() - 0.5d) * 2.0d * hitRadius;
            double offsetY = (level.getRandom().nextDouble() - 0.5d) * 2.0d * hitRadius;
            double offsetZ = (level.getRandom().nextDouble() - 0.5d) * 2.0d * hitRadius;

            level.sendParticles(
                    particle,
                    point.x + offsetX,
                    point.y + offsetY,
                    point.z + offsetZ,
                    1,
                    spread,
                    spread,
                    spread,
                    0.0d
            );
        }
    }

    private List<SimpleParticleType> resolveParticles() {
        List<SimpleParticleType> resolved = new ArrayList<>();
        for (Identifier id : this.particleConfig.particles) {
            BuiltInRegistries.PARTICLE_TYPE.get(id)
                    .map(holder -> holder.value())
                    .filter(SimpleParticleType.class::isInstance)
                    .map(SimpleParticleType.class::cast)
                    .ifPresentOrElse(resolved::add, () ->
                            YourHeroAcademia.LOGGER.warn("Unknown/simple particle '{}' in spray_attack ability config", id));
        }
        return resolved;
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.SPRAY_ATTACK.get();
    }

    public static class Serializer extends AbilitySerializer<SprayAttackAbility> {
        public MapCodec<SprayAttackAbility> codec() {
            return SprayAttackAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, SprayAttackAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("While held, projects a configurable particle spray in front of the user and applies per-tick damage, fire, and potion effects to targets inside a ray-with-radius hit sweep.")
                    .add("range", TYPE_VALUE, "How far the spray reaches from the user's eyes.")
                    .add("hit_radius", TYPE_VALUE, "Radius around the forward ray used for target hit checks.")
                    .add("damage", TYPE_VALUE, "Flat damage applied per tick to each target hit. Set to 0 to disable.")
                    .add("fire_seconds", TYPE_VALUE, "How many seconds targets are lit on fire when hit. Set to 0 to disable.")
                    .add("tick_rate", TYPE_INT, "How many ticks between spray updates.")
                    .add("max_targets_per_tick", TYPE_INT, "Maximum number of targets affected per update. 0 means no cap.")
                    .add("particle_config", TYPE_VALUE, "Visual spray settings object. Supports particles, density, spread, growth_per_tick, max_distance, and camera-relative origin offsets (origin_forward/origin_right/origin_up).")
                    .add("effects", TYPE_IDENTIFIER, "A list of mob effect ids to apply on hit.")
                    .add("effect_duration", TYPE_VALUE, "Duration in ticks for applied effects.")
                    .add("effect_amplifier", TYPE_VALUE, "Amplifier for applied effects.")
                    .add("show_effect_particles", TYPE_BOOLEAN, "Whether potion effect particles are visible on affected targets.")
                    .addExampleObject(new SprayAttackAbility(
                            new StaticValue(9.0f),
                            new StaticValue(1.2f),
                            new StaticValue(2.0f),
                            new StaticValue(2.0f),
                            1,
                            4,
                            new ParticleConfig(
                                    Arrays.asList(Identifier.fromNamespaceAndPath("minecraft", "flame")),
                                    new StaticValue(2.0f),
                                    new StaticValue(0.05f),
                                    new StaticValue(1.1f),
                                    new StaticValue(-0.35f),
                                    new StaticValue(-0.25f),
                                    new StaticValue(0.6f),
                                    Optional.of(new StaticValue(9.0f))
                            ),
                            Arrays.asList(Identifier.fromNamespaceAndPath("minecraft", "weakness")),
                            new StaticValue(40),
                            new StaticValue(0),
                            true,
                            AbilityProperties.BASIC,
                            AbilityStateManager.EMPTY,
                            Collections.emptyList()
                    ));
        }
    }

    public static class ParticleConfig {
        public static final MapCodec<ParticleConfig> CODEC = RecordCodecBuilder.mapCodec((instance) ->
                instance.group(
                        PalladiumCodecs.listOrPrimitive(Identifier.CODEC).optionalFieldOf("particles", Collections.emptyList()).forGetter((cfg) -> cfg.particles),
                        Value.CODEC.optionalFieldOf("density", new StaticValue(2.0f)).forGetter((cfg) -> cfg.density),
                        Value.CODEC.optionalFieldOf("spread", new StaticValue(0.05f)).forGetter((cfg) -> cfg.spread),
                        Value.CODEC.optionalFieldOf("origin_forward", new StaticValue(0.9f)).forGetter((cfg) -> cfg.originForward),
                        Value.CODEC.optionalFieldOf("origin_right", new StaticValue(0.0f)).forGetter((cfg) -> cfg.originRight),
                        Value.CODEC.optionalFieldOf("origin_up", new StaticValue(-0.2f)).forGetter((cfg) -> cfg.originUp),
                        Value.CODEC.optionalFieldOf("growth_per_tick", new StaticValue(0.6f)).forGetter((cfg) -> cfg.growthPerTick),
                        Value.CODEC.optionalFieldOf("max_distance").forGetter((cfg) -> cfg.maxDistance)
                ).apply(instance, ParticleConfig::new));

        public final List<Identifier> particles;
        public final Value density;
        public final Value spread;
        public final Value originForward;
        public final Value originRight;
        public final Value originUp;
        public final Value growthPerTick;
        public final Optional<Value> maxDistance;

        public ParticleConfig(
                List<Identifier> particles,
                Value density,
                Value spread,
                Value originForward,
                Value originRight,
                Value originUp,
                Value growthPerTick,
                Optional<Value> maxDistance
        ) {
            this.particles = particles;
            this.density = density;
            this.spread = spread;
            this.originForward = originForward;
            this.originRight = originRight;
            this.originUp = originUp;
            this.growthPerTick = growthPerTick;
            this.maxDistance = maxDistance;
        }
    }
}
