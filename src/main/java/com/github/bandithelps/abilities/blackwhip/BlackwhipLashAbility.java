package com.github.bandithelps.abilities.blackwhip;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.utils.blackwhip.BlackwhipHelper;
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
 * Creative addition - "Lash": a sweeping cone slash with the Blackwhip. Damages and knocks back every
 * entity in a forward cone, fanning out a few short whip arcs for the visual. Damage scales with quirk
 * factor.
 */
public class BlackwhipLashAbility extends Ability {

    public static final MapCodec<BlackwhipLashAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("range", new StaticValue(7.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("cone_half_angle", new StaticValue(55.0f)).forGetter((ab) -> ab.coneHalfAngle),
                    Value.CODEC.optionalFieldOf("damage", new StaticValue(5.0f)).forGetter((ab) -> ab.damage),
                    Value.CODEC.optionalFieldOf("knockback", new StaticValue(0.7f)).forGetter((ab) -> ab.knockback),
                    Value.CODEC.optionalFieldOf("max_targets", new StaticValue(6.0f)).forGetter((ab) -> ab.maxTargets),
                    Value.CODEC.optionalFieldOf("arcs", new StaticValue(3.0f)).forGetter((ab) -> ab.arcs),
                    Value.CODEC.optionalFieldOf("thickness", new StaticValue(1.0f)).forGetter((ab) -> ab.thickness),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipLashAbility::new));

    public final Value range;
    public final Value coneHalfAngle;
    public final Value damage;
    public final Value knockback;
    public final Value maxTargets;
    public final Value arcs;
    public final Value thickness;

    public BlackwhipLashAbility(Value range, Value coneHalfAngle, Value damage, Value knockback, Value maxTargets, Value arcs,
                               Value thickness, AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.range = range;
        this.coneHalfAngle = coneHalfAngle;
        this.damage = damage;
        this.knockback = knockback;
        this.maxTargets = maxTargets;
        this.arcs = arcs;
        this.thickness = thickness;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        DataContext context = DataContext.forEntity(entity);
        double range = this.range.getAsFloat(context);
        double halfAngle = this.coneHalfAngle.getAsFloat(context);
        int maxTargets = Math.max(1, this.maxTargets.getAsInt(context));
        double qf = QuirkFactorUtil.getQuirkFactor(player);
        float damage = this.damage.getAsFloat(context) * (float) (1.0 + 0.1 * qf);
        double knockback = this.knockback.getAsFloat(context);

        List<LivingEntity> targets = BlackwhipTargeting.entitiesInCone(player, range, halfAngle, maxTargets);
        for (LivingEntity target : targets) {
            target.hurt(level.damageSources().mobAttack(player), damage);
            Vec3 push = target.position().subtract(player.position());
            push = new Vec3(push.x, 0, push.z);
            if (push.lengthSqr() > 1.0e-4) {
                push = push.normalize().scale(knockback);
                target.push(push.x, 0.25, push.z);
            }
        }

        spawnArcs(player, context, range);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9f, 1.0f);
    }

    private void spawnArcs(ServerPlayer player, DataContext context, double range) {
        int arcs = Math.max(1, this.arcs.getAsInt(context));
        float thickness = this.thickness.getAsFloat(context);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = look.cross(up);
        if (right.lengthSqr() < 1.0e-6) {
            right = new Vec3(1, 0, 0);
        }
        right = right.normalize();
        double spread = Math.toRadians(45.0);
        for (int i = 0; i < arcs; i++) {
            double t = arcs == 1 ? 0.0 : (i / (double) (arcs - 1)) * 2.0 - 1.0;
            Vec3 dir = look.scale(Math.cos(t * spread)).add(right.scale(Math.sin(t * spread))).normalize();
            Vec3 point = eye.add(dir.scale(range * 0.85));
            BlackwhipHelper.spawnLash(player, point, thickness, 0.7f, 4);
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_LASH.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipLashAbility> {
        public MapCodec<BlackwhipLashAbility> codec() {
            return BlackwhipLashAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipLashAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("A sweeping cone slash with the Blackwhip: damages and knocks back every entity in a forward cone (quirk-factor scaled) with a fan of short whip arcs.")
                    .add("range", TYPE_VALUE, "Reach of the slash cone.")
                    .add("cone_half_angle", TYPE_VALUE, "Half-angle of the slash cone in degrees.")
                    .add("damage", TYPE_VALUE, "Base damage to each hit entity.")
                    .add("knockback", TYPE_VALUE, "Horizontal knockback strength.")
                    .add("max_targets", TYPE_VALUE, "Maximum entities hit.")
                    .add("arcs", TYPE_VALUE, "Number of visual whip arcs fanned across the cone.")
                    .add("thickness", TYPE_VALUE, "Visual whip thickness.")
                    .addExampleObject(new BlackwhipLashAbility(new StaticValue(7.0f), new StaticValue(55.0f), new StaticValue(5.0f),
                            new StaticValue(0.7f), new StaticValue(6.0f), new StaticValue(3.0f), new StaticValue(1.0f),
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
