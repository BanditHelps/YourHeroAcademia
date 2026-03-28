package com.github.bandithelps.abilities.blockdisplayanims;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.abilities.bodydata.BodyPartValueTickAbility;
import com.github.bandithelps.abilities.bodydata.DisplayBodyBarAbility;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.utils.blockdisplays.BlockDisplaySummoner;
import com.github.bandithelps.values.ModSettingTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
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
import org.joml.Vector3i;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
                    PalladiumCodecs.listOrPrimitive(BlockState.CODEC).optionalFieldOf("palette", Arrays.asList(Blocks.DIAMOND_BLOCK.defaultBlockState())).forGetter((ab) -> ab.palette),
                    PalladiumCodecs.VECTOR_3F_CODEC.optionalFieldOf("location_offset", new Vector3f(0.0f, 0.0f, 0.0f)).forGetter((ab) -> ab.locationOffset),
                    PalladiumCodecs.VECTOR_3F_CODEC.optionalFieldOf("rotation_deg", new Vector3f(0.0f, 0.0f, 0.0f)).forGetter((ab) -> ab.rotationDeg),
                    Value.CODEC.optionalFieldOf("lifetime", new StaticValue(40.0f)).forGetter((ab) -> ab.lifetime),
                    Codec.BOOL.optionalFieldOf("random_decay", true).forGetter((ab) -> ab.randomDecay),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BDShockwaveAbility::new));

    public final Value radius;
    public final Value tickSpeed;
    public final Value density;
    public final List<BlockState> palette;
    public final Vector3f locationOffset; // relative to player
    public final Vector3f rotationDeg;
    public final Value lifetime;
    public final boolean randomDecay;

    public BDShockwaveAbility(Value radius, Value tickSpeed, Value density, List<BlockState> palette, Vector3f locationOffset, Vector3f rotationDeg, Value lifetime, boolean randomDecay, AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.radius = radius;
        this.tickSpeed = tickSpeed;
        this.density = density;
        this.palette = palette;
        this.locationOffset = locationOffset;
        this.rotationDeg = rotationDeg;
        this.lifetime = lifetime;
        this.randomDecay = randomDecay;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player) {

            float radius = this.radius.getAsFloat(DataContext.forEntity(entity));
            float density = this.density.getAsFloat(DataContext.forEntity(entity));

            BlockDisplaySummoner.summonShockwave(
                    player.level(),
                    player,
                    radius,
                    density
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
                    .add("palette", TYPE_BLOCK_STATE, "The body part that the custom float value is stored in")
                    .add("location_offset", TYPE_VECTOR3, "The relative shift to the player's center that the shock wave should appear")
                    .add("rotation_deg", TYPE_VECTOR3, "The degrees of rotation in the x y and z planes. Only accepts int")
                    .add("lifetime", TYPE_VALUE, "How long the block displays will stay visible")
                    .add("random_decay", TYPE_BOOLEAN, "Whether or not the block displays disapear randomly or all at the same time")
                    .addExampleObject(new BDShockwaveAbility(new StaticValue(5.0f), new StaticValue(40), new StaticValue(50.0f), Arrays.asList(Blocks.DIAMOND_BLOCK.defaultBlockState()), new Vector3f(0, -1, 0), new Vector3f(0, 0, 0), new StaticValue(40f), true, AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }

    }

}
