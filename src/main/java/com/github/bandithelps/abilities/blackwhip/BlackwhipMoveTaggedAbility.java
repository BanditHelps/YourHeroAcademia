package com.github.bandithelps.abilities.blackwhip;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.utils.blackwhip.BlackwhipTagStore;
import com.github.bandithelps.utils.blackwhip.BlackwhipTargeting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
 * "Puppet": while held, drags tagged entities to a hold point floating in front of the owner's crosshair,
 * letting the player swing them around. Mode {@code single} only moves the looked-at (or nearest) tagged
 * entity; mode {@code all} moves every tethered entity.
 */
public class BlackwhipMoveTaggedAbility extends Ability {

    public static final MapCodec<BlackwhipMoveTaggedAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("hold_distance", new StaticValue(5.0f)).forGetter((ab) -> ab.holdDistance),
                    Value.CODEC.optionalFieldOf("pull_strength", new StaticValue(0.5f)).forGetter((ab) -> ab.pullStrength),
                    Value.CODEC.optionalFieldOf("max_step", new StaticValue(1.4f)).forGetter((ab) -> ab.maxStep),
                    Codec.STRING.optionalFieldOf("mode", "all").forGetter((ab) -> ab.mode),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipMoveTaggedAbility::new));

    public final Value holdDistance;
    public final Value pullStrength;
    public final Value maxStep;
    public final String mode;

    public BlackwhipMoveTaggedAbility(Value holdDistance, Value pullStrength, Value maxStep, String mode,
                                     AbilityProperties properties, AbilityStateManager conditions,
                                     List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.holdDistance = holdDistance;
        this.pullStrength = pullStrength;
        this.maxStep = maxStep;
        this.mode = mode;
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player) {
            DataContext context = DataContext.forEntity(entity);
            double holdDistance = this.holdDistance.getAsFloat(context);
            double pull = Math.max(0.0, this.pullStrength.getAsFloat(context));
            double maxStep = Math.max(0.0, this.maxStep.getAsFloat(context));

            Vec3 hold = player.getEyePosition().add(player.getLookAngle().scale(holdDistance));
            List<LivingEntity> targets = resolveTargets(player);

            for (LivingEntity target : targets) {
                Vec3 to = hold.subtract(target.getBoundingBox().getCenter());
                double dist = to.length();
                Vec3 velocity = dist < 1.0e-3
                        ? Vec3.ZERO
                        : to.scale(Math.min(dist, maxStep) / dist).scale(pull);
                target.setDeltaMovement(velocity);
                target.hurtMarked = true;
                target.fallDistance = 0;
                if (target instanceof Mob mob) {
                    mob.getNavigation().stop();
                }
            }
        }
        return super.tick(entity, abilityInstance, enabled);
    }

    private List<LivingEntity> resolveTargets(ServerPlayer player) {
        List<LivingEntity> tagged = BlackwhipTagStore.getTaggedEntities(player);
        if (!"single".equalsIgnoreCase(this.mode) || tagged.isEmpty()) {
            return tagged;
        }
        LivingEntity looked = BlackwhipTargeting.raycastLiving(player, 24.0);
        if (looked != null && BlackwhipTagStore.isTagged(player, looked.getId())) {
            return List.of(looked);
        }
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity t : tagged) {
            double d = player.distanceToSqr(t);
            if (d < best) {
                best = d;
                nearest = t;
            }
        }
        return nearest == null ? List.of() : List.of(nearest);
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_MOVE_TAGGED.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipMoveTaggedAbility> {
        public MapCodec<BlackwhipMoveTaggedAbility> codec() {
            return BlackwhipMoveTaggedAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipMoveTaggedAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("While held, drags tagged entities to a hold point in front of the crosshair so the player can swing them around.")
                    .add("hold_distance", TYPE_VALUE, "How far in front of the eyes the hold point sits.")
                    .add("pull_strength", TYPE_VALUE, "Fraction of the gap closed each tick (0-1 feels natural).")
                    .add("max_step", TYPE_VALUE, "Maximum movement per tick applied to a target.")
                    .add("mode", TYPE_STRING, "'all' moves every tethered entity; 'single' moves only the looked-at/nearest one.")
                    .addExampleObject(new BlackwhipMoveTaggedAbility(new StaticValue(5.0f), new StaticValue(0.5f), new StaticValue(1.4f), "all",
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
