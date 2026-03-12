package com.github.bandithelps.abilities.movement;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.AbilityRegister;
import com.mojang.serialization.Codec;
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
import net.threetag.palladium.power.ability.*;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.Collections;
import java.util.List;

public class DashAbility extends Ability {
    private static final float GROUND_HORIZONTAL_BOOST = 3.3f;
    private static final float AIR_DASH_MODIFIER = 0.66f;
    private static final double MAX_VERTICAL_COMPONENT = 0.85d;
    private static final double MIN_HORIZONTAL_COMPONENT = 0.15d;

    public static final MapCodec<DashAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("strength", new StaticValue(2.0f)).forGetter((ab) -> ab.strength),
                    Codec.BOOL.optionalFieldOf("block_vertical", true).forGetter((ab) -> ab.blockVertical),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, DashAbility::new));

    public final Value strength;
    public final boolean blockVertical;

    public DashAbility(Value strength, boolean blockVertical, AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.strength = strength;
        this.blockVertical = blockVertical;
    }

    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player) {
            float strengthValue = this.strength.getAsFloat(DataContext.forEntity(entity));
            Vec3 dashDirection = this.blockVertical
                    ? horizontalDirection(player)
                    : directionalDash(player);
            Vec3 dashVelocity = dashDirection.scale(strengthValue);

            // This if helps to balance out the friction on the ground, with the absence of it in the air. Balances
            // the movement so it is a little more consistent
            if (player.onGround()) {
                dashVelocity = new Vec3(
                        dashVelocity.x * GROUND_HORIZONTAL_BOOST,
                        dashVelocity.y,
                        dashVelocity.z * GROUND_HORIZONTAL_BOOST
                );
            } else {
                dashVelocity = dashVelocity.scale(AIR_DASH_MODIFIER);
            }

            player.setDeltaMovement(player.getDeltaMovement().add(dashVelocity));
            player.hurtMarked = true;
        }
    }

    private static Vec3 directionalDash(ServerPlayer player) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 horizontal = new Vec3(look.x, 0.0d, look.z);
        double horizontalLength = horizontal.length();

        // This checks for the case that they are basically looking straight up
        // When we tried to scale things before, we were doing near-0 values and getting gibberish
        // So we focus instead on the vertical only, ensuring the distance is clamped to prevent rocket ships
        if (horizontalLength <= 1.0e-6d) {
            Vec3 facing = horizontalDirection(player);
            double vertical = look.y >= 0.0d
                    ? Math.sqrt(1.0d - (MIN_HORIZONTAL_COMPONENT * MIN_HORIZONTAL_COMPONENT))
                    : -Math.sqrt(1.0d - (MIN_HORIZONTAL_COMPONENT * MIN_HORIZONTAL_COMPONENT));
            return facing.scale(MIN_HORIZONTAL_COMPONENT).add(0.0d, vertical, 0.0d).normalize();
        }

        horizontal = horizontal.scale(1.0d / horizontalLength);
        double clampedVertical = Math.max(-MAX_VERTICAL_COMPONENT, Math.min(MAX_VERTICAL_COMPONENT, look.y));
        double horizontalComponent = Math.max(MIN_HORIZONTAL_COMPONENT, Math.sqrt(1.0d - (clampedVertical * clampedVertical)));

        return horizontal.scale(horizontalComponent).add(0.0d, clampedVertical, 0.0d).normalize();
    }

    /**
     * Calculates the horizontal movement
     * @param player
     * @return
     */
    private static Vec3 horizontalDirection(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0d, look.z);

        // Check if the vector is basically non-0, since normalizing 0 causes pretty weird things
        if (horizontal.lengthSqr() > 1.0e-6d) {
            return horizontal.normalize();
        }

        double yawRadians = Math.toRadians(player.getYRot());
        return new Vec3(-Math.sin(yawRadians), 0.0d, Math.cos(yawRadians));
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.DASH.get();
    }

    /*
     * Serializer for the documentation
     */
    public static class Serializer extends AbilitySerializer<DashAbility> {
        public MapCodec<DashAbility> codec() { return DashAbility.CODEC; }

        public void addDocumentation(CodecDocumentationBuilder<Ability, DashAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Dash the player in any direction any number of blocks, with specific particles.")
                    .add("strength", TYPE_FLOAT, "The strength to dash forwards based on the look direction")
                    .add("block_vertical", TYPE_BOOLEAN, "Whether or not to lock dashing to the horizontal directions only")
                    .addExampleObject(new DashAbility(new StaticValue(5.0f), true, AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }

    }
}
