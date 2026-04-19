package com.github.bandithelps.abilities.common;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.entities.ModEntities;
import com.github.bandithelps.entities.PotionEffectGeneratorEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
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
import net.threetag.palladium.util.PalladiumCodecs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PotionGeneratorAbility extends Ability {

    public static final MapCodec<PotionGeneratorAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("health", new StaticValue(20.0f)).forGetter((ab) -> ab.health),
                    Value.CODEC.optionalFieldOf("duration", new StaticValue(20)).forGetter((ab) -> ab.duration),
                    Value.CODEC.optionalFieldOf("amplifier", new StaticValue(0)).forGetter((ab) -> ab.amplifier),
                    Value.CODEC.optionalFieldOf("radius", new StaticValue(5.0f)).forGetter((ab) -> ab.radius),
                    Value.CODEC.optionalFieldOf("expiration_ticks").forGetter((ab) -> ab.expirationTicks),
                    Codec.BOOL.optionalFieldOf("effect_visible", true).forGetter((ab) -> ab.effectVisible),
                    Codec.BOOL.optionalFieldOf("generate_particles", false).forGetter((ab) -> ab.generateParticles),
                    Value.CODEC.optionalFieldOf("particle_size", new StaticValue(0.25f)).forGetter((ab) -> ab.particleSize),
                    Value.CODEC.optionalFieldOf("particle_density", new StaticValue(1.0f)).forGetter((ab) -> ab.particleDensity),
                    PalladiumCodecs.listOrPrimitive(Identifier.CODEC).optionalFieldOf("effects", Arrays.asList(Identifier.parse("minecraft:slowness"))).forGetter((ab) -> ab.effects),
                    PalladiumCodecs.listOrPrimitive(Identifier.CODEC).optionalFieldOf("particles", Collections.emptyList()).forGetter((ab) -> ab.particles),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()
            ).apply(instance, PotionGeneratorAbility::new));

    public final Value health;
    public final Value duration;
    public final Value amplifier;
    public final Value radius;
    public final Optional<Value> expirationTicks;
    public final boolean effectVisible;
    public final boolean generateParticles;
    public final Value particleSize;
    public final Value particleDensity;
    public final List<Identifier> effects;
    public final List<Identifier> particles;

    private final Map<UUID, UUID> activeGeneratorsByOwner = new HashMap<>();

    public PotionGeneratorAbility(
            Value health,
            Value duration,
            Value amplifier,
            Value radius,
            Optional<Value> expirationTicks,
            boolean effectVisible,
            boolean generateParticles,
            Value particleSize,
            Value particleDensity,
            List<Identifier> effects,
            List<Identifier> particles,
            AbilityProperties properties,
            AbilityStateManager conditions,
            List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.health = health;
        this.duration = duration;
        this.amplifier = amplifier;
        this.radius = radius;
        this.expirationTicks = expirationTicks;
        this.effectVisible = effectVisible;
        this.generateParticles = generateParticles;
        this.particleSize = particleSize;
        this.particleDensity = particleDensity;
        this.effects = effects;
        this.particles = particles;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        UUID ownerId = entity.getUUID();
        UUID oldGeneratorId = this.activeGeneratorsByOwner.remove(ownerId);
        if (oldGeneratorId != null) {
            Entity oldGenerator = serverLevel.getEntity(oldGeneratorId);
            if (oldGenerator != null) {
                oldGenerator.discard();
            }
        }

        float radius = this.radius.getAsFloat(DataContext.forEntity(entity));
        float health = this.health.getAsFloat(DataContext.forEntity(entity));
        int duration = this.duration.getAsInt(DataContext.forEntity(entity));
        int amplifier = this.amplifier.getAsInt(DataContext.forEntity(entity));
        float particleSize = this.particleSize.getAsFloat(DataContext.forEntity(entity));
        float particleDensity = this.particleDensity.getAsFloat(DataContext.forEntity(entity));
        Integer expirationTicks = this.expirationTicks
                .map(value -> value.getAsInt(DataContext.forEntity(entity)))
                .orElse(null);

        List<Holder<MobEffect>> effectsToApply = new ArrayList<>();
        for (Identifier id : this.effects) {
            Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(id)
                    .map(holder -> (Holder<MobEffect>) holder)
                    .orElse(MobEffects.SLOWNESS);
            effectsToApply.add(effect);
        }
        List<SimpleParticleType> particlesToSpawn = new ArrayList<>();
        for (Identifier id : this.particles) {
            BuiltInRegistries.PARTICLE_TYPE.get(id)
                    .map(holder -> holder.value())
                    .filter(SimpleParticleType.class::isInstance)
                    .map(SimpleParticleType.class::cast)
                    .ifPresent(particlesToSpawn::add);
        }

        PotionEffectGeneratorEntity potionGen = new PotionEffectGeneratorEntity(ModEntities.POTION_GENERATOR.get(), entity.level());
        potionGen.setRadius(radius);
        potionGen.setDuration(duration);
        potionGen.setGeneratorHealth(health);
        potionGen.setAmplifier(amplifier);
        potionGen.setEffects(effectsToApply);
        potionGen.setEffectVisible(this.effectVisible);
        potionGen.setGenerateParticles(this.generateParticles);
        potionGen.setParticles(particlesToSpawn);
        potionGen.setParticleSize(particleSize);
        potionGen.setParticleDensity(particleDensity);
        potionGen.setExpirationTicks(expirationTicks);
        potionGen.setInvisible(true);
        potionGen.setNoGravity(true);
        potionGen.setPos(new Vec3(entity.getX(), entity.getY(), entity.getZ()));

        serverLevel.addFreshEntity(potionGen);
        this.activeGeneratorsByOwner.put(ownerId, potionGen.getUUID());
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        UUID generatorId = this.activeGeneratorsByOwner.remove(entity.getUUID());
        if (generatorId == null) return;

        Entity generator = serverLevel.getEntity(generatorId);

        Integer expirationTicks = this.expirationTicks
                .map(value -> value.getAsInt(DataContext.forEntity(entity)))
                .orElse(null);

        if (generator != null && expirationTicks == null) {
            generator.discard();
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.POTION_GEN.get();
    }

    public static class Serializer extends AbilitySerializer<PotionGeneratorAbility> {
        public MapCodec<PotionGeneratorAbility> codec() {
            return PotionGeneratorAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, PotionGeneratorAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Summons an invisible stationary potion field generator that periodically applies configured effects in a radius.")
                    .add("health", TYPE_VALUE, "Health to assign to the generated entity.")
                    .add("duration", TYPE_VALUE, "Duration (ticks) of each applied effect.")
                    .add("amplifier", TYPE_VALUE, "Amplifier level for each applied effect.")
                    .add("radius", TYPE_VALUE, "Radius around the generator to search for targets.")
                    .add("expiration_ticks", TYPE_VALUE, "Optional lifetime (ticks) before the generated entity discards itself.")
                    .add("effect_visible", TYPE_BOOLEAN, "Whether particles are shown for the applied potion effects.")
                    .add("generate_particles", TYPE_BOOLEAN, "Whether to emit configured particles in the generator's area.")
                    .add("particle_size", TYPE_VALUE, "Randomized spread per spawned particle puff.")
                    .add("particle_density", TYPE_VALUE, "Multiplier for how many particles spawn per emission tick.")
                    .add("effects", TYPE_IDENTIFIER, "A list of mob effect ids to apply.")
                    .add("particles", TYPE_IDENTIFIER, "A list of simple particle ids to spawn randomly inside the generator radius.")
                    .addExampleObject(new PotionGeneratorAbility(
                            new StaticValue(20.0f),
                            new StaticValue(20),
                            new StaticValue(0),
                            new StaticValue(5.0f),
                            Optional.empty(),
                            true,
                            false,
                            new StaticValue(0.25f),
                            new StaticValue(1.0f),
                            Arrays.asList(Identifier.fromNamespaceAndPath("minecraft", "slowness")),
                            Collections.emptyList(),
                            AbilityProperties.BASIC,
                            AbilityStateManager.EMPTY,
                            Collections.emptyList()
                    ));
        }
    }
}
