package com.github.bandithelps.abilities.blockdisplayanims;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.utils.blockdisplays.BlockDisplaySummoner;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.logic.value.StaticValue;
import net.threetag.palladium.logic.value.Value;
import net.threetag.palladium.power.ability.*;
import net.threetag.palladium.power.energybar.EnergyBarUsage;
import net.threetag.palladium.util.PalladiumCodecs;
import org.joml.Vector3f;

import java.util.*;
import java.util.List;

/*
 * radius
 * speed
 * palette
 * location (player relative)
 * rotation
 * decay time
 * scaling stuff
 */


public class BDShockwaveAbility extends Ability {
    public static final MapCodec<BDShockwaveAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("radius", new StaticValue(5.0)).forGetter((ab) -> ab.radius),
                    Value.CODEC.optionalFieldOf("tick_speed", new StaticValue(40)).forGetter((ab) -> ab.tickSpeed),
                    Value.CODEC.optionalFieldOf("density", new StaticValue(50)).forGetter((ab) -> ab.density),
                    PalladiumCodecs.listOrPrimitive(Identifier.CODEC).optionalFieldOf("palette", Arrays.asList(Identifier.parse("minecraft:diamond_block"))).forGetter((ab) -> ab.palette),
                    PalladiumCodecs.VECTOR_3F_CODEC.optionalFieldOf("location_offset", new Vector3f(0.0f, 0.0f, 0.0f)).forGetter((ab) -> ab.locationOffset),
                    PalladiumCodecs.VECTOR_3F_CODEC.optionalFieldOf("rotation_offset", new Vector3f(0.0f, 0.0f, 0.0f)).forGetter((ab) -> ab.rotationOffset),
                    PalladiumCodecs.VECTOR_3F_CODEC.optionalFieldOf("initial_scale", new Vector3f(0.3f, 0.3f, 0.3f)).forGetter((ab) -> ab.initialScale),
                    PalladiumCodecs.VECTOR_3F_CODEC.optionalFieldOf("final_scale", new Vector3f(0.6f, 0.6f, 0.6f)).forGetter((ab) -> ab.finalScale),
                    Value.CODEC.optionalFieldOf("lifetime", new StaticValue(40.0f)).forGetter((ab) -> ab.lifetime),
                    Codec.BOOL.optionalFieldOf("random_decay", true).forGetter((ab) -> ab.randomDecay),
                    Codec.BOOL.optionalFieldOf("random_rotation", true).forGetter((ab) -> ab.randomRotation),
                    Codec.BOOL.optionalFieldOf("relative", false).forGetter((ab) -> ab.useRelative),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BDShockwaveAbility::new));

    public final Value radius;
    public final Value tickSpeed;
    public final Value density;
    public final List<Identifier> palette;
    public final Vector3f locationOffset; // relative to player
    public final Vector3f rotationOffset;
    public final Vector3f initialScale;
    public final Vector3f finalScale;
    public final Value lifetime;
    public final boolean randomDecay;
    public final boolean randomRotation;
    public final boolean useRelative;

    public BDShockwaveAbility(Value radius, Value tickSpeed, Value density, List<Identifier> palette, Vector3f locationOffset, Vector3f rotationOffset, Vector3f initialScale, Vector3f finalScale, Value lifetime, boolean randomDecay, boolean randomRotation, boolean useRelative, AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.radius = radius;
        this.tickSpeed = tickSpeed;
        this.density = density;
        this.palette = palette;
        this.locationOffset = locationOffset;
        this.rotationOffset = rotationOffset;
        this.initialScale = initialScale;
        this.finalScale = finalScale;
        this.lifetime = lifetime;
        this.randomDecay = randomDecay;
        this.randomRotation = randomRotation;
        this.useRelative = useRelative;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player) {

            float radius = this.radius.getAsFloat(DataContext.forEntity(entity));
            float density = this.density.getAsFloat(DataContext.forEntity(entity));
            int tickSpeed = this.tickSpeed.getAsInt(DataContext.forEntity(entity));
            int lifetime = this.lifetime.getAsInt(DataContext.forEntity(entity));

            List<BlockState> paletteBlocks = new ArrayList<>();

            for (Identifier id : this.palette) {
                Block block = BuiltInRegistries.BLOCK.get(id).map(Holder::value).orElse(Blocks.AIR);
                paletteBlocks.add(block.defaultBlockState());
            }

            BlockDisplaySummoner.summonShockwave(
                    player.level(),
                    player,
                    radius,
                    tickSpeed,
                    density,
                    paletteBlocks,
                    this.locationOffset,
                    this.rotationOffset,
                    this.initialScale,
                    this.finalScale,
                    lifetime,
                    this.randomDecay,
                    this.randomRotation,
                    this.useRelative
            );
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BD_SHOCKWAVE.get();
    }

    /*
     * Serializer for the documentation
     */
    public static class Serializer extends AbilitySerializer<BDShockwaveAbility> {
        public MapCodec<BDShockwaveAbility> codec() { return BDShockwaveAbility.CODEC; }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BDShockwaveAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Creates a configurable block display shock wave at a specific point")
                    .add("radius", TYPE_VALUE, "The ending radius of the growing shock wave")
                    .add("tick_speed", TYPE_VALUE, "How many ticks it takes to reach the end radius of the shock wave")
                    .add("density", TYPE_VALUE, "How many blocks displays are drawn in the effect")
                    .add("palette", TYPE_IDENTIFIER, "A list of block ids to add to the effect")
                    .add("location_offset", TYPE_VECTOR3, "The relative shift to the player's center that the shock wave should appear")
                    .add("rotation_offset", TYPE_VECTOR3, "The degrees of rotation in the x y and z planes the entire shock wave is rotated. Only accepts int")
                    .add("initial_scale", TYPE_VECTOR3, "The initial size the block displays spawn in the world as")
                    .add("final_scale", TYPE_VECTOR3, "The final size the block displays will grow to")
                    .add("lifetime", TYPE_VALUE, "How long the block displays will stay visible")
                    .add("random_decay", TYPE_BOOLEAN, "Whether or not the block displays disappear randomly or all at the same time")
                    .add("random_rotation", TYPE_BOOLEAN, "Whether or not the block displays spawn with a randomized right_rotation value")
                    .add("relative", TYPE_BOOLEAN, "Controls if the location_offset is based on the player's relative coordinates. i.e ^ ^ ^ instead of ~ ~ ~")
                    .addExampleObject(new BDShockwaveAbility(new StaticValue(5.0f), new StaticValue(40), new StaticValue(50.0f), Arrays.asList(Identifier.parse("minecraft:diamond_block")), new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), new Vector3f(0.3f, 0.3f, 0.3f), new Vector3f(0.6f, 0.6f, 0.6f), new StaticValue(40f), true, true, false, AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }

    }

}
